package com.example.notifytv.tv

import android.animation.LayoutTransition
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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import org.json.JSONObject

class OverlayManager(private val ctx: Context) {
    companion object {
        private const val MAX_PER_CORNER = 4
    }

    private class Style(j: JSONObject) {
        val corner: String = j.optString("corner", "TOP_RIGHT")
        val durationMs: Long = j.optLong("durationMs", 6000)
        val autoWidth: Boolean = j.optBoolean("autoWidth", true)
        val width: Int = j.optInt("widthDp", 420)
        val minHeight: Int = j.optInt("heightDp", 0)
        val appNameSp: Float = j.optInt("appNameSp", 12).toFloat()
        val titleSp: Float = j.optInt("titleSp", 16).toFloat()
        val textSp: Float = j.optInt("textSp", 14).toFloat()
        val bodyLines: Int = j.optInt("bodyLines", 4)
        val iconDp: Int = j.optInt("iconDp", 40)
        val radiusDp: Int = j.optInt("radiusDp", 16)
        val marginDp: Int = j.optInt("marginDp", 32)
        val padHDp: Int = j.optInt("padHDp", 16).coerceIn(0, 200)
        val padVDp: Int = j.optInt("padVDp", 14).coerceIn(0, 200)
        val bg: Int = j.optInt("bgColor", 0xEB202020.toInt())
        val fg: Int = j.optInt("textColor", Color.WHITE)
        val showIcon: Boolean = j.optBoolean("showIcon", true)
        val showAppName: Boolean = j.optBoolean("showAppName", true)
        val anim: String = j.optString("anim", "SLIDE")
        val animMs: Long = j.optLong("animMs", 350)
        val isTop: Boolean = corner.startsWith("TOP")
        val isRight: Boolean = corner.endsWith("RIGHT")
    }

    /** A LinearLayout that never measures wider than [maxW] pixels. */
    private class MaxWidthLinearLayout(ctx: Context, private val maxW: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val spec = when (mode) {
                MeasureSpec.EXACTLY -> widthMeasureSpec
                MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(minOf(size, maxW), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST)
            }
            super.onMeasure(spec, heightMeasureSpec)
        }
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private var root: FrameLayout? = null
    private val stacks = mutableMapOf<String, LinearLayout>()
    private val styles = HashMap<View, Style>()
    private val dismissing = HashSet<View>()

    private fun dp(v: Int) = (v * density).toInt()

