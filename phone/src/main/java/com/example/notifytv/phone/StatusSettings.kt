package com.example.notifytv.phone

import org.json.JSONObject

/** Settings for the always-on clock / phone battery / weather pill shown on the TV. */
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
        .apply {
            // JSONObject refuses NaN, so the location is only stored once a city has been picked.
            if (hasPlace) {
                put("lat", lat)
                put("lon", lon)
            }
        }

    /** The part the TV needs to draw the pill. */
    fun toTvConfig(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("corner", corner.name)
        .put("showClock", showClock)
        .put("clock24h", clock24h)
        .put("showBattery", showBattery)
        .put("showWeather", showWeather)
        .put("sizeSp", sizeSp)
        .put("marginDp", marginDp)
        .put("bgColor", (opacity.coerceIn(0, 100) * 255 / 100) shl 24)
        .put("textColor", 0xFFFFFFFF.toInt())

    companion object {
        fun fromJson(s: String?): StatusSettings {
            val d = StatusSettings()
            if (s == null) return d
            val j = runCatching { JSONObject(s) }.getOrNull() ?: return d
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
            )
        }
    }
}
