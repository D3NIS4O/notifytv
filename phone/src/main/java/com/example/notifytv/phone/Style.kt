package com.example.notifytv.phone

import org.json.JSONObject

enum class Corner(val label: String) {
    TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");

    val isTop: Boolean get() = this == TOP_LEFT || this == TOP_RIGHT
    val isRight: Boolean get() = this == TOP_RIGHT || this == BOTTOM_RIGHT
}

enum class Anim(val label: String) { SLIDE("Slide"), DROP("Drop"), FADE("Fade"), POP("Pop") }

data class NotifStyle(
    val corner: Corner = Corner.TOP_RIGHT,
    val durationSec: Int = 6,
    val autoWidth: Boolean = true,
    val widthDp: Int = 420,
    val heightDp: Int = 0,
    val appNameSp: Int = 12,
    val titleSp: Int = 16,
    val textSp: Int = 14,
    val bodyLines: Int = 4,
    val iconDp: Int = 40,
    val radiusDp: Int = 16,
    val marginDp: Int = 32,
    val padHDp: Int = 16,
    val padVDp: Int = 14,
    val color: Int = 0x202020,
    val opacity: Int = 92,
    val showIcon: Boolean = true,
    val showAppName: Boolean = true,
    val anim: Anim = Anim.SLIDE,
    val animMs: Int = 350,
) {
    val argb: Int get() = ((opacity.coerceIn(0, 100) * 255 / 100) shl 24) or (color and 0xFFFFFF)

    val textColor: Int
        get() = if (android.graphics.Color.luminance(color or 0xFF000000.toInt()) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    fun toJson(): JSONObject = JSONObject()
        .put("corner", corner.name)
        .put("durationSec", durationSec)
        .put("autoWidth", autoWidth)
        .put("widthDp", widthDp)
        .put("heightDp", heightDp)
        .put("appNameSp", appNameSp)
        .put("titleSp", titleSp)
        .put("textSp", textSp)
        .put("bodyLines", bodyLines)
        .put("iconDp", iconDp)
        .put("radiusDp", radiusDp)
        .put("marginDp", marginDp)
        .put("padHDp", padHDp)
        .put("padVDp", padVDp)
        .put("color", color)
        .put("opacity", opacity)
        .put("showIcon", showIcon)
        .put("showAppName", showAppName)
        .put("anim", anim.name)
        .put("animMs", animMs)

    companion object {
        fun fromJson(s: String?): NotifStyle {
            val d = NotifStyle()
            if (s == null) return d
            val j = runCatching { JSONObject(s) }.getOrNull() ?: return d
            return NotifStyle(
                corner = runCatching { Corner.valueOf(j.optString("corner")) }.getOrDefault(d.corner),
                durationSec = j.optInt("durationSec", d.durationSec),
                autoWidth = j.optBoolean("autoWidth", d.autoWidth),
                widthDp = j.optInt("widthDp", d.widthDp),
                heightDp = j.optInt("heightDp", d.heightDp),
                appNameSp = j.optInt("appNameSp", d.appNameSp),
                titleSp = j.optInt("titleSp", d.titleSp),
                textSp = j.optInt("textSp", d.textSp),
                bodyLines = j.optInt("bodyLines", d.bodyLines),
                iconDp = j.optInt("iconDp", d.iconDp),
                radiusDp = j.optInt("radiusDp", d.radiusDp),
                marginDp = j.optInt("marginDp", d.marginDp),
                padHDp = j.optInt("padHDp", d.padHDp),
                padVDp = j.optInt("padVDp", d.padVDp),
                color = j.optInt("color", d.color),
                opacity = j.optInt("opacity", d.opacity),
                showIcon = j.optBoolean("showIcon", d.showIcon),
                showAppName = j.optBoolean("showAppName", d.showAppName),
                anim = runCatching { Anim.valueOf(j.optString("anim")) }.getOrDefault(d.anim),
                animMs = j.optInt("animMs", d.animMs),
            )
        }
    }
}
