package com.example.notifytv.phone

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun host(c: Context): String? = sp(c).getString("host", null)
    fun port(c: Context): Int = sp(c).getInt("port", 47321)
    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun setTarget(c: Context, host: String, port: Int, token: String) =
        sp(c).edit().putString("host", host).putInt("port", port).putString("token", token).apply()

    fun corner(c: Context): String = sp(c).getString("corner", "TOP_RIGHT") ?: "TOP_RIGHT"
    fun setCorner(c: Context, v: String) = sp(c).edit().putString("corner", v).apply()

    fun durationSec(c: Context): Int = sp(c).getInt("duration", 6)
    fun setDurationSec(c: Context, v: Int) = sp(c).edit().putInt("duration", v).apply()

    fun bgColor(c: Context): Int = sp(c).getInt("bg", 0xE6202020.toInt())
    fun setBgColor(c: Context, v: Int) = sp(c).edit().putInt("bg", v).apply()

    fun enabledApps(c: Context): Set<String> = sp(c).getStringSet("apps", emptySet())?.toSet() ?: emptySet()
    fun setEnabledApps(c: Context, v: Set<String>) = sp(c).edit().putStringSet("apps", HashSet(v)).apply()
}
