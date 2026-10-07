package com.example.notifytv.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class ReceiverService : Service() {
    companion object { const val PORT = 47321 }

    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayManager

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayManager(this)
        goForeground()
        thread(name = "notifytv-server") { runServer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { server?.close() }
        super.onDestroy()
    }

    private fun runServer() {
        try {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(PORT))
            server = ss
            while (!ss.isClosed) {
                val client = try { ss.accept() } catch (e: Exception) { break }
                thread { handle(client) }
            }
        } catch (e: Exception) {
            Log.e("NotifyTV", "Server error", e)
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.use { s ->
                s.soTimeout = 10_000
                val reader = s.getInputStream().bufferedReader()
                val out = s.getOutputStream()
                while (true) {
                    val line = reader.readLine() ?: break
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (json.optString("type") == "discover") {
                        // Lets phones on the same WiFi find this TV and pair without scanning the QR code.
                        val reply = JSONObject().put("ok", true).put("app", "notifytv").put("token", Token.get(this))
                        out.write((reply.toString() + "\n").toByteArray())
                        out.flush()
                        continue
                    }
                    if (json.optString("token") != Token.get(this)) {
                        out.write("{\"ok\":false,\"error\":\"bad token\"}\n".toByteArray())
                        out.flush()
                        break
                    }
                    if (json.optString("type") == "notification") {
                        main.post { runCatching { overlay.show(json) } }
                    }
                    out.write("{\"ok\":true}\n".toByteArray())
                    out.flush()
                }
            }
        } catch (e: Exception) {
            Log.w("NotifyTV", "Client error", e)
        }
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("svc", "Receiver", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, "svc")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = builder
            .setContentTitle("NotifyTV is listening")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }
}
