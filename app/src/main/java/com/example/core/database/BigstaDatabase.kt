package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProcessedSmsEntity::class,
        PendingUploadEntity::class,
        CachedTemplateEntity::class,
        LocalDownloadEntity::class,
        AppNotificationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BigstaDatabase : RoomDatabase() {
    abstract fun smsTransactionDao(): SmsTransactionDao
    abstract fun pendingUploadDao(): PendingUploadDao
    abstract fun cachedTemplateDao(): CachedTemplateDao
    abstract fun localDownloadDao(): LocalDownloadDao
    abstract fun appNotificationDao(): AppNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: BigstaDatabase? = null

        fun getInstance(context: Context): BigstaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BigstaDatabase::class.java,
                    "bigsta_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
