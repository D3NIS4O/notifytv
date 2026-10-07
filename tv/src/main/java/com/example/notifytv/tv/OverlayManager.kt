package com.example.notifytv.tv

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class OverlayManager(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val current = mutableMapOf<String, View>()

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    fun show(j: JSONObject) {
        if (!Settings.canDrawOverlays(ctx)) return
        val corner = j.optString("corner", "TOP_RIGHT")
        val duration = j.optLong("durationMs", 5000)
        val bg = j.optInt("bgColor", 0xE6202020.toInt())
        val fg = j.optInt("textColor", Color.WHITE)

        current.remove(corner)?.let { old -> runCatching { wm.removeView(old) } }

        val view = buildView(j, bg, fg)
        val params = WindowManager.LayoutParams(
            dp(420), WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = when (corner) {
            "TOP_LEFT" -> Gravity.TOP or Gravity.START
            "BOTTOM_LEFT" -> Gravity.BOTTOM or Gravity.START
            "BOTTOM_RIGHT" -> Gravity.BOTTOM or Gravity.END
            else -> Gravity.TOP or Gravity.END
        }
        params.x = dp(32)
        params.y = dp(32)

        wm.addView(view, params)
        current[corner] = view
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(250).start()
        view.postDelayed({
            view.animate().alpha(0f).setDuration(300).withEndAction {
                runCatching { wm.removeView(view) }
                if (current[corner] === view) current.remove(corner)
            }.start()
        }, duration)
    }

    private fun text(value: String, size: Float, fg: Int, bold: Boolean = false, alpha: Float = 1f, lines: Int = 1) =
        TextView(ctx).apply {
            text = value
            textSize = size
            setTextColor(fg)
            this.alpha = alpha
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun buildView(j: JSONObject, bg: Int, fg: Int): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(16).toFloat() }
        }
        val iconB64 = j.optString("icon")
        if (iconB64.isNotEmpty()) {
            runCatching {
                val bytes = Base64.decode(iconB64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val iv = ImageView(ctx).apply { setImageBitmap(bmp) }
                val lp = LinearLayout.LayoutParams(dp(40), dp(40))
                lp.marginEnd = dp(14)
                root.addView(iv, lp)
            }
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(j.optString("appName"), 12f, fg, alpha = 0.7f))
        col.addView(text(j.optString("title"), 16f, fg, bold = true))
        val body = j.optString("text")
        if (body.isNotEmpty()) col.addView(text(body, 14f, fg, lines = 3))
        root.addView(col)
        return root
    }
}
