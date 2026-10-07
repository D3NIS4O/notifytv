package com.example.notifytv.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : Activity() {
    private lateinit var qr: ImageView
    private lateinit var info: TextView
    private var askedOverlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qr = ImageView(this)
        info = TextView(this).apply {
            textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 32, 0, 0)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(qr, LinearLayout.LayoutParams(800, 800))
            addView(info)
        })
        ContextCompat.startForegroundService(this, Intent(this, ReceiverService::class.java))
    }

    override fun onResume() {
        super.onResume()
        val ip = localIp()
        if (ip == null) {
            info.text = "Not connected to WiFi"
            return
        }
        val url = "notifytv://$ip:${ReceiverService.PORT}?t=${Token.get(this)}"
        qr.setImageBitmap(qrBitmap(url, 800))

        info.text = if (Settings.canDrawOverlays(this)) {
            "Scan with the NotifyTV phone app\n$ip"
        } else {
            if (!askedOverlay) { askedOverlay = true; requestOverlay() }
            "Overlay permission missing! If no settings screen opened, run from a computer:\n" +
                "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow\n\n$ip"
        }
    }

    private fun requestOverlay() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun qrBitmap(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    }
}