    @Suppress("DEPRECATION")
    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    fun show(j: JSONObject) {
        if (!Settings.canDrawOverlays(ctx)) return
        val s = Style(j)
        val stack = stackFor(s)

        val live = (0 until stack.childCount).map { stack.getChildAt(it) }.filter { it !in dismissing }
        if (live.size >= MAX_PER_CORNER) dismiss(if (s.isTop) live.last() else live.first())

        val card = buildCard(j, s)
        styles[card] = s
        // Height is always WRAP_CONTENT so wrapped lines make the card taller.
        val lp = LinearLayout.LayoutParams(
            if (s.autoWidth) ViewGroup.LayoutParams.WRAP_CONTENT else dp(s.width),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(6)
        lp.bottomMargin = dp(6)
        card.alpha = 0f
        stack.addView(card, if (s.isTop) 0 else stack.childCount, lp)
        card.doOnPreDraw { animateIn(card, s, stack) }
        card.postDelayed({ dismiss(card) }, s.durationMs + s.animMs)
    }

    private fun ensureRoot(): FrameLayout {
        root?.let { return it }
        val r = FrameLayout(ctx).apply {
            clipChildren = false
            clipToPadding = false
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(r, p)
        root = r
        return r
    }

    private fun stackFor(s: Style): LinearLayout {
        val r = ensureRoot()
        val stack = stacks.getOrPut(s.corner) {
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
                layoutTransition = LayoutTransition().apply {
                    disableTransitionType(LayoutTransition.APPEARING)
                    disableTransitionType(LayoutTransition.DISAPPEARING)
                    setStartDelay(LayoutTransition.CHANGE_APPEARING, 0)
                    setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0)
                    setDuration(250)
                }
                r.addView(this)
            }
        }
        stack.gravity = if (s.isRight) Gravity.END else Gravity.START
        val vertical = if (s.isTop) Gravity.TOP else Gravity.BOTTOM
        val horizontal = if (s.isRight) Gravity.END else Gravity.START
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, vertical or horizontal)
        val h = dp(s.marginDp)
        val v = (dp(s.marginDp) - dp(6)).coerceAtLeast(0)
        lp.setMargins(h, v, h, v)
        stack.layoutParams = lp
        return stack
    }

    private fun offX(card: View, stack: View, s: Style): Float {
        val r = root ?: return 0f
        val left = stack.left + card.left
        return if (s.isRight) (r.width - left + dp(24)).toFloat() else -(left + card.width + dp(24)).toFloat()
    }

    private fun offY(card: View, stack: View, s: Style): Float {
        val r = root ?: return 0f
        val top = stack.top + card.top
        return if (s.isTop) -(top + card.height + dp(24)).toFloat() else (r.height - top + dp(24)).toFloat()
    }

    private fun animateIn(card: View, s: Style, stack: View) {
        val a = card.animate().setDuration(s.animMs)
        when (s.anim) {
            "FADE" -> {
                card.alpha = 0f
                a.alpha(1f).setInterpolator(DecelerateInterpolator())
            }
            "POP" -> {
                card.alpha = 0f
                card.scaleX = 0.8f
                card.scaleY = 0.8f
                a.alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(1.6f))
            }
            "DROP" -> {
                card.alpha = 1f
                card.translationY = offY(card, stack, s)
                a.translationY(0f).setInterpolator(DecelerateInterpolator(2f))
            }
            else -> {
                card.alpha = 1f
                card.translationX = offX(card, stack, s)
                a.translationX(0f).setInterpolator(DecelerateInterpolator(2f))
            }
        }
        a.start()
    }

    private fun dismiss(card: View) {
        val stack = card.parent as? LinearLayout ?: return
        if (!dismissing.add(card)) return
        val s = styles[card] ?: return
        card.animate().cancel()
        val a = card.animate().setDuration(s.animMs).setInterpolator(AccelerateInterpolator(1.5f))
        when (s.anim) {
            "FADE" -> a.alpha(0f)
            "POP" -> a.alpha(0f).scaleX(0.8f).scaleY(0.8f)
            "DROP" -> a.translationY(offY(card, stack, s)).alpha(0f)
            else -> a.translationX(offX(card, stack, s))
        }
        a.withEndAction {
            stack.removeView(card)
            dismissing.remove(card)
            styles.remove(card)
            cleanup()
        }.start()
    }

    private fun cleanup() {
        if (stacks.values.any { it.childCount > 0 }) return
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        stacks.clear()
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false, alpha: Float = 1f, lines: Int = 1) =
        TextView(ctx).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            this.alpha = alpha
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun buildCard(j: JSONObject, s: Style): View {
        val card = MaxWidthLinearLayout(ctx, dp(s.width)).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(s.padHDp), dp(s.padVDp), dp(s.padHDp), dp(s.padVDp))
            background = GradientDrawable().apply {
                setColor(s.bg)
                cornerRadius = dp(s.radiusDp).toFloat()
            }
            elevation = dp(8).toFloat()
            if (s.minHeight > 0) minimumHeight = dp(s.minHeight)
        }
        val iconB64 = j.optString("icon")
        if (s.showIcon && iconB64.isNotEmpty()) {
            runCatching {
                val bytes = Base64.decode(iconB64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val iv = ImageView(ctx).apply { setImageBitmap(bmp) }
                val lp = LinearLayout.LayoutParams(dp(s.iconDp), dp(s.iconDp))
                lp.marginEnd = dp(14)
                card.addView(iv, lp)
            }
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (s.showAppName) col.addView(text(j.optString("appName"), s.appNameSp, s.fg, alpha = 0.7f))
        col.addView(text(j.optString("title"), s.titleSp, s.fg, bold = true, lines = 2))
        val body = j.optString("text")
        if (body.isNotEmpty()) col.addView(text(body, s.textSp, s.fg, lines = s.bodyLines))
        val colLp = if (s.autoWidth) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        card.addView(col, colLp)
        return card
    }
}
