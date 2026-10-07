package com.example.notifytv.phone

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap

class NotifListener : NotificationListenerService() {
    private var lastKey = ""
    private var lastTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.forwarding(this)) return
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

        TvSender.sendNotification(this, sbn.packageName, title, text, photo = senderPhoto(n))
    }

    /** Profile picture of whoever sent the notification (e.g. the Instagram user), if the app provides one. */
    @Suppress("DEPRECATION")
    private fun senderPhoto(n: Notification): Bitmap? = runCatching {
        // Chat apps: picture of the person who sent the latest message.
        val fromChat = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            ?.messages?.lastOrNull { it.person?.icon != null }
            ?.person?.icon?.loadDrawable(this)
        // Most other apps (Instagram included) put the sender's picture in the large icon.
        val drawable: Drawable? = fromChat ?: n.getLargeIcon()?.loadDrawable(this)
        val bmp = drawable?.toBitmap(192, 192) ?: (n.extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
        bmp?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }.getOrNull()
}
