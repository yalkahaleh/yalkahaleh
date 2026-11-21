package com.yalkahaleh.masroof.capture

import android.app.Notification
import android.os.Bundle
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
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val captureAll = Prefs.captureAll(this)
        // Ongoing notifications and group summaries ("3 new messages") are normally
        // noise, but in capture-all mode nothing is skipped, so no payment
        // notification can be dropped by this filter.
        if (!captureAll && (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0)) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n").orEmpty()
        val body = bigText.ifBlank { lines }

        val ticker = n.tickerText?.toString().orEmpty()
        val raw = rawDump(n, sbn)

        // Some apps (banks especially) draw a custom notification and leave the
        // standard title/text empty, so every readable field is searched.
        val content = listOf(title, text, body, subText, ticker, raw).joinToString("\n")
        if (!CaptureFilter.shouldCapture(sbn.packageName, content, captureAll)) return

        val standardEmpty = title.isBlank() && text.isBlank() && body.isBlank()
        val details = buildString {
            if (body != text) append(body)
            // Keep the raw fields whenever they are the only evidence, or when
            // the user asked to capture everything.
            if (captureAll || standardEmpty) {
                if (isNotEmpty()) append("\n")
                append("--- raw ---\n").append(raw)
            }
        }

        val item = CapturedNotification(
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            title = title,
            text = text,
            bigText = details,
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

    /** Every text-like value the notification carries, for diagnosing odd formats. */
    private fun rawDump(n: Notification, sbn: StatusBarNotification): String = buildString {
        append("flags=0x").append(Integer.toHexString(n.flags))
        append(" ongoing=").append(sbn.isOngoing)
        append(" customView=").append(n.contentView != null || n.bigContentView != null)
        append(" user=").append(sbn.user.hashCode()).append('\n')
        n.tickerText?.let { append("ticker: ").append(it).append('\n') }
        val extras = n.extras
        for (key in extras.keySet().sorted()) {
            @Suppress("DEPRECATION")
            when (val v = runCatching { extras.get(key) }.getOrNull()) {
                is CharSequence -> if (v.isNotBlank()) append(key).append(": ").append(v).append('\n')
                is Array<*> -> v.forEach { e ->
                    when (e) {
                        is CharSequence -> append(key).append("[]: ").append(e).append('\n')
                        is Bundle -> e.getCharSequence("text")?.let { t ->
                            append(key).append("[].text: ").append(t).append('\n')
                        }
                    }
                }
            }
        }
    }.trim()

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
