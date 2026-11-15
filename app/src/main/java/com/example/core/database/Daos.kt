package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTransactionDao {
    @Query("SELECT * FROM processed_sms_transactions WHERE userId = :userId ORDER BY createdAtMs DESC")
    fun observeByUser(userId: String): Flow<List<ProcessedSmsEntity>>

    @Query("SELECT * FROM processed_sms_transactions ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<ProcessedSmsEntity>>

    @Query("SELECT * FROM processed_sms_transactions WHERE transactionRef = :ref LIMIT 1")
    suspend fun findByRef(ref: String): ProcessedSmsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ProcessedSmsEntity): Long

    @Query("UPDATE processed_sms_transactions SET status = :status, retryCount = :retryCount, lastError = :lastError, updatedAtMs = :updatedAt WHERE transactionRef = :ref")
    suspend fun updateStatus(ref: String, status: String, retryCount: Int, lastError: String?, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface PendingUploadDao {
    @Query("SELECT * FROM pending_upload_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtMs ASC")
    suspend fun getRetryableUploads(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_upload_queue ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<PendingUploadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingUploadEntity)

    @Query("UPDATE pending_upload_queue SET status = :status, retryCount = :retryCount, lastError = :lastError WHERE idempotencyKey = :key")
    suspend fun updateStatus(key: String, status: String, retryCount: Int, lastError: String?)

    @Query("DELETE FROM pending_upload_queue WHERE idempotencyKey = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface CachedTemplateDao {
    @Query("SELECT * FROM cached_templates ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<CachedTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedTemplateEntity>)

    @Query("UPDATE cached_templates SET localCachedPath = :localPath WHERE id = :id")
    suspend fun updateLocalPath(id: String, localPath: String)

    @Query("DELETE FROM cached_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface LocalDownloadDao {
    @Query("SELECT * FROM local_downloads WHERE userId = :userId ORDER BY createdAtMs DESC")
    fun observeByUser(userId: String): Flow<List<LocalDownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalDownloadEntity)

    @Query("DELETE FROM local_downloads WHERE id = :id AND userId = :userId")
    suspend fun deleteOwned(id: String, userId: String)
}

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM in_app_notifications ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM in_app_notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(notification: AppNotificationEntity): Long

    @Query("UPDATE in_app_notifications SET isRead = 1 WHERE eventKey = :eventKey")
    suspend fun markRead(eventKey: String)

    @Query("UPDATE in_app_notifications SET isRead = 1")
    suspend fun markAllRead()
}
