package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "processed_sms_transactions",
    indices = [Index(value = ["transactionRef"], unique = true)]
)
data class ProcessedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionRef: String,
    val provider: String,
    val amount: Double,
    val currency: String,
    val transactionDateTime: String,
    val transactionType: String,
    val senderAccount: String?,
    val userId: String,
    val status: String, // QUEUED, UPLOADED, FAILED, DUPLICATE
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_upload_queue")
data class PendingUploadEntity(
    @PrimaryKey val idempotencyKey: String,
    val payloadType: String, // PAYMENT_SMS, PAYMENT_REQUEST, NIDA_REQUEST
    val payloadJson: String,
    val userId: String,
    val retryCount: Int = 0,
    val status: String = "PENDING", // PENDING, SYNCING, SYNCED, FAILED
    val lastError: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_templates")
data class CachedTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val documentCategoryId: String,
    val templateCardId: String,
    val side: String, // front or back
    val backgroundUrl: String?,
    val localCachedPath: String?,
    val isActive: Boolean,
    val layersJson: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_downloads")
data class LocalDownloadEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val mimeType: String,
    val localFilePath: String,
    val fileSizeBytes: Long,
    val pageCount: Int?,
    val userId: String,
    val sourceType: String, // ID_CARD, PDF_EXPORT, IMAGE_EXPORT
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "in_app_notifications")
data class AppNotificationEntity(
    @PrimaryKey val eventKey: String, // Prevents duplicate notifications
    val title: String,
    val body: String,
    val category: String, // PAYMENT, RECHARGE, BALANCE, ALERT, ADMIN, FAILURE
    val targetRoute: String,
    val isRead: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)
