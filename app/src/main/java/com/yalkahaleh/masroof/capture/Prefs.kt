package com.yalkahaleh.masroof.capture

import android.content.Context

object Prefs {
    private const val FILE = "capture"
    private const val CAPTURE_ALL = "capture_all"

    fun captureAll(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(CAPTURE_ALL, false)

    fun setCaptureAll(context: Context, value: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(CAPTURE_ALL, value).apply()
}
