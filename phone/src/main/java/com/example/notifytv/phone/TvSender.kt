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
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors

object TvSender {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Addresses that are checked when looking for the TV automatically. */
    val SCAN_HOSTS = (101..109).map { "192.168.0.$it" }
    private const val PORT = 47321

    fun ping(c: Context, onResult: (Boolean) -> Unit) {
        val ctx = c.applicationContext
        exec.execute {
            val ok = sendOrDiscover(ctx, JSONObject().put("type", "ping").put("token", Prefs.token(ctx)))
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

            val ok = sendOrDiscover(ctx, json)
            if (onResult != null) main.post { onResult(ok) }
        }
    }

    /** Sends to the saved TV; if that fails, looks for the TV on 192.168.0.101-109 and tries once more. */
    private fun sendOrDiscover(c: Context, json: JSONObject): Boolean {
        if (send(c, json)) return true
        if (!discoverBlocking(c)) return false
        json.put("token", Prefs.token(c))
        return send(c, json)
    }

    /** Checks all [SCAN_HOSTS] at the same time and saves the first NotifyTV TV that answers. Blocking. */
    private fun discoverBlocking(c: Context): Boolean {
        val pool = Executors.newFixedThreadPool(SCAN_HOSTS.size)
        try {
            val results = ExecutorCompletionService<Pair<String, String>?>(pool)
            SCAN_HOSTS.forEach { h -> results.submit(Callable { probe(h) }) }
            repeat(SCAN_HOSTS.size) {
                val found = runCatching { results.take().get() }.getOrNull()
                if (found != null) {
                    Prefs.setTarget(c, found.first, PORT, found.second)
                    return true
                }
            }
            return false
        } finally {
            pool.shutdownNow()
        }
    }

    private fun probe(host: String): Pair<String, String>? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, PORT), 1500)
            s.soTimeout = 1500
            val out = s.getOutputStream()
            out.write("{\"type\":\"discover\"}\n".toByteArray())
            out.flush()
            val line = s.getInputStream().bufferedReader().readLine() ?: return@use null
            val j = JSONObject(line)
            val token = j.optString("token")
            if (j.optBoolean("ok") && j.optString("app") == "notifytv" && token.isNotEmpty()) host to token else null
        }
    }.getOrNull()

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
