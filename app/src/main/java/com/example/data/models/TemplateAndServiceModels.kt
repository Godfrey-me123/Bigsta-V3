package com.example.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TemplateDto(
    val id: String = "",
    val name: String = "",
    val category: String? = null,
    @SerialName("document_category_id") val documentCategoryId: String? = null,
    @SerialName("template_card_id") val templateCardId: String? = null,
    val side: String = "front",
    @SerialName("background_url") val backgroundUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val layers: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val resolvedCategory: String
        get() = documentCategoryId?.takeIf { it.isNotBlank() }
            ?: category?.takeIf { it.isNotBlank() }
            ?: "NIDA"
}

@Serializable
data class NidaRequestCreateDto(
    @SerialName("user_id") val userId: String,
    @SerialName("service_type") val serviceType: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val phone: String,
    @SerialName("date_of_birth") val dateOfBirth: String,
    @SerialName("national_id") val nationalId: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("signature_url") val signatureUrl: String? = null,
    val status: String = "pending"
)

@Serializable
data class NidaRequestDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("service_type") val serviceType: String = "NIDA",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val phone: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("national_id") val nationalId: String? = null,
    val status: String = "pending",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("signature_url") val signatureUrl: String? = null,
    @SerialName("response_files") val responseFiles: JsonElement? = null,
    @SerialName("admin_notes") val adminNotes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val applicantDisplayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "Unnamed Applicant" }
}

@Serializable
data class SystemSettingDto(
    val id: String = "",
    val value: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserFeedbackDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    val message: String = "",
    val category: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null
)
