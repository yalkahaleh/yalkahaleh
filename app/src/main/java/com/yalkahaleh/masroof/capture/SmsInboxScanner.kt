package com.yalkahaleh.masroof.capture

import android.content.Context
import android.provider.Telephony
import com.yalkahaleh.masroof.data.AppDatabase
import com.yalkahaleh.masroof.data.CapturedNotification

/**
 * Reads SMS already stored on the phone. Unlike the notification listener this
 * also finds messages whose notification was never shown or was missed.
 */
object SmsInboxScanner {
    const val SOURCE = "sms-inbox"
    private const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000

    data class Result(val read: Int, val added: Int)

    suspend fun scan(context: Context, includeAll: Boolean): Result {
        val dao = AppDatabase.get(context).capturedNotifications()
        val since = System.currentTimeMillis() - WINDOW_MS
        var read = 0
        var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                read++
                val body = c.getString(2).orEmpty()
                if (!includeAll && !CaptureFilter.looksLikeMoney(body)) continue
                val key = "sms:${c.getLong(0)}"
                if (dao.latestForKey(key) != null) continue
                dao.insert(
                    CapturedNotification(
                        notificationKey = key,
                        packageName = SOURCE,
                        appLabel = "SMS inbox",
                        title = c.getString(1).orEmpty(),
                        text = body,
                        bigText = "",
                        subText = "",
                        category = "",
                        postedAt = c.getLong(3),
                    )
                )
                added++
            }
        }
        return Result(read, added)
    }
}
