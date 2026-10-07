package com.example.notifytv.phone

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {
    private var lastKey = ""
    private var lastTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.isOngoing) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.packageName !in Prefs.enabledApps(this)) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val key = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastTime < 3000) return
        lastKey = key
        lastTime = now

        TvSender.sendNotification(this, sbn.packageName, title, text)
    }
}
