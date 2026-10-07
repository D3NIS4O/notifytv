package com.example.notifytv.tv

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.os.Build

/**
 * Outline status icons (weather + battery) drawn with plain Android Canvas calls.
 * The same file lives in the phone and TV apps so the phone preview looks exactly like the TV.
 * Weather icons are designed on a 24x24 grid.
 */
object StatusIcons {
    enum class Weather { CLEAR_DAY, CLEAR_NIGHT, PARTLY_DAY, PARTLY_NIGHT, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    /** WMO weather code (Open-Meteo) -> icon. */
    fun weatherFor(code: Int, isDay: Boolean): Weather = when (code) {
        0 -> if (isDay) Weather.CLEAR_DAY else Weather.CLEAR_NIGHT
        1, 2 -> if (isDay) Weather.PARTLY_DAY else Weather.PARTLY_NIGHT
        3 -> Weather.CLOUDY
        45, 48 -> Weather.FOG
        51, 53, 55, 56, 57 -> Weather.DRIZZLE
        in 61..67, in 80..82 -> Weather.RAIN
        in 71..77, 85, 86 -> Weather.SNOW
        in 95..99 -> Weather.STORM
        else -> Weather.CLOUDY
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    /** Draws [w] as a white-outline icon inside the square at ([x], [y]) with side [size]. */
    fun drawWeather(c: Canvas, w: Weather, x: Float, y: Float, size: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.scale(size / 24f, size / 24f)
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        when (w) {
            Weather.CLEAR_DAY -> sun(c, 12f, 12f, 4.5f, 7f, 9.5f)
            Weather.CLEAR_NIGHT -> moon(c, 12f, 12f, 8f)
            Weather.PARTLY_DAY, Weather.PARTLY_NIGHT -> {
                val cl = cloud(1.5f, 3.5f)
                c.save()
                clipOut(c, cl)
                if (w == Weather.PARTLY_DAY) sun(c, 8.5f, 8.5f, 3.3f, 5.3f, 7f) else moon(c, 9f, 9f, 6f)
                c.restore()
                c.drawPath(cl, paint)
            }
            Weather.CLOUDY -> c.drawPath(cloud(0f, 1f), paint)
            Weather.FOG -> {
                c.drawPath(cloud(0f, -2f), paint)
                c.drawLine(5f, 18.5f, 19f, 18.5f, paint)
                c.drawLine(7f, 21.5f, 17f, 21.5f, paint)
            }
            Weather.DRIZZLE -> {
                c.drawPath(cloud(0f, -1.5f), paint)
                c.drawLine(9f, 18.5f, 8.3f, 20.5f, paint)
                c.drawLine(15f, 18.5f, 14.3f, 20.5f, paint)
                c.drawLine(12f, 20.5f, 11.3f, 22.5f, paint)
            }
            Weather.RAIN -> {
                c.drawPath(cloud(0f, -1.5f), paint)
                c.drawLine(8f, 18f, 6.8f, 21.8f, paint)
                c.drawLine(12f, 18f, 10.8f, 21.8f, paint)
                c.drawLine(16f, 18f, 14.8f, 21.8f, paint)
            }
            Weather.SNOW -> {
                c.drawPath(cloud(0f, -1.5f), paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(8f, 19.5f, 1.2f, paint)
                c.drawCircle(12f, 21.5f, 1.2f, paint)
                c.drawCircle(16f, 19.5f, 1.2f, paint)
                paint.style = Paint.Style.STROKE
            }
            Weather.STORM -> {
                c.drawPath(cloud(0f, -1.5f), paint)
                path.reset()
                path.moveTo(13f, 16.5f)
                path.lineTo(10.2f, 20f)
                path.lineTo(13.2f, 20f)
                path.lineTo(10.8f, 23.3f)
                c.drawPath(path, paint)
            }
        }
        c.restore()
    }

    /**
     * Horizontal battery outline in ([x], [y], [w], [h]) with the terminal on the right.
     * Shows a lightning bolt while charging, otherwise a fill for the level.
     */
    fun drawBattery(c: Canvas, x: Float, y: Float, w: Float, h: Float, level: Int, charging: Boolean, color: Int) {
        val stroke = (h * 0.11f).coerceAtLeast(1.5f)
        val cap = w * 0.09f
        val bodyRight = x + w - cap
        val r = h * 0.24f
        paint.color = color

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        rect.set(x + stroke / 2, y + stroke / 2, bodyRight - stroke / 2, y + h - stroke / 2)
        c.drawRoundRect(rect, r, r, paint)

        paint.style = Paint.Style.FILL
        rect.set(bodyRight + stroke * 0.3f, y + h * 0.32f, x + w, y + h * 0.68f)
        c.drawRoundRect(rect, cap * 0.4f, cap * 0.4f, paint)

        if (charging) {
            val cx = x + (bodyRight - x) / 2
            val cy = y + h / 2
            val b = h * 0.64f
            path.reset()
            path.moveTo(cx + b * 0.12f, cy - b / 2)
            path.lineTo(cx - b * 0.32f, cy + b * 0.08f)
            path.lineTo(cx - b * 0.02f, cy + b * 0.08f)
            path.lineTo(cx - b * 0.12f, cy + b / 2)
            path.lineTo(cx + b * 0.32f, cy - b * 0.08f)
            path.lineTo(cx + b * 0.02f, cy - b * 0.08f)
            path.close()
            c.drawPath(path, paint)
        } else {
            val inset = stroke * 2f
            val full = bodyRight - x - inset * 2
            rect.set(x + inset, y + inset, x + inset + full * level.coerceIn(0, 100) / 100f, y + h - inset)
            c.drawRoundRect(rect, r * 0.5f, r * 0.5f, paint)
        }
    }

    /** Cloud spanning x 3.5..20.5, y 5..17 on the 24 grid, moved by ([dx], [dy]). */
    private fun cloud(dx: Float, dy: Float): Path {
        path.reset()
        path.moveTo(7f + dx, 17f + dy)
        arc(7f + dx, 13.5f + dy, 3.5f, 90f, 180f)
        arc(12f + dx, 10f + dy, 5f, 180f, 180f)
        arc(17f + dx, 13.5f + dy, 3.5f, 270f, 180f)
        path.close()
        return path
    }

    private fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        path.arcTo(rect, start, sweep, false)
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, rayFrom: Float, rayTo: Float) {
        c.drawCircle(cx, cy, r, paint)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val cs = Math.cos(a).toFloat()
            val sn = Math.sin(a).toFloat()
            c.drawLine(cx + cs * rayFrom, cy + sn * rayFrom, cx + cs * rayTo, cy + sn * rayTo, paint)
        }
    }

    private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
        val outer = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val bite = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.45f, r * 0.8f, Path.Direction.CW) }
        outer.op(bite, Path.Op.DIFFERENCE)
        c.drawPath(outer, paint)
    }

    @Suppress("DEPRECATION")
    private fun clipOut(c: Canvas, p: Path) {
        if (Build.VERSION.SDK_INT >= 26) c.clipOutPath(p) else c.clipPath(p, Region.Op.DIFFERENCE)
    }
}
