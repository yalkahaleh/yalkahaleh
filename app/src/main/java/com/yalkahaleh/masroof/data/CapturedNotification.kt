package com.yalkahaleh.masroof.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One raw notification, stored exactly as the phone showed it. */
@Entity(tableName = "captured_notification")
data class CapturedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val category: String,
    val postedAt: Long,
)
