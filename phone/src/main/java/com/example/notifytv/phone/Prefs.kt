package com.example.notifytv.phone

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun host(c: Context): String? = sp(c).getString("host", null)
    fun port(c: Context): Int = sp(c).getInt("port", 47321)
    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun setTarget(c: Context, host: String, port: Int, token: String) =
        sp(c).edit().putString("host", host).putInt("port", port).putString("token", token).apply()

    fun style(c: Context): NotifStyle = NotifStyle.fromJson(sp(c).getString("style", null))
    fun setStyle(c: Context, s: NotifStyle) = sp(c).edit().putString("style", s.toJson().toString()).apply()

    fun status(c: Context): StatusSettings = StatusSettings.fromJson(sp(c).getString("status", null))
    fun setStatus(c: Context, s: StatusSettings) = sp(c).edit().putString("status", s.toJson().toString()).apply()

    fun forwarding(c: Context): Boolean = sp(c).getBoolean("forwarding", true)
    fun setForwarding(c: Context, v: Boolean) = sp(c).edit().putBoolean("forwarding", v).apply()

    fun enabledApps(c: Context): Set<String> = sp(c).getStringSet("apps", emptySet())?.toSet() ?: emptySet()
    fun setEnabledApps(c: Context, v: Set<String>) = sp(c).edit().putStringSet("apps", HashSet(v)).apply()
}
