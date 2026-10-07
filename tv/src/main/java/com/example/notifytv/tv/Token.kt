package com.example.notifytv.tv

import android.content.Context
import java.util.UUID

object Token {
    fun get(c: Context): String {
        val sp = c.getSharedPreferences("tv", Context.MODE_PRIVATE)
        val existing = sp.getString("token", null)
        if (existing != null) return existing
        val t = UUID.randomUUID().toString().replace("-", "").take(12)
        sp.edit().putString("token", t).apply()
        return t
    }
}
