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
 * styled like native TV status indicators; every badge's look (font, icon, height, padding, roundness, border,
 * opacity) is set on the phone. The clock runs on the TV; battery and weather come from the phone and the last
 * known values stay on screen indefinitely (also across TV reboots) until the phone sends newer ones.
 */
class StatusOverlay(private val ctx: Context) {

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("status", Context.MODE_PRIVATE)

    // Saved so the clock comes back after a TV reboot even before the phone reconnects.
    private var config: JSONObject =
        runCatching { JSONObject(prefs.getString("config", null) ?: "{}") }.getOrDefault(JSONObject())
    private var battery: JSONObject? = load("battery")
    private var weather: JSONObject? = load("weather")

    private var row: LinearLayout? = null
    private var builtFor = ""
    private var badgeGap = 0
    private var clockGap = 0
    private lateinit var weatherBadge: LinearLayout
    private lateinit var weatherIcon: IconView
    private lateinit var weatherText: TextView
    private lateinit var batteryBadge: LinearLayout
    private lateinit var batteryIcon: IconView
    private lateinit var boltIcon: IconView
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
    private fun dp(v: Float) = (v * density).toInt()

    private fun load(key: String): JSONObject? =
        prefs.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }

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
        // Keep the last known values: a message without battery/weather (e.g. a failed weather fetch) doesn't clear them.
        j.optJSONObject("battery")?.let {
            battery = it
            prefs.edit().putString("battery", it.toString()).apply()
        }
        j.optJSONObject("weather")?.let {
            weather = it
            prefs.edit().putString("weather", it.toString()).apply()
        }
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
        val key = listOf("corner", "sizeSp", "marginDp", "bgColor", "textColor", "gapDp", "clockSp", "clockBold", "weatherStyle", "batteryStyle")
            .joinToString("|") { config.opt(it)?.toString().orEmpty() }
        if (row == null || key != builtFor) {
            detach()
            if (runCatching { build() }.isFailure) {
                detach()
                return
            }
            builtFor = key
        }
        val r = row ?: return

        val w = weather?.takeIf { showWeather }
        weatherBadge.visibility = if (w != null) View.VISIBLE else View.GONE
        if (w != null) {
            weatherIcon.weather = StatusIcons.weatherFor(w.optInt("code", 3), w.optBoolean("isDay", true))
            weatherIcon.invalidate()
            weatherText.text = "${w.optInt("temp")}\u00b0"
        }

        val b = battery?.takeIf { showBattery }
        batteryBadge.visibility = if (b != null) View.VISIBLE else View.GONE
        if (b != null) {
            val level = b.optInt("level", 0).coerceIn(0, 100)
            batteryIcon.level = level
            batteryIcon.invalidate()
            boltIcon.visibility = if (b.optBoolean("charging", false)) View.VISIBLE else View.GONE
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

    private fun label(sizeSp: Float, color: Int, bold: Boolean) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        includeFontPadding = false
    }

    /** One badge's look as set on the phone. Older phone versions don't send it, so everything has a default. */
    private class Style(j: JSONObject?, defIconDp: Int, fallbackBg: Int) {
        val textSp = (j?.optInt("textSp", 16) ?: 16).coerceIn(4, 120).toFloat()
        val bold = j?.optBoolean("bold", true) ?: true
        val iconDp = (j?.optInt("iconDp", defIconDp) ?: defIconDp).coerceIn(4, 160)
        val heightDp = (j?.optInt("heightDp", 34) ?: 34).coerceIn(8, 200)
        val paddingDp = (j?.optInt("paddingDp", 10) ?: 10).coerceIn(0, 100)
        val borderTenths = (j?.optInt("borderTenths", 14) ?: 14).coerceIn(0, 200)
        val radiusPct = (j?.optInt("radiusPct", 100) ?: 100).coerceIn(0, 100)
        val bgColor = j?.optInt("bgColor", fallbackBg) ?: fallbackBg
    }

    /** Badge: translucent fill, optional border, icon(s) then text, vertically centred. */
    private fun badge(st: Style, fg: Int, icons: List<Pair<View, LinearLayout.LayoutParams>>, text: TextView): LinearLayout {
        val heightPx = dp(st.heightDp)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dp(st.paddingDp)
            setPadding(pad, 0, pad, 0)
            background = GradientDrawable().apply {
                setColor(st.bgColor)
                cornerRadius = heightPx / 2f * st.radiusPct / 100f
                if (st.borderTenths > 0) setStroke((st.borderTenths * density / 10f).roundToInt().coerceAtLeast(1), fg)
            }
            icons.forEach { (v, lp) -> addView(v, lp) }
            addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(st.iconDp * 0.3f)
            })
        }
    }

    private fun build() {
        val fg = config.optInt("textColor", Color.WHITE)
        val fallbackBg = config.optInt("bgColor", 0x99000000.toInt())
        val c = corner
        val isRight = c.endsWith("RIGHT")
        val gap = config.optInt("gapDp", 8).coerceIn(0, 100)
        badgeGap = dp(gap)
        clockGap = dp(gap * 1.6f)

        val ws = Style(config.optJSONObject("weatherStyle"), 21, fallbackBg)
        weatherIcon = IconView(ctx, fg, IconView.Kind.WEATHER)
        weatherText = label(ws.textSp, fg, ws.bold)
        val wSide = dp(ws.iconDp)
        weatherBadge = badge(ws, fg, listOf(weatherIcon to LinearLayout.LayoutParams(wSide, wSide)), weatherText)

        // Upright battery with the charging bolt to its right (hidden when not charging).
        val bs = Style(config.optJSONObject("batteryStyle"), 20, fallbackBg)
        val ih = bs.iconDp.toFloat()
        batteryIcon = IconView(ctx, fg, IconView.Kind.BATTERY)
        boltIcon = IconView(ctx, fg, IconView.Kind.BOLT).apply { visibility = View.GONE }
        batteryText = label(bs.textSp, fg, bs.bold)
        batteryBadge = badge(
            bs, fg,
            listOf(
                batteryIcon to LinearLayout.LayoutParams(dp(ih * 0.55f), dp(ih)),
                boltIcon to LinearLayout.LayoutParams(dp(ih * 0.42f), dp(ih * 0.72f)).apply { marginStart = dp(ih * 0.12f) },
            ),
            batteryText
        )

        clockText = label(config.optInt("clockSp", 21).coerceIn(4, 160).toFloat(), fg, config.optBoolean("clockBold", true))

        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // The clock is always the item closest to the corner; weather stays left of the battery.
        val items = if (isRight) listOf(weatherBadge, batteryBadge, clockText) else listOf(clockText, weatherBadge, batteryBadge)
        items.forEach { v ->
            val h = when (v) {
                weatherBadge -> dp(ws.heightDp)
                batteryBadge -> dp(bs.heightDp)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
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

    private class IconView(ctx: Context, private val color: Int, private val kind: Kind) : View(ctx) {
        enum class Kind { WEATHER, BATTERY, BOLT }

        var weather = StatusIcons.Weather.CLOUDY
        var level = 0

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            when (kind) {
                Kind.BATTERY -> StatusIcons.drawBattery(canvas, 0f, 0f, w, h, level, color)
                Kind.BOLT -> StatusIcons.drawBolt(canvas, 0f, 0f, w, h, color)
                Kind.WEATHER -> {
                    val side = minOf(w, h)
                    StatusIcons.drawWeather(canvas, weather, (w - side) / 2, (h - side) / 2, side, color)
                }
            }
        }
    }
}
