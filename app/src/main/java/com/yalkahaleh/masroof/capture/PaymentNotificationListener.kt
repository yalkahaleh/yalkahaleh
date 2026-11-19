package com.yalkahaleh.masroof.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yalkahaleh.masroof.data.AppDatabase
import com.yalkahaleh.masroof.data.CapturedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification ?: return
        // Group summaries ("3 new messages") repeat what the child notifications say.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n").orEmpty()
        val body = bigText.ifBlank { lines }

        val content = listOf(title, text, body, subText).joinToString("\n")
        if (content.isBlank()) return
        if (!CaptureFilter.shouldCapture(sbn.packageName, content, Prefs.captureAll(this))) return

        val item = CapturedNotification(
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            title = title,
            text = text,
            bigText = if (body == text) "" else body,
            subText = subText,
            category = n.category.orEmpty(),
            postedAt = sbn.postTime,
        )
        scope.launch {
            val dao = AppDatabase.get(applicationContext).capturedNotifications()
            writeLock.withLock {
                // Apps often re-post the same notification; keep one copy.
                val last = dao.latestForKey(item.notificationKey)
                val repeated = last != null && last.title == item.title &&
                    last.text == item.text && last.bigText == item.bigText
                if (!repeated) dao.insert(item)
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
