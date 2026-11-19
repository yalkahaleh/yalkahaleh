package com.yalkahaleh.masroof.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {
    @Query("SELECT * FROM captured_notification ORDER BY postedAt DESC, id DESC")
    fun observeAll(): Flow<List<CapturedNotification>>

    @Query("SELECT * FROM captured_notification ORDER BY postedAt DESC, id DESC")
    suspend fun getAll(): List<CapturedNotification>

    @Query("SELECT * FROM captured_notification WHERE notificationKey = :key ORDER BY id DESC LIMIT 1")
    suspend fun latestForKey(key: String): CapturedNotification?

    @Insert
    suspend fun insert(item: CapturedNotification)

    @Query("DELETE FROM captured_notification WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM captured_notification")
    suspend fun clear()
}

@Database(entities = [CapturedNotification::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capturedNotifications(): CapturedNotificationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "masroof.db"
            ).build().also { instance = it }
        }
    }
}
