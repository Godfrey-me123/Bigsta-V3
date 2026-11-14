package com.example.data.repositories

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.database.PendingUploadDao
import com.example.core.database.PendingUploadEntity
import com.example.core.database.ProcessedSmsEntity
import com.example.core.database.SmsTransactionDao
import com.example.core.network.ApiResult
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.core.utilities.AppPreferences
import com.example.data.models.PaymentRequestCreateDto
import com.example.data.models.SmsWebhookPayloadDto
import com.example.domain.sms.SmsParseOutcome
import com.example.domain.sms.TransactionSmsParser
import com.example.features.notifications.BigstaNotificationManager
import com.example.features.notifications.NotificationCategory
import com.example.features.sms.SmsSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

sealed class SmsSubmissionResult {
    data class SubmittedImmediately(val transactionRef: String, val provider: String, val amount: Double) : SmsSubmissionResult()
    data class QueuedOffline(val transactionRef: String, val reason: String) : SmsSubmissionResult()
    data class DuplicateBlocked(val transactionRef: String) : SmsSubmissionResult()
    data class Rejected(val reason: String, val isSensitiveAuthCode: Boolean) : SmsSubmissionResult()
}

class SmsTransactionRepository(
    private val context: Context,
    private val smsDao: SmsTransactionDao,
    private val pendingUploadDao: PendingUploadDao,
    private val restClient: SupabaseRestClient,
    private val sessionManager: SessionManager,
    private val appPreferences: AppPreferences,
    private val notificationManager: BigstaNotificationManager
) {
    fun observeTransactions(userId: String): Flow<List<ProcessedSmsEntity>> =
        if (userId.isBlank()) smsDao.observeAll() else smsDao.observeByUser(userId)

    fun observePendingQueue(): Flow<List<PendingUploadEntity>> = pendingUploadDao.observeAll()

    suspend fun processIncomingTransactionText(
        rawMessage: String,
        packageName: String = "SMS Transaction Intake",
        tokens: Int = 0
    ): SmsSubmissionResult {
        val settings = appPreferences.current()
        if (!settings.smsConsentAccepted || !settings.smsMonitoringEnabled) {
            return SmsSubmissionResult.Rejected(
                reason = "Transaction SMS monitoring is disabled. Enable user-consented SMS processing first.",
                isSensitiveAuthCode = false
            )
        }

        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return SmsSubmissionResult.Rejected(
                reason = "Sign in to associate transaction confirmations with your BIGsta account.",
                isSensitiveAuthCode = false
            )
        }

        val outcome = TransactionSmsParser.parse(rawMessage, settings.enabledSmsProviders)
        if (outcome is SmsParseOutcome.Rejected) {
            return SmsSubmissionResult.Rejected(outcome.reason, outcome.isSensitiveAuthCode)
        }

        val parsed = outcome as SmsParseOutcome.ValidTransaction

        // Duplicate prevention via unique transactionRef index in Room
        val existing = smsDao.findByRef(parsed.transactionRef)
        if (existing != null) {
            return SmsSubmissionResult.DuplicateBlocked(parsed.transactionRef)
        }

        val entity = ProcessedSmsEntity(
            transactionRef = parsed.transactionRef,
            provider = parsed.provider,
            amount = parsed.amount,
            currency = parsed.currency,
            transactionDateTime = parsed.transactionDateTime,
            transactionType = parsed.transactionType,
            senderAccount = parsed.senderAccount,
            userId = session.userId,
            status = "QUEUED"
        )
        val insertedId = smsDao.insertIgnore(entity)
        if (insertedId == -1L) {
            return SmsSubmissionResult.DuplicateBlocked(parsed.transactionRef)
        }

        val attempt = tryUploadToBackend(parsed, session.userId, session.accessToken, packageName, tokens)
        return when (attempt) {
            is ApiResult.Success -> {
                smsDao.updateStatus(parsed.transactionRef, "UPLOADED", 0, null)
                notificationManager.notifyEvent(
                    eventKey = "sms_uploaded_${parsed.transactionRef}",
                    title = "Transaction Submitted for Verification",
                    body = "${parsed.provider} ref ${parsed.transactionRef} (${parsed.currency} ${parsed.amount.toInt()}) is pending server approval.",
                    category = NotificationCategory.PAYMENT,
                    targetRoute = "billing"
                )
                SmsSubmissionResult.SubmittedImmediately(parsed.transactionRef, parsed.provider, parsed.amount)
            }
            is ApiResult.Error -> {
                // Queue in Room + schedule WorkManager retry
                val queuePayload = restClient.json.encodeToString(
                    SmsWebhookPayloadDto(
                        transactionRef = parsed.transactionRef,
                        amount = parsed.amount,
                        currency = parsed.currency,
                        provider = parsed.provider,
                        transactionDateTime = parsed.transactionDateTime,
                        transactionType = parsed.transactionType,
                        senderPhone = parsed.senderAccount,
                        userId = session.userId
                    )
                )
                pendingUploadDao.upsert(
                    PendingUploadEntity(
                        idempotencyKey = "sms_${parsed.transactionRef}",
                        payloadType = "PAYMENT_SMS",
                        payloadJson = queuePayload,
                        userId = session.userId,
                        status = "PENDING",
                        lastError = attempt.message
                    )
                )
                smsDao.updateStatus(parsed.transactionRef, "QUEUED", 1, attempt.message)
                enqueueSyncWorker()
                SmsSubmissionResult.QueuedOffline(parsed.transactionRef, attempt.message)
            }
        }
    }

    suspend fun tryUploadToBackend(
        parsed: SmsParseOutcome.ValidTransaction,
        userId: String,
        accessToken: String,
        packageName: String = "SMS Transaction Verification",
        tokens: Int = 0
    ): ApiResult<String> {
        val env = restClient.configProvider()
        // If custom API route /api/public/payment-sms is configured, post approved fields there;
        // otherwise write directly to Supabase payment_requests with status="pending".
        if (env.hasApiBackend) {
            val webhookBody = restClient.json.encodeToString(
                SmsWebhookPayloadDto(
                    transactionRef = parsed.transactionRef,
                    amount = parsed.amount,
                    currency = parsed.currency,
                    provider = parsed.provider,
                    transactionDateTime = parsed.transactionDateTime,
                    transactionType = parsed.transactionType,
                    senderPhone = parsed.senderAccount,
                    userId = userId
                )
            )
            val apiRes = restClient.callApiEndpoint("/api/public/payment-sms", "POST", webhookBody, accessToken)
            if (apiRes is ApiResult.Success) return apiRes
        }

        val requestDto = PaymentRequestCreateDto(
            userId = userId,
            packageId = null,
            packageName = packageName,
            amount = parsed.amount,
            tokens = if (tokens > 0) tokens else 0,
            paymentMethod = parsed.provider,
            transactionRef = parsed.transactionRef,
            senderPhone = parsed.senderAccount,
            smsText = "Ref:${parsed.transactionRef}|${parsed.currency} ${parsed.amount}|${parsed.transactionDateTime}|${parsed.transactionType}",
            status = "pending"
        )
        return restClient.postRest(
            table = "payment_requests",
            bodyJson = restClient.json.encodeToString(requestDto),
            accessToken = accessToken
        )
    }

    fun enqueueSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bigsta_sms_sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
