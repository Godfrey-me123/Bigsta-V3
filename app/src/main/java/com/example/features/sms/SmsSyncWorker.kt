package com.example.features.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BigstaApplication
import com.example.core.network.ApiResult
import com.example.data.models.SmsWebhookPayloadDto
import com.example.domain.sms.SmsParseOutcome
import com.example.features.notifications.NotificationCategory

class SmsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? BigstaApplication)?.container
            ?: return Result.failure()

        val session = container.sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return Result.retry()
        }

        val db = container.database
        val pendingItems = db.pendingUploadDao().getRetryableUploads()
        if (pendingItems.isEmpty()) return Result.success()

        var anyTransientFailure = false

        for (item in pendingItems) {
            if (item.payloadType != "PAYMENT_SMS") continue
            try {
                val payload = container.restClient.json
                    .decodeFromString<SmsWebhookPayloadDto>(item.payloadJson)

                val parsed = SmsParseOutcome.ValidTransaction(
                    transactionRef = payload.transactionRef,
                    amount = payload.amount,
                    currency = payload.currency,
                    provider = payload.provider,
                    transactionDateTime = payload.transactionDateTime,
                    transactionType = payload.transactionType,
                    senderAccount = payload.senderPhone
                )

                val res = container.smsTransactionRepository.tryUploadToBackend(
                    parsed = parsed,
                    userId = session.userId,
                    accessToken = session.accessToken
                )
                when (res) {
                    is ApiResult.Success -> {
                        db.pendingUploadDao().deleteByKey(item.idempotencyKey)
                        db.smsTransactionDao().updateStatus(
                            ref = payload.transactionRef,
                            status = "UPLOADED",
                            retryCount = item.retryCount,
                            lastError = null
                        )
                        container.notificationManager.notifyEvent(
                            eventKey = "sms_synced_${payload.transactionRef}",
                            title = "Queued Transaction Synced",
                            body = "${payload.provider} ref ${payload.transactionRef} uploaded for verification.",
                            category = NotificationCategory.PAYMENT,
                            targetRoute = "billing"
                        )
                    }
                    is ApiResult.Error -> {
                        val nextRetry = item.retryCount + 1
                        val status = if (nextRetry >= 5 && !res.isNetworkError) "FAILED" else "PENDING"
                        db.pendingUploadDao().updateStatus(
                            key = item.idempotencyKey,
                            status = status,
                            retryCount = nextRetry,
                            lastError = res.message
                        )
                        db.smsTransactionDao().updateStatus(
                            ref = payload.transactionRef,
                            status = if (status == "FAILED") "FAILED" else "QUEUED",
                            retryCount = nextRetry,
                            lastError = res.message
                        )
                        if (status == "PENDING") {
                            anyTransientFailure = true
                        } else {
                            container.notificationManager.notifyEvent(
                                eventKey = "sms_failed_${payload.transactionRef}",
                                title = "Transaction Upload Failed",
                                body = "Ref ${payload.transactionRef}: ${res.message}",
                                category = NotificationCategory.FAILURE,
                                targetRoute = "sms"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                db.pendingUploadDao().updateStatus(
                    key = item.idempotencyKey,
                    status = "FAILED",
                    retryCount = item.retryCount + 1,
                    lastError = e.localizedMessage ?: "Malformed queued item"
                )
            }
        }

        return if (anyTransientFailure) Result.retry() else Result.success()
    }
}
