package com.yalkahaleh.masroof.capture

/**
 * Decides which notifications are worth logging. By default only wallet apps,
 * SMS apps, and anything that reads like a money message are kept, so private
 * chats do not end up in the log.
 */
object CaptureFilter {
    val WALLET_PACKAGES = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.spayfw",
        "com.samsung.android.samsungpay.gear",
        "com.google.android.apps.walletnfcrel",
    )

    val SMS_PACKAGES = setOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
    )

    private val MONEY = Regex(
        "\\b(SAR|AED|USD|EUR|GBP|KWD|QAR|BHD|OMR|EGP|JOD|TRY|SYP|SR)\\b" +
            "|\\b(purchase|debited|debit|spent|paid|payment|transaction|withdraw\\w*|POS)\\b" +
            "|ريال|درهم|دينار|ليرة|جنيه|ر\\.س|د\\.إ|شراء|مبلغ|خصم|سحب|دفع|عملية",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeMoney(content: String): Boolean = MONEY.containsMatchIn(content)

    fun shouldCapture(packageName: String, content: String, captureAll: Boolean): Boolean =
        captureAll ||
            packageName in WALLET_PACKAGES ||
            packageName in SMS_PACKAGES ||
            looksLikeMoney(content)
}
