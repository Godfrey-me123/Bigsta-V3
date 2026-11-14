package com.example.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthSignInRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthSignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class AuthUserDto(
    val id: String = "",
    val email: String? = null,
    @SerialName("user_metadata") val userMetadata: JsonObject? = null
)

@Serializable
data class AuthSessionResponseDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: AuthUserDto? = null
)

@Serializable
data class ProfileDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val region: String? = null,
    @SerialName("tokens_remaining") val tokensRemaining: Int? = null,
    val usages: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val displayName: String
        get() = fullName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")
            ?: "BIGsta User"

    val effectiveTokens: Int
        get() = tokensRemaining ?: usages ?: 0
}

@Serializable
data class UserRoleDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String = "",
    val role: String = "user"
)

@Serializable
data class BootstrapResponseDto(
    val ok: Boolean = true,
    val role: String? = null,
    val profile: ProfileDto? = null
)
