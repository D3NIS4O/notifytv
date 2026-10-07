package com.example.notifytv.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Always-on pill with the phone's battery, the weather and a clock, in the corner picked on the phone.
 * The clock runs on the TV; battery and weather are pushed by the phone and hidden when they get stale.
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

    private var pill: LinearLayout? = null
    private var builtFor = ""
    private lateinit var batteryBox: LinearLayout
    private lateinit var batteryIcon: BatteryView
    private lateinit var batteryText: TextView
    private lateinit var weatherText: TextView
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

    /** Distance from the top/bottom edge that notifications in [c] should keep so they don't cover the pill. */
    fun reservedPx(c: String): Int {
        val p = pill ?: return 0
        if (c != corner || p.visibility != View.VISIBLE) return 0
        val h = if (p.height > 0) p.height else dp(40)
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
        if (pill == null || key != builtFor) {
            detach()
            if (runCatching { build() }.isFailure) {
                detach()
                return
            }
            builtFor = key
        }
        val p = pill ?: return

        val fresh = System.currentTimeMillis() - lastPhone < STALE_MS
        val b = battery?.takeIf { fresh && showBattery }
        batteryBox.visibility = if (b != null) View.VISIBLE else View.GONE
        if (b != null) {
            val level = b.optInt("level", 0).coerceIn(0, 100)
            batteryIcon.set(level, b.optBoolean("charging", false))
            batteryText.text = "$level%"
        }

        val w = weather?.takeIf { fresh && showWeather }
        weatherText.visibility = if (w != null) View.VISIBLE else View.GONE
        if (w != null) weatherText.text = "${w.optString("icon")} ${w.optInt("temp")}\u00b0"

        clockText.visibility = if (showClock) View.VISIBLE else View.GONE
        if (showClock) {
            val pattern = if (config.optBoolean("clock24h", true)) "HH:mm" else "h:mm"
            clockText.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        }

        // Hide the empty pill, e.g. clock turned off and the phone hasn't reported yet.
        p.visibility = if (showClock || b != null || w != null) View.VISIBLE else View.GONE
    }

    @Suppress("DEPRECATION")
    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun label(sizeSp: Float, color: Int) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        maxLines = 1
        includeFontPadding = false
        setShadowLayer(4f, 0f, 1f, 0x66000000)
    }

    private fun build() {
        val size = config.optInt("sizeSp", 18).coerceIn(8, 60).toFloat()
        val unit = size * density
        val fg = config.optInt("textColor", Color.WHITE)
        val bg = config.optInt("bgColor", 0x99000000.toInt())

        val p = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (unit * 0.8f).toInt()
            val padV = (unit * 0.4f).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 1000f // clamped to half the height -> pill shape
            }
            // Gap between items that disappears together with hidden items.
            dividerDrawable = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setSize((unit * 0.8f).toInt(), 1)
            }
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        }

        batteryIcon = BatteryView(ctx, fg)
        batteryText = label(size * 0.85f, fg)
        batteryBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val h = (unit * 0.7f).toInt()
            addView(batteryIcon, LinearLayout.LayoutParams((h * 1.9f).toInt(), h).apply { marginEnd = (unit * 0.3f).toInt() })
            addView(batteryText)
        }
        weatherText = label(size * 0.85f, fg)
        clockText = label(size, fg).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

        p.addView(batteryBox)
        p.addView(weatherText)
        p.addView(clockText)

        val c = corner
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
                (if (c.endsWith("RIGHT")) Gravity.RIGHT else Gravity.LEFT)
            x = margin
            y = margin
        }
        wm.addView(p, lp)
        pill = p
    }

    private fun detach() {
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
        builtFor = ""
    }

    /** Battery outline with a fill for the level: green while charging, red at 15% or less. */
    private class BatteryView(ctx: Context, private val color: Int) : View(ctx) {
        private var level = 0
        private var charging = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        fun set(newLevel: Int, isCharging: Boolean) {
            if (newLevel == level && isCharging == charging) return
            level = newLevel
            charging = isCharging
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cap = w * 0.08f
            val body = w - cap
            val stroke = h * 0.12f
            val r = h * 0.22f

            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            rect.set(stroke / 2, stroke / 2, body - stroke / 2, h - stroke / 2)
            canvas.drawRoundRect(rect, r, r, paint)

            paint.style = Paint.Style.FILL
            rect.set(body, h * 0.3f, w, h * 0.7f)
            canvas.drawRect(rect, paint)

            paint.color = when {
                charging -> 0xFF66BB6A.toInt()
                level <= 15 -> 0xFFEF5350.toInt()
                else -> color
            }
            val inset = stroke * 1.8f
            rect.set(inset, inset, inset + (body - inset * 2) * level / 100f, h - inset)
            canvas.drawRoundRect(rect, r / 2, r / 2, paint)
        }
    }
}
