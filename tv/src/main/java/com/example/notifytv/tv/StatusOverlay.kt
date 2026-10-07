package com.example.notifytv.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Always-on status row in the corner picked on the phone: a weather badge, a phone battery badge and the clock.
 * The clock is always the item closest to the corner. Badges are outlined pills with a translucent dark fill,
 * styled like native TV status indicators. The clock runs on the TV; battery and weather come from the phone
 * and are hidden when they get stale.
 */
class StatusOverlay(private val ctx: Context) {
    companion object {
        private const val STALE_MS = 3 * 60 * 1000L
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("status", Context.MODE_PRIVATE)

    // Saved so the clock comes back after a TV reboot even before the phone reconnects.
    private var config: JSONObject =
        runCatching { JSONObject(prefs.getString("config", null) ?: "{}") }.getOrDefault(JSONObject())
    private var battery: JSONObject? = null
    private var weather: JSONObject? = null
    private var lastPhone = 0L

    private var row: LinearLayout? = null
    private var builtFor = ""
    private var badgeGap = 0
    private var clockGap = 0
    private lateinit var weatherBadge: LinearLayout
    private lateinit var weatherIcon: IconView
    private lateinit var weatherText: TextView
    private lateinit var batteryBadge: LinearLayout
    private lateinit var batteryIcon: IconView
    private lateinit var batteryText: TextView
    private lateinit var clockText: TextView

    val corner: String get() = config.optString("corner", "TOP_RIGHT")

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { render() }
            val now = System.currentTimeMillis()
            main.postDelayed(this, 60_000 - now % 60_000 + 50) // right after the minute changes
        }
    }

    private fun dp(v: Int) = (v * density).toInt()

    fun start() {
        main.removeCallbacks(ticker)
        ticker.run()
    }

    fun stop() {
        main.removeCallbacks(ticker)
        detach()
    }

    /** Distance from the top/bottom edge that notifications in [c] should keep so they don't cover the row. */
    fun reservedPx(c: String): Int {
        val r = row ?: return 0
        if (c != corner || r.visibility != View.VISIBLE) return 0
        val h = if (r.height > 0) r.height else dp(40)
        return dp(config.optInt("marginDp", 32).coerceIn(0, 300)) + h + dp(6)
    }

    fun update(j: JSONObject) {
        j.optJSONObject("config")?.let {
            config = it
            prefs.edit().putString("config", it.toString()).apply()
        }
        battery = j.optJSONObject("battery")
        weather = j.optJSONObject("weather")
        lastPhone = System.currentTimeMillis()
        render()
    }

    private fun render() {
        val showClock = config.optBoolean("showClock", true)
        val showBattery = config.optBoolean("showBattery", true)
        val showWeather = config.optBoolean("showWeather", true)
        val enabled = config.optBoolean("enabled", false)
        if (!enabled || !(showClock || showBattery || showWeather) || !Settings.canDrawOverlays(ctx)) {
            detach()
            return
        }
        val key = listOf("corner", "sizeSp", "marginDp", "bgColor", "textColor").joinToString("|") { config.opt(it)?.toString().orEmpty() }
        if (row == null || key != builtFor) {
            detach()
            if (runCatching { build() }.isFailure) {
                detach()
                return
            }
            builtFor = key
        }
        val r = row ?: return

        val fresh = System.currentTimeMillis() - lastPhone < STALE_MS

        val w = weather?.takeIf { fresh && showWeather }
        weatherBadge.visibility = if (w != null) View.VISIBLE else View.GONE
        if (w != null) {
            weatherIcon.weather = StatusIcons.weatherFor(w.optInt("code", 3), w.optBoolean("isDay", true))
            weatherIcon.invalidate()
            weatherText.text = "${w.optInt("temp")}\u00b0"
        }

        val b = battery?.takeIf { fresh && showBattery }
        batteryBadge.visibility = if (b != null) View.VISIBLE else View.GONE
        if (b != null) {
            val level = b.optInt("level", 0).coerceIn(0, 100)
            batteryIcon.level = level
            batteryIcon.charging = b.optBoolean("charging", false)
            batteryIcon.invalidate()
            batteryText.text = "$level%"
        }

        clockText.visibility = if (showClock) View.VISIBLE else View.GONE
        if (showClock) {
            val pattern = if (config.optBoolean("clock24h", true)) "HH:mm" else "h:mm"
            clockText.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        }

        applyGaps(r)
        // Nothing to show yet (e.g. clock off and the phone hasn't reported).
        r.visibility = if (showClock || b != null || w != null) View.VISIBLE else View.GONE
    }

    /** Small gap between the two badges, a slightly bigger one next to the clock; none before the first visible item. */
    private fun applyGaps(r: LinearLayout) {
        var prev: View? = null
        for (i in 0 until r.childCount) {
            val v = r.getChildAt(i)
            if (v.visibility != View.VISIBLE) continue
            val lp = v.layoutParams as LinearLayout.LayoutParams
            val gap = when {
                prev == null -> 0
                prev === clockText || v === clockText -> clockGap
                else -> badgeGap
            }
            if (lp.marginStart != gap) {
                lp.marginStart = gap
                v.layoutParams = lp
            }
            prev = v
        }
    }

    @Suppress("DEPRECATION")
    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun label(sizeSp: Float, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        includeFontPadding = false
    }

    /** Outlined pill: translucent dark fill, thin crisp border, icon then text, vertically centred. */
    private fun badge(unit: Float, fill: Int, fg: Int, icon: View, iconW: Int, iconH: Int, text: TextView) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((unit * 0.55f).toInt(), 0, (unit * 0.65f).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = 1000f // clamped to half the height -> pill
                setStroke((unit * 0.075f).roundToInt().coerceAtLeast(1), fg)
            }
            addView(icon, LinearLayout.LayoutParams(iconW, iconH).apply { marginEnd = (unit * 0.35f).toInt() })
            addView(text)
        }

    private fun build() {
        val size = config.optInt("sizeSp", 18).coerceIn(8, 60).toFloat()
        val unit = size * density
        val fg = config.optInt("textColor", Color.WHITE)
        val fill = config.optInt("bgColor", 0x99000000.toInt())
        val c = corner
        val isRight = c.endsWith("RIGHT")
        badgeGap = (unit * 0.4f).toInt()
        clockGap = (unit * 0.7f).toInt()

        weatherIcon = IconView(ctx, fg, battery = false)
        weatherText = label(size * 0.9f, fg)
        val weatherSide = (unit * 1.15f).toInt()
        weatherBadge = badge(unit, fill, fg, weatherIcon, weatherSide, weatherSide, weatherText)

        batteryIcon = IconView(ctx, fg, battery = true)
        batteryText = label(size * 0.9f, fg)
        batteryBadge = badge(unit, fill, fg, batteryIcon, (unit * 1.2f).toInt(), (unit * 0.62f).toInt(), batteryText)

        clockText = label(size * 1.15f, fg)

        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badgeH = (unit * 1.85f).toInt()
        // The clock is always the item closest to the corner; weather stays left of the battery.
        val items = if (isRight) listOf(weatherBadge, batteryBadge, clockText) else listOf(clockText, weatherBadge, batteryBadge)
        items.forEach { v ->
            val h = if (v === clockText) ViewGroup.LayoutParams.WRAP_CONTENT else badgeH
            r.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h))
        }

        val margin = dp(config.optInt("marginDp", 32).coerceIn(0, 300))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (c.startsWith("TOP")) Gravity.TOP else Gravity.BOTTOM) or
                (if (isRight) Gravity.RIGHT else Gravity.LEFT)
            x = margin
            y = margin
        }
        wm.addView(r, lp)
        row = r
    }

    private fun detach() {
        row?.let { runCatching { wm.removeView(it) } }
        row = null
        builtFor = ""
    }

    private class IconView(ctx: Context, private val color: Int, private val battery: Boolean) : View(ctx) {
        var weather = StatusIcons.Weather.CLOUDY
        var level = 0
        var charging = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            if (battery) {
                StatusIcons.drawBattery(canvas, 0f, 0f, w, h, level, charging, color)
            } else {
                val side = minOf(w, h)
                StatusIcons.drawWeather(canvas, weather, (w - side) / 2, (h - side) / 2, side, color)
            }
        }
    }
}
