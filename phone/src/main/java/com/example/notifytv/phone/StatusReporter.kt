package com.example.notifytv.phone

import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps the TV's clock / battery / weather pill up to date.
 * Runs inside the notification listener's process, which Android keeps alive while notification access is granted.
 */
object StatusReporter {
    private const val EVERY_SEC = 30L
    private const val WEATHER_EVERY_MS = 20 * 60 * 1000L
    private const val WEATHER_RETRY_MS = 2 * 60 * 1000L

    private val exec = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var loop: ScheduledFuture<*>? = null
    private var pending: ScheduledFuture<*>? = null

    // Only touched on [exec].
    private var weather: Weather.Now? = null
    private var weatherKey = ""
    private var weatherAt = 0L
    private var weatherTriedAt = 0L

    /** Starts/stops the periodic updates to match the saved settings and sends the current state to the TV. */
    @Synchronized
    fun sync(c: Context, onResult: ((Boolean) -> Unit)? = null) {
        val ctx = c.applicationContext
        val on = Prefs.status(ctx).enabled
        if (on && loop == null) {
            loop = exec.scheduleWithFixedDelay(Runnable { runCatching { push(ctx, discover = false) } }, EVERY_SEC, EVERY_SEC, TimeUnit.SECONDS)
        } else if (!on) {
            loop?.cancel(false)
            loop = null
        }
        // Debounced so dragging a slider doesn't flood the TV.
        pending?.cancel(false)
        pending = exec.schedule(Runnable {
            val ok = runCatching { push(ctx, discover = true) }.getOrDefault(false)
            if (onResult != null) main.post { onResult(ok) }
        }, 400, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        loop?.cancel(false)
        loop = null
    }

    private fun push(ctx: Context, discover: Boolean): Boolean {
        if (!discover && Prefs.host(ctx) == null) return false
        val s = Prefs.status(ctx)
        val json = JSONObject().put("type", "status").put("config", s.toTvConfig())
        if (s.enabled && s.showBattery) json.put("battery", battery(ctx))
        if (s.enabled && s.showWeather && s.hasPlace) {
            weatherFor(s)?.let { w ->
                json.put(
                    "weather", JSONObject()
                        .put("temp", w.rounded)
                        .put("unit", if (s.fahrenheit) "F" else "C")
                        .put("icon", w.icon)
                )
            }
        }
        return TvSender.sendStatusBlocking(ctx, json, discover)
    }

    private fun weatherFor(s: StatusSettings): Weather.Now? {
        val key = "${s.lat},${s.lon},${s.fahrenheit}"
        val now = System.currentTimeMillis()
        if (key != weatherKey) {
            weather = null
            weatherKey = key
            weatherAt = 0L
            weatherTriedAt = 0L
        }
        val due = weather == null || now - weatherAt > WEATHER_EVERY_MS
        if (due && now - weatherTriedAt > WEATHER_RETRY_MS) {
            weatherTriedAt = now
            Weather.current(s.lat, s.lon, s.fahrenheit)?.let {
                weather = it
                weatherAt = now
            }
        }
        return weather
    }

    private fun battery(ctx: Context): JSONObject {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        return JSONObject()
            .put("level", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100))
            .put("charging", bm.isCharging)
    }
}
