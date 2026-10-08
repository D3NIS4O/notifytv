package com.example.notifytv.phone

import org.json.JSONObject

/** Look of one badge (weather or battery) on the TV. Sizes are in TV dp/sp. */
data class BadgeStyle(
    val textSp: Int = 16,
    val bold: Boolean = true,
    val iconDp: Int = 21,
    val heightDp: Int = 34,
    val paddingDp: Int = 10,
    /** Border thickness in tenths of a dp (14 = 1.4 dp). 0 = no border. */
    val borderTenths: Int = 14,
    /** 0 = square corners, 100 = fully rounded pill. */
    val radiusPct: Int = 100,
    val opacity: Int = 60,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("textSp", textSp)
        .put("bold", bold)
        .put("iconDp", iconDp)
        .put("heightDp", heightDp)
        .put("paddingDp", paddingDp)
        .put("borderTenths", borderTenths)
        .put("radiusPct", radiusPct)
        .put("opacity", opacity)

    /** What the TV needs: same as [toJson] plus the ready-made fill colour. */
    fun toTvJson(): JSONObject = toJson().put("bgColor", (opacity.coerceIn(0, 100) * 255 / 100) shl 24)

    companion object {
        val WEATHER = BadgeStyle()
        val BATTERY = BadgeStyle(iconDp = 20)

        fun fromJson(j: JSONObject?, d: BadgeStyle, legacyOpacity: Int?): BadgeStyle {
            val fallbackOpacity = legacyOpacity ?: d.opacity
            if (j == null) return d.copy(opacity = fallbackOpacity)
            return BadgeStyle(
                textSp = j.optInt("textSp", d.textSp),
                bold = j.optBoolean("bold", d.bold),
                iconDp = j.optInt("iconDp", d.iconDp),
                heightDp = j.optInt("heightDp", d.heightDp),
                paddingDp = j.optInt("paddingDp", d.paddingDp),
                borderTenths = j.optInt("borderTenths", d.borderTenths),
                radiusPct = j.optInt("radiusPct", d.radiusPct),
                opacity = j.optInt("opacity", fallbackOpacity),
            )
        }
    }
}

/** Settings for the always-on clock / phone battery / weather row shown on the TV. */
data class StatusSettings(
    val enabled: Boolean = false,
    val corner: Corner = Corner.TOP_RIGHT,
    val showClock: Boolean = true,
    val clock24h: Boolean = true,
    val showBattery: Boolean = true,
    val showWeather: Boolean = true,
    val fahrenheit: Boolean = false,
    val city: String = "",
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
    val sizeSp: Int = 18,
    val marginDp: Int = 32,
    val opacity: Int = 60,
    val gapDp: Int = 8,
    val clockSp: Int = 21,
    val clockBold: Boolean = true,
    val weatherStyle: BadgeStyle = BadgeStyle.WEATHER,
    val batteryStyle: BadgeStyle = BadgeStyle.BATTERY,
) {
    val hasPlace: Boolean get() = !lat.isNaN() && !lon.isNaN()

    val summary: String
        get() {
            if (!enabled) return "Off"
            val parts = listOfNotNull(
                "clock".takeIf { showClock },
                "battery".takeIf { showBattery },
                "weather".takeIf { showWeather },
            )
            return "${corner.label} \u00b7 " + parts.joinToString(", ").ifEmpty { "nothing selected" }
        }

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("corner", corner.name)
        .put("showClock", showClock)
        .put("clock24h", clock24h)
        .put("showBattery", showBattery)
        .put("showWeather", showWeather)
        .put("fahrenheit", fahrenheit)
        .put("city", city)
        .put("sizeSp", sizeSp)
        .put("marginDp", marginDp)
        .put("opacity", opacity)
        .put("gapDp", gapDp)
        .put("clockSp", clockSp)
        .put("clockBold", clockBold)
        .put("weatherStyle", weatherStyle.toJson())
        .put("batteryStyle", batteryStyle.toJson())
        .apply {
            // JSONObject refuses NaN, so the location is only stored once a city has been picked.
            if (hasPlace) {
                put("lat", lat)
                put("lon", lon)
            }
        }

    /** The part the TV needs to draw the row. */
    fun toTvConfig(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("corner", corner.name)
        .put("showClock", showClock)
        .put("clock24h", clock24h)
        .put("showBattery", showBattery)
        .put("showWeather", showWeather)
        .put("sizeSp", sizeSp)
        .put("marginDp", marginDp)
        .put("gapDp", gapDp)
        .put("clockSp", clockSp)
        .put("clockBold", clockBold)
        .put("weatherStyle", weatherStyle.toTvJson())
        .put("batteryStyle", batteryStyle.toTvJson())
        .put("bgColor", (opacity.coerceIn(0, 100) * 255 / 100) shl 24)
        .put("textColor", 0xFFFFFFFF.toInt())

    companion object {
        fun fromJson(s: String?): StatusSettings {
            val d = StatusSettings()
            if (s == null) return d
            val j = runCatching { JSONObject(s) }.getOrNull() ?: return d
            // Before 5.2 there was one opacity for the whole row; reuse it for both badges.
            val legacyOpacity = if (j.has("opacity")) j.optInt("opacity", d.opacity) else null
            return StatusSettings(
                enabled = j.optBoolean("enabled", d.enabled),
                corner = runCatching { Corner.valueOf(j.optString("corner")) }.getOrDefault(d.corner),
                showClock = j.optBoolean("showClock", d.showClock),
                clock24h = j.optBoolean("clock24h", d.clock24h),
                showBattery = j.optBoolean("showBattery", d.showBattery),
                showWeather = j.optBoolean("showWeather", d.showWeather),
                fahrenheit = j.optBoolean("fahrenheit", d.fahrenheit),
                city = j.optString("city", d.city),
                lat = j.optDouble("lat", Double.NaN),
                lon = j.optDouble("lon", Double.NaN),
                sizeSp = j.optInt("sizeSp", d.sizeSp),
                marginDp = j.optInt("marginDp", d.marginDp),
                opacity = j.optInt("opacity", d.opacity),
                gapDp = j.optInt("gapDp", d.gapDp),
                clockSp = j.optInt("clockSp", d.clockSp),
                clockBold = j.optBoolean("clockBold", d.clockBold),
                weatherStyle = BadgeStyle.fromJson(j.optJSONObject("weatherStyle"), BadgeStyle.WEATHER, legacyOpacity),
                batteryStyle = BadgeStyle.fromJson(j.optJSONObject("batteryStyle"), BadgeStyle.BATTERY, legacyOpacity),
            )
        }
    }
}
