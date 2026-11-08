package com.example.jarvis.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Version 2.0 Notification Listener & Spoken Reader.
 * Captures incoming notifications (WhatsApp, Telegram, SMS, Gmail, etc.)
 * and formats them for hands-free voice readouts.
 */
class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "JarvisNotification"
        private const val MAX_HISTORY = 20

        data class NotificationItem(
            val packageName: String,
            val appLabel: String,
            val title: String,
            val text: String,
            val isMessage: Boolean,
            val timestamp: Long = System.currentTimeMillis()
        )

        private val history = ConcurrentLinkedDeque<NotificationItem>()

        @Volatile
        var isConnected: Boolean = false
            private set

        fun getRecentSummary(limit: Int = 3): String {
            if (!isConnected) {
                return "Notification access is not enabled. Please enable it in Settings under Notification Access."
            }
            if (history.isEmpty()) {
                return "You have no new notifications right now."
            }
            val items = history.toList().take(limit)
            val builder = StringBuilder("Here are your recent notifications: ")
            items.forEachIndexed { index, item ->
                if (index > 0) builder.append(". Also, ")
                builder.append("From ${item.appLabel}: ${item.title}")
                if (item.text.isNotBlank() && item.text != item.title) {
                    builder.append(" saying ${item.text}")
                }
            }
            return builder.toString()
        }

        fun getMessagesSummary(limit: Int = 3): String {
            if (!isConnected) {
                return "Notification access is not enabled. Please enable it in Settings under Notification Access."
            }
            val messageItems = history.filter { it.isMessage }.take(limit)
            if (messageItems.isEmpty()) {
                return "No unread messages found right now."
            }
            val builder = StringBuilder("Here are your recent messages: ")
            messageItems.forEachIndexed { index, item ->
                if (index > 0) builder.append(". Also, ")
                builder.append("From ${item.title} on ${item.appLabel}")
                if (item.text.isNotBlank()) {
                    builder.append(": \"${item.text}\"")
                }
            }
            return builder.toString()
        }

        fun clearHistory(): String {
            history.clear()
            return "Notification history cleared."
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(TAG, "JarvisNotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.i(TAG, "JarvisNotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Skip our own notifications to prevent feedback loops
        if (pkg == packageName) return

        val notification = sbn.notification ?: return

        // Skip ongoing persistent notifications (e.g. media players, background service status)
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pm = applicationContext.packageManager
        val appLabel = try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }

        val isMessage = isMessagingApp(pkg)

        val item = NotificationItem(
            packageName = pkg,
            appLabel = appLabel,
            title = title,
            text = text,
            isMessage = isMessage
        )

        history.addFirst(item)
        while (history.size > MAX_HISTORY) {
            history.removeLast()
        }

        Log.d(TAG, "Captured notification from $appLabel ($pkg): $title - $text")
    }

    private fun isMessagingApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("whatsapp") ||
                p.contains("messaging") ||
                p.contains("telegram") ||
                p.contains("mms") ||
                p.contains("sms") ||
                p.contains("instagram") ||
                p.contains("signal") ||
                p.contains("discord") ||
                p.contains("gmail")
    }
}
