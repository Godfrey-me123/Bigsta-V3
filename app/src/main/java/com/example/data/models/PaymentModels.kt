package com.example.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenPackageDto(
    val id: String = "",
    val name: String = "",
    val usages: Int = 0,
    val price: Double = 0.0,
    val description: String? = null,
    val visibility: String = "public",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val bonus: Int = 0,
    val promotion: String? = null
)

@Serializable
data class WeeklyOfferDto(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val tokens: Int = 0,
    val price: Double = 0.0,
    val services: List<String> = emptyList(),
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class PaymentRequestCreateDto(
    @SerialName("user_id") val userId: String,
    @SerialName("package_id") val packageId: String? = null,
    @SerialName("package_name") val packageName: String,
    val amount: Double,
    val tokens: Int,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("transaction_ref") val transactionRef: String,
    @SerialName("sender_phone") val senderPhone: String? = null,
    @SerialName("sms_text") val smsText: String? = null,
    val status: String = "pending"
)

@Serializable
data class PaymentRequestDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("package_id") val packageId: String? = null,
    @SerialName("package_name") val packageName: String? = null,
    val amount: Double = 0.0,
    val tokens: Int = 0,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("transaction_ref") val transactionRef: String? = null,
    @SerialName("sender_phone") val senderPhone: String? = null,
    @SerialName("sms_text") val smsText: String? = null,
    val status: String = "pending",
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SmsWebhookPayloadDto(
    @SerialName("transaction_ref") val transactionRef: String,
    val amount: Double,
    val currency: String = "TZS",
    val provider: String,
    @SerialName("transaction_datetime") val transactionDateTime: String,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("sender_phone") val senderPhone: String? = null,
    @SerialName("user_id") val userId: String
)

@Serializable
data class PaymentMethodInfoDto(
    val provider: String,
    @SerialName("lipa_namba") val lipaNamba: String,
    @SerialName("account_name") val accountName: String,
    @SerialName("ussd_code") val ussdCode: String,
    val instructions: List<String> = emptyList()
)
