package com.example.notifytv.phone

import android.content.Context
import android.graphics.Bitmap
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

    fun ping(c: Context, onResult: (Boolean) -> Unit) {
        val ctx = c.applicationContext
        exec.execute {
            val ok = send(ctx, JSONObject().put("type", "ping").put("token", Prefs.token(ctx)))
            main.post { onResult(ok) }
        }
    }

    fun sendTest(c: Context, onResult: ((Boolean) -> Unit)? = null) =
        sendNotification(c, c.packageName, "Alex", "Hey! Are we still on for tonight? This is how your notifications will look on the TV.", onResult)

    fun sendNotification(c: Context, pkg: String, title: String, text: String, onResult: ((Boolean) -> Unit)? = null) {
        val ctx = c.applicationContext
        exec.execute {
            val st = Prefs.style(ctx)
            val pm = ctx.packageManager
            val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            val iconPx = (st.iconDp * 2).coerceIn(64, 192)
            val icon = runCatching {
                val bmp = pm.getApplicationIcon(pkg).toBitmap(iconPx, iconPx)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }.getOrDefault("")

            val json = st.toJson()
                .put("type", "notification")
                .put("token", Prefs.token(ctx))
                .put("appName", appName)
                .put("title", title)
                .put("text", text)
                .put("icon", icon)
                .put("durationMs", st.durationSec * 1000L)
                .put("bgColor", st.argb)
                .put("textColor", st.textColor)

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
                val line = s.getInputStream().bufferedReader().readLine() ?: return@use false
                JSONObject(line).optBoolean("ok", false)
            }
        }.getOrDefault(false)
    }
}
