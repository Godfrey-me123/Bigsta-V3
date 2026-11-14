package com.example.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PaymentSubmissionDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val payload: JsonElement? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PaymentReviewDto(
    val id: String = "",
    @SerialName("submission_id") val submissionId: String = "",
    @SerialName("admin_id") val adminId: String = "",
    @SerialName("review_notes") val reviewNotes: String? = null,
    val status: String = "pending",
    @SerialName("reviewed_at") val reviewedAt: String? = null
)

@Serializable
data class TemplateCategoryDto(
    val id: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("service_type") val serviceType: String = "NIDA",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TemplateElementDto(
    val id: String = "",
    @SerialName("template_id") val templateId: String = "",
    @SerialName("element_name") val elementName: String = "",
    @SerialName("element_type") val elementType: String = "text",
    @SerialName("element_json") val elementJson: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TemplateVersionDto(
    val id: String = "",
    @SerialName("template_id") val templateId: String = "",
    @SerialName("version_number") val versionNumber: Int = 1,
    @SerialName("template_json") val templateJson: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TokenWalletDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val balance: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TokenRecordDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val balance: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserSettingDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("profile_settings") val profileSettings: JsonElement? = null,
    val preferences: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
