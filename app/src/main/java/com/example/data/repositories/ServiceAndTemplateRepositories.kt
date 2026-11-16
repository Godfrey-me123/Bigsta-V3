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

    suspend fun saveTemplate(
        id: String? = null,
        name: String,
        category: String,
        side: String,
        backgroundUrl: String? = null,
        isActive: Boolean = true,
        isUniversal: Boolean = false,
        layersJson: String = "[]"
    ): ApiResult<TemplateDto> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return ApiResult.Error("Authentication required.", statusCode = 401)
        }
        if (!session.isAdmin) {
            return ApiResult.Error("Access Denied: Only verified administrators can create or modify master templates.", statusCode = 403)
        }
        val safeName = name.trim().ifBlank { "$category Template" }
        val templateId = id?.takeIf { it.isNotBlank() } ?: "tpl_${System.currentTimeMillis()}"

        // Persist to local Room cache first for offline instant availability
        cachedTemplateDao.upsertAll(
            listOf(
                CachedTemplateEntity(
                    id = templateId,
                    name = safeName,
                    documentCategoryId = category,
                    templateCardId = templateId,
                    side = side.lowercase(),
                    backgroundUrl = backgroundUrl,
                    localCachedPath = null,
                    isActive = isActive,
                    layersJson = layersJson
                )
            )
        )

        val localResult = TemplateDto(
            id = templateId,
            name = safeName,
            category = category,
            documentCategoryId = category,
            templateCardId = templateId,
            side = side.lowercase(),
            backgroundUrl = backgroundUrl,
            isActive = isActive
        )

        // If authenticated, sync directly with cloud Supabase templates table
        if (session.isAuthenticated) {
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
                accessToken = session.accessToken,
                upsertOnConflict = "id"
            )
            return when (res) {
                is ApiResult.Success -> {
                    try {
                        val parsed = restClient.json.decodeFromString<List<TemplateDto>>(res.data).firstOrNull()
                        ApiResult.Success(parsed ?: localResult)
                    } catch (e: Exception) {
                        ApiResult.Success(localResult)
                    }
                }
                is ApiResult.Error -> ApiResult.Success(localResult)
            }
        }

        return ApiResult.Success(localResult)
    }

    suspend fun deleteTemplate(templateId: String): ApiResult<Unit> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) {
            return ApiResult.Error("Authentication required.", statusCode = 401)
        }
        if (!session.isAdmin) {
            return ApiResult.Error("Access Denied: Only verified administrators can delete master templates.", statusCode = 403)
        }
        cachedTemplateDao.deleteById(templateId)
        if (session.isAuthenticated) {
            restClient.deleteRest(
                table = "templates",
                filterQuery = "id=eq.$templateId",
                accessToken = session.accessToken
            )
        }
        return ApiResult.Success(Unit)
    }

    suspend fun uploadTemplateBackground(
        bytes: ByteArray,
        mimeType: String
    ): ApiResult<String> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) return ApiResult.Error("Sign in required to upload backgrounds.", statusCode = 401)
        if (!session.isAdmin) return ApiResult.Error("Access Denied: Only verified administrators can upload template backgrounds.", statusCode = 403)
        val ext = if (mimeType.contains("png")) "png" else "jpg"
        val path = "templates/${session.userId}_${System.currentTimeMillis()}.$ext"
        return restClient.uploadStorageObject(
            bucket = "template_backgrounds",
            objectPath = path,
            bytes = bytes,
            contentType = mimeType,
            accessToken = session.accessToken
        )
    }
}
