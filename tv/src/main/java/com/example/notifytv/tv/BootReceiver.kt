package com.example.notifytv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { ContextCompat.startForegroundService(c, Intent(c, ReceiverService::class.java)) }
        }
    }
}
