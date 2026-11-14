package com.example.data.repositories

import com.example.core.network.ApiResult
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.data.models.PaymentMethodInfoDto
import com.example.data.models.PaymentRequestCreateDto
import com.example.data.models.PaymentRequestDto
import com.example.data.models.SystemSettingDto
import com.example.data.models.TokenPackageDto
import com.example.data.models.WeeklyOfferDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PaymentRepository(
    private val restClient: SupabaseRestClient,
    private val sessionManager: SessionManager
) {
    suspend fun fetchTokenPackages(): ApiResult<List<TokenPackageDto>> {
        val session = sessionManager.currentSession()
        val res = restClient.getRest(
            table = "token_packages",
            queryParams = mapOf(
                "is_active" to "eq.true",
                "order" to "sort_order.asc"
            ),
            accessToken = session.accessToken.takeIf { it.isNotBlank() }
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val items = restClient.json.decodeFromString<List<TokenPackageDto>>(res.data)
                    ApiResult.Success(items.filter { it.visibility != "hidden" })
                } catch (e: Exception) {
                    ApiResult.Error("Failed to parse token packages: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchWeeklyOffers(): ApiResult<List<WeeklyOfferDto>> {
        val session = sessionManager.currentSession()
        val res = restClient.getRest(
            table = "weekly_offers",
            queryParams = mapOf("is_active" to "eq.true"),
            accessToken = session.accessToken.takeIf { it.isNotBlank() }
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    ApiResult.Success(restClient.json.decodeFromString<List<WeeklyOfferDto>>(res.data))
                } catch (e: Exception) {
                    ApiResult.Error("Failed to parse weekly offers: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchPaymentMethodsConfig(): ApiResult<List<PaymentMethodInfoDto>> {
        val session = sessionManager.currentSession()
        val res = restClient.getRest(
            table = "system_settings",
            queryParams = mapOf("id" to "eq.payment_methods"),
            accessToken = session.accessToken.takeIf { it.isNotBlank() }
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val settings = restClient.json.decodeFromString<List<SystemSettingDto>>(res.data)
                    val valueEl = settings.firstOrNull()?.value
                    val methods = when (valueEl) {
                        is JsonArray -> valueEl.mapNotNull { parseMethodObject(it.jsonObject) }
                        is JsonObject -> valueEl["methods"]?.jsonArray?.mapNotNull { parseMethodObject(it.jsonObject) }
                            ?: listOfNotNull(parseMethodObject(valueEl))
                        else -> emptyList()
                    }
                    ApiResult.Success(methods)
                } catch (e: Exception) {
                    ApiResult.Error("Failed to parse payment methods: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun parseMethodObject(obj: JsonObject): PaymentMethodInfoDto? {
        val provider = obj["provider"]?.jsonPrimitive?.contentOrNull
            ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val lipa = obj["lipa_namba"]?.jsonPrimitive?.contentOrNull
            ?: obj["lipaNamba"]?.jsonPrimitive?.contentOrNull ?: ""
        val account = obj["account_name"]?.jsonPrimitive?.contentOrNull
            ?: obj["accountName"]?.jsonPrimitive?.contentOrNull ?: ""
        val ussd = obj["ussd_code"]?.jsonPrimitive?.contentOrNull
            ?: obj["ussdCode"]?.jsonPrimitive?.contentOrNull ?: ""
        return PaymentMethodInfoDto(provider = provider, lipaNamba = lipa, accountName = account, ussdCode = ussd)
    }

    /**
     * Strict contract from AGENTS.md & BIGSTA_AUDIT_REPORT.md:
     * Must await payment_requests remote write before reporting success.
     * Never auto-approves or locally credits tokens.
     */
    suspend fun submitPaymentRequest(
        packageId: String?,
        packageName: String,
        amount: Double,
        tokens: Int,
        paymentMethod: String,
        transactionRef: String,
        senderPhone: String?,
        smsText: String? = null
    ): ApiResult<PaymentRequestDto> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return ApiResult.Error("Please sign in before submitting a payment request.")
        }
        val cleanRef = transactionRef.trim()
        if (cleanRef.length < 4 || amount <= 0.0 || tokens <= 0) {
            return ApiResult.Error("Valid transaction reference, amount, and token package are required.")
        }

        val dto = PaymentRequestCreateDto(
            userId = session.userId,
            packageId = packageId,
            packageName = packageName,
            amount = amount,
            tokens = tokens,
            paymentMethod = paymentMethod,
            transactionRef = cleanRef,
            senderPhone = senderPhone?.trim()?.takeIf { it.isNotBlank() },
            smsText = smsText?.trim()?.takeIf { it.isNotBlank() },
            status = "pending"
        )
        val payload = restClient.json.encodeToString(dto)
        val res = restClient.postRest(
            table = "payment_requests",
            bodyJson = payload,
            accessToken = session.accessToken
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val created = restClient.json.decodeFromString<List<PaymentRequestDto>>(res.data).firstOrNull()
                    if (created != null) {
                        ApiResult.Success(created)
                    } else {
                        ApiResult.Error("Payment submission was not persisted by backend.")
                    }
                } catch (e: Exception) {
                    ApiResult.Error("Unexpected response from payment_requests: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchUserPaymentRequests(): ApiResult<List<PaymentRequestDto>> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) return ApiResult.Error("Sign in required.")
        val res = restClient.getRest(
            table = "payment_requests",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "order" to "created_at.desc"
            ),
            accessToken = session.accessToken
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    ApiResult.Success(restClient.json.decodeFromString<List<PaymentRequestDto>>(res.data))
                } catch (e: Exception) {
                    ApiResult.Error("Could not load payment history: ${e.localizedMessage}")
                }
            }
        }
    }
}
