package com.example.notifytv.phone

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Current weather from Open-Meteo (free, no API key, no location permission needed). All calls are blocking. */
object Weather {
    data class Place(val name: String, val lat: Double, val lon: Double)
    data class Now(val temp: Double, val code: Int, val isDay: Boolean) {
        val icon: String get() = emoji(code, isDay)
        val rounded: Int get() = Math.round(temp).toInt()
    }

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    /** Finds a city by name, e.g. "Varna". */
    fun geocode(query: String): Place? = runCatching {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language.ifEmpty { "en" }
        val r = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=$lang&format=json")
        val first = r.optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null
        val name = listOf(first.optString("name"), first.optString("country"))
            .filter { it.isNotBlank() }
            .joinToString(", ")
        Place(name, first.getDouble("latitude"), first.getDouble("longitude"))
    }.getOrNull()

    fun current(lat: Double, lon: Double, fahrenheit: Boolean): Now? = runCatching {
        // Locale.US so the decimal separator is always a dot (Bulgarian uses a comma).
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day%s",
            lat, lon, if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        )
        val cur = get(url).getJSONObject("current")
        Now(cur.getDouble("temperature_2m"), cur.optInt("weather_code", 0), cur.optInt("is_day", 1) == 1)
    }.getOrNull()

    /** WMO weather code -> emoji. */
    fun emoji(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "\u2600\uFE0F" else "\uD83C\uDF19"             // clear
        1, 2 -> if (isDay) "\u26C5" else "\u2601\uFE0F"                  // partly cloudy
        3 -> "\u2601\uFE0F"                                               // overcast
        45, 48 -> "\uD83C\uDF2B\uFE0F"                                   // fog
        51, 53, 55, 56, 57 -> "\uD83C\uDF26\uFE0F"                       // drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> "\uD83C\uDF27\uFE0F"           // rain
        71, 73, 75, 77, 85, 86 -> "\u2744\uFE0F"                          // snow
        95, 96, 99 -> "\u26C8\uFE0F"                                      // thunderstorm
        else -> "\uD83C\uDF21\uFE0F"
    }
}
