package com.example.data.repositories

import com.example.core.network.ApiResult
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.data.models.NidaRequestDto
import com.example.data.models.PaymentRequestDto
import com.example.data.models.ProfileDto
import com.example.data.models.TemplateDto

class AdminRepository(
    private val restClient: SupabaseRestClient,
    private val sessionManager: SessionManager
) {
    private suspend fun requireAdminSession(): ApiResult<String> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return ApiResult.Error("Authentication required.", statusCode = 401)
        }
        if (!session.isAdmin) {
            return ApiResult.Error("Forbidden: verified administrator role in user_roles is required.", statusCode = 403)
        }
        return ApiResult.Success(session.accessToken)
    }

    suspend fun fetchAllPaymentRequests(): ApiResult<List<PaymentRequestDto>> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val res = restClient.getRest(
            table = "payment_requests",
            queryParams = mapOf("order" to "created_at.desc"),
            accessToken = token
        )
        return decodeList(res)
    }

    suspend fun updatePaymentRequestStatus(
        requestId: String,
        newStatus: String,
        adminNote: String? = null
    ): ApiResult<PaymentRequestDto> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val noteField = if (!adminNote.isNullOrBlank()) ""","admin_note":"${adminNote.replace("\"", "'")}"""" else ""
        val body = """{"status":"$newStatus"$noteField}"""
        val res = restClient.patchRest(
            table = "payment_requests",
            filterQuery = "id=eq.$requestId",
            bodyJson = body,
            accessToken = token
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val updated = restClient.json.decodeFromString<List<PaymentRequestDto>>(res.data).firstOrNull()
                    if (updated != null) ApiResult.Success(updated)
                    else ApiResult.Error("Payment request not updated.")
                } catch (e: Exception) {
                    ApiResult.Error("Failed to parse updated payment request: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchAllNidaRequests(): ApiResult<List<NidaRequestDto>> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val res = restClient.getRest(
            table = "nida_requests",
            queryParams = mapOf("order" to "created_at.desc"),
            accessToken = token
        )
        return decodeList(res)
    }

    suspend fun updateNidaRequestStatus(
        requestId: String,
        newStatus: String,
        adminNotes: String? = null
    ): ApiResult<NidaRequestDto> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val noteField = if (!adminNotes.isNullOrBlank()) ""","admin_notes":"${adminNotes.replace("\"", "'")}"""" else ""
        val body = """{"status":"$newStatus"$noteField}"""
        val res = restClient.patchRest(
            table = "nida_requests",
            filterQuery = "id=eq.$requestId",
            bodyJson = body,
            accessToken = token
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val updated = restClient.json.decodeFromString<List<NidaRequestDto>>(res.data).firstOrNull()
                    if (updated != null) ApiResult.Success(updated)
                    else ApiResult.Error("Service request not updated.")
                } catch (e: Exception) {
                    ApiResult.Error("Failed to parse updated service request: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchAllProfiles(): ApiResult<List<ProfileDto>> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val res = restClient.getRest(
            table = "profiles",
            queryParams = mapOf("order" to "created_at.desc"),
            accessToken = token
        )
        return decodeList(res)
    }

    suspend fun fetchAllTemplates(): ApiResult<List<TemplateDto>> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val res = restClient.getRest(
            table = "templates",
            queryParams = mapOf("order" to "updated_at.desc"),
            accessToken = token
        )
        return decodeList(res)
    }

    suspend fun toggleTemplateActiveSide(template: TemplateDto, active: Boolean): ApiResult<TemplateDto> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val res = restClient.patchRest(
            table = "templates",
            filterQuery = "id=eq.${template.id}",
            bodyJson = """{"is_active":$active}""",
            accessToken = token
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val updated = restClient.json.decodeFromString<List<TemplateDto>>(res.data).firstOrNull()
                    if (updated != null) ApiResult.Success(updated)
                    else ApiResult.Error("Template update did not return a row.")
                } catch (e: Exception) {
                    ApiResult.Error("Template decode failed: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun deleteTemplate(templateId: String): ApiResult<Unit> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val res = restClient.deleteRest(
            table = "templates",
            filterQuery = "id=eq.$templateId",
            accessToken = token
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    suspend fun reassignTemplateCategory(templateId: String, newCategory: String): ApiResult<TemplateDto> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val body = """{
            "document_category_id": "$newCategory",
            "service_type": "$newCategory",
            "updated_at": "${java.time.Instant.now()}"
        }"""
        val res = restClient.patchRest(
            table = "templates",
            filterQuery = "id=eq.$templateId",
            bodyJson = body,
            accessToken = token
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val updated = restClient.json.decodeFromString<List<TemplateDto>>(res.data).firstOrNull()
                    if (updated != null) ApiResult.Success(updated)
                    else ApiResult.Error("Template category update returned no row.")
                } catch (e: Exception) {
                    ApiResult.Error("Template decode failed: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun saveOrUpdateTemplate(
        id: String? = null,
        name: String,
        category: String,
        side: String,
        backgroundUrl: String? = null,
        isActive: Boolean = true,
        isUniversal: Boolean = false
    ): ApiResult<TemplateDto> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        val safeName = name.trim().ifBlank { "$category Template" }
        val templateId = id?.takeIf { it.isNotBlank() } ?: "tpl_${System.currentTimeMillis()}"
        val body = """{
            "id": "$templateId",
            "name": "${safeName.replace(""", "\"")}",
            "template_name": "${safeName.replace(""", "\"")}",
            "document_category_id": "$category",
            "service_type": "$category",
            "side": "${side.lowercase()}",
            "is_active": $isActive,
            "is_universal": $isUniversal,
            ${if (!backgroundUrl.isNullOrBlank()) """"background_url": "${backgroundUrl.replace(""", "\"")}",""" else ""}
            "updated_at": "${java.time.Instant.now()}"
        }"""

        val res = restClient.postRest(
            table = "templates",
            bodyJson = body,
            accessToken = token,
            upsertOnConflict = "id"
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val saved = restClient.json.decodeFromString<List<TemplateDto>>(res.data).firstOrNull()
                    if (saved != null) ApiResult.Success(saved)
                    else ApiResult.Error("Template save returned no row.")
                } catch (e: Exception) {
                    ApiResult.Error("Failed to decode saved template: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchSmsMonitorLogs(): ApiResult<String> {
        val token = when (val auth = requireAdminSession()) {
            is ApiResult.Success -> auth.data
            is ApiResult.Error -> return auth
        }
        return if (restClient.configProvider().hasApiBackend) {
            restClient.callApiEndpoint("/api/admin/sms-monitor", method = "GET", accessToken = token)
        } else {
            restClient.getRest(
                table = "payment_requests",
                queryParams = mapOf(
                    "sms_text" to "not.is.null",
                    "order" to "created_at.desc",
                    "limit" to "25"
                ),
                accessToken = token
            )
        }
    }

    private inline fun <reified T> decodeList(res: ApiResult<String>): ApiResult<List<T>> {
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    ApiResult.Success(restClient.json.decodeFromString<List<T>>(res.data))
                } catch (e: Exception) {
                    ApiResult.Error("Failed to decode admin response: ${e.localizedMessage}")
                }
            }
        }
    }
}
