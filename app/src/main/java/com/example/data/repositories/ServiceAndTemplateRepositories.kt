package com.example.data.repositories

import com.example.core.database.CachedTemplateDao
import com.example.core.database.CachedTemplateEntity
import com.example.core.network.ApiResult
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.data.models.NidaRequestCreateDto
import com.example.data.models.NidaRequestDto
import com.example.data.models.TemplateDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

class ServiceRequestRepository(
    private val restClient: SupabaseRestClient,
    private val sessionManager: SessionManager
) {
    suspend fun submitServiceRequest(
        serviceType: String,
        firstName: String,
        lastName: String,
        phone: String,
        dateOfBirth: String,
        nationalId: String?,
        photoBytes: ByteArray? = null,
        photoMimeType: String = "image/jpeg"
    ): ApiResult<NidaRequestDto> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return ApiResult.Error("Please sign in to submit government form requests.")
        }
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        val cleanPhone = phone.trim()
        val cleanDob = dateOfBirth.trim()
        if (cleanFirst.isBlank() || cleanLast.isBlank() || cleanPhone.isBlank() || cleanDob.isBlank()) {
            return ApiResult.Error("First name, last name, phone number, and date of birth are required.")
        }

        var uploadedPhotoUrl: String? = null
        if (photoBytes != null && photoBytes.isNotEmpty()) {
            val ext = if (photoMimeType.contains("png")) "png" else "jpg"
            val path = "${session.userId}/${System.currentTimeMillis()}_photo.$ext"
            when (val uploadRes = restClient.uploadStorageObject(
                bucket = "nida-uploads",
                objectPath = path,
                bytes = photoBytes,
                contentType = photoMimeType,
                accessToken = session.accessToken
            )) {
                is ApiResult.Success -> uploadedPhotoUrl = uploadRes.data
                is ApiResult.Error -> return ApiResult.Error("Photo upload failed: ${uploadRes.message}")
            }
        }

        val dto = NidaRequestCreateDto(
            userId = session.userId,
            serviceType = serviceType,
            fullName = "$cleanFirst $cleanLast",
            firstName = cleanFirst,
            lastName = cleanLast,
            phone = cleanPhone,
            dateOfBirth = cleanDob,
            nationalId = nationalId?.trim()?.takeIf { it.isNotBlank() },
            photoUrl = uploadedPhotoUrl,
            status = "pending"
        )
        val payload = restClient.json.encodeToString(dto)
        val res = restClient.postRest(
            table = "nida_requests",
            bodyJson = payload,
            accessToken = session.accessToken
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val created = restClient.json.decodeFromString<List<NidaRequestDto>>(res.data).firstOrNull()
                    if (created != null) ApiResult.Success(created)
                    else ApiResult.Error("Server did not return created request record.")
                } catch (e: Exception) {
                    ApiResult.Error("Failed to decode request response: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun fetchUserRequests(): ApiResult<List<NidaRequestDto>> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) return ApiResult.Error("Sign in required.")
        val res = restClient.getRest(
            table = "nida_requests",
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
                    ApiResult.Success(restClient.json.decodeFromString<List<NidaRequestDto>>(res.data))
                } catch (e: Exception) {
                    ApiResult.Error("Could not parse requests: ${e.localizedMessage}")
                }
            }
        }
    }
}

class TemplateRepository(
    private val restClient: SupabaseRestClient,
    private val sessionManager: SessionManager,
    private val cachedTemplateDao: CachedTemplateDao
) {
    val cachedTemplatesFlow: Flow<List<CachedTemplateEntity>> = cachedTemplateDao.observeAll()

    suspend fun syncTemplates(): ApiResult<List<TemplateDto>> {
        val session = sessionManager.currentSession()
        val res = restClient.getRest(
            table = "templates",
            queryParams = mapOf("order" to "updated_at.desc"),
            accessToken = session.accessToken.takeIf { it.isNotBlank() }
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val list = restClient.json.decodeFromString<List<TemplateDto>>(res.data)
                    cachedTemplateDao.upsertAll(
                        list.map { dto ->
                            CachedTemplateEntity(
                                id = dto.id,
                                name = dto.name,
                                documentCategoryId = dto.resolvedCategory,
                                templateCardId = dto.templateCardId ?: dto.id,
                                side = dto.side.lowercase(),
                                backgroundUrl = dto.backgroundUrl,
                                localCachedPath = null,
                                isActive = dto.isActive,
                                layersJson = dto.layers?.toString() ?: "[]"
                            )
                        }
                    )
                    ApiResult.Success(list)
                } catch (e: Exception) {
                    ApiResult.Error("Failed to decode templates: ${e.localizedMessage}")
                }
            }
        }
    }
}
