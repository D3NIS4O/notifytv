package com.example.notifytv.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

object TvSender {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun sendNotification(c: Context, pkg: String, title: String, text: String, onResult: ((Boolean) -> Unit)? = null) {
        val ctx = c.applicationContext
        exec.execute {
            val pm = ctx.packageManager
            val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            val icon = runCatching {
                val bmp = pm.getApplicationIcon(pkg).toBitmap(96, 96)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }.getOrDefault("")

            val bg = Prefs.bgColor(ctx)
            val json = JSONObject()
                .put("type", "notification")
                .put("token", Prefs.token(ctx))
                .put("appName", appName)
                .put("title", title)
                .put("text", text)
                .put("icon", icon)
                .put("corner", Prefs.corner(ctx))
                .put("durationMs", Prefs.durationSec(ctx) * 1000L)
                .put("bgColor", bg)
                .put("textColor", if (Color.luminance(bg) > 0.5f) Color.BLACK else Color.WHITE)

            val ok = send(ctx, json)
            if (onResult != null) main.post { onResult(ok) }
        }
    }

    private fun send(c: Context, json: JSONObject): Boolean {
        val host = Prefs.host(c) ?: return false
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, Prefs.port(c)), 3000)
                s.soTimeout = 3000
                val out = s.getOutputStream()
                out.write((json.toString() + "\n").toByteArray())
                out.flush()
                s.getInputStream().bufferedReader().readLine()?.contains("\"ok\":true") == true
            }
        }.getOrDefault(false)
    }
}
