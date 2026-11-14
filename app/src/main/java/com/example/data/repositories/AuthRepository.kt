package com.example.data.repositories

import com.example.core.network.ApiResult
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.core.security.UserRole
import com.example.core.security.UserSession
import com.example.data.models.AuthSessionResponseDto
import com.example.data.models.AuthSignInRequest
import com.example.data.models.AuthSignUpRequest
import com.example.data.models.ProfileDto
import com.example.data.models.UserRoleDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

class AuthRepository(
    private val restClient: SupabaseRestClient,
    private val sessionManager: SessionManager
) {
    val sessionFlow: Flow<UserSession> = sessionManager.sessionFlow

    suspend fun signIn(email: String, password: String): ApiResult<UserSession> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            return ApiResult.Error("Email and password are required.")
        }
        val payload = restClient.json.encodeToString(AuthSignInRequest(cleanEmail, password))
        return when (val res = restClient.authRequest("token?grant_type=password", "POST", payload)) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val auth = restClient.json.decodeFromString<AuthSessionResponseDto>(res.data)
                    val userId = auth.user?.id.orEmpty()
                    if (auth.accessToken.isBlank() || userId.isBlank()) {
                        return ApiResult.Error("Invalid authentication session returned by server.")
                    }
                    val verifiedRole = fetchVerifiedRole(userId, auth.accessToken)
                    val session = UserSession(
                        accessToken = auth.accessToken,
                        refreshToken = auth.refreshToken,
                        userId = userId,
                        email = auth.user?.email ?: cleanEmail,
                        role = verifiedRole.first,
                        roleVerifiedFromDb = verifiedRole.second
                    )
                    sessionManager.saveSession(session)
                    // Optional server bootstrap if BIGSTA_API_BASE_URL is configured
                    if (restClient.configProvider().hasApiBackend) {
                        restClient.callApiEndpoint("/api/auth/bootstrap", "POST", "{}", auth.accessToken)
                    }
                    ApiResult.Success(session)
                } catch (e: Exception) {
                    ApiResult.Error("Failed to parse authentication response: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        region: String
    ): ApiResult<String> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.length < 6 || fullName.isBlank()) {
            return ApiResult.Error("Full name, valid email, and password (min 6 chars) are required.")
        }
        val metadata = buildMap {
            put("full_name", fullName.trim())
            if (phone.isNotBlank()) put("phone", phone.trim())
            if (region.isNotBlank()) put("region", region.trim())
        }
        val payload = restClient.json.encodeToString(AuthSignUpRequest(cleanEmail, password, metadata))
        return when (val res = restClient.authRequest("signup", "POST", payload)) {
            is ApiResult.Error -> res
            is ApiResult.Success -> ApiResult.Success("Account created. Check your email for confirmation if required, then sign in.")
        }
    }

    suspend fun sendPasswordReset(email: String): ApiResult<String> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) return ApiResult.Error("Please enter your email address.")
        val payload = """{"email":"$cleanEmail"}"""
        return when (val res = restClient.authRequest("recover", "POST", payload)) {
            is ApiResult.Error -> res
            is ApiResult.Success -> ApiResult.Success("Password recovery email requested for $cleanEmail.")
        }
    }

    /**
     * Authorizes admin role strictly from protected `user_roles` table using caller's session token.
     * Never trusts profile roles or hardcoded email allowlists.
     */
    suspend fun fetchVerifiedRole(userId: String, accessToken: String): Pair<UserRole, Boolean> {
        val res = restClient.getRest(
            table = "user_roles",
            queryParams = mapOf(
                "user_id" to "eq.$userId",
                "select" to "id,user_id,role"
            ),
            accessToken = accessToken
        )
        return when (res) {
            is ApiResult.Success -> {
                try {
                    val roles = restClient.json.decodeFromString<List<UserRoleDto>>(res.data)
                    val isAdmin = roles.any { it.role.equals("admin", ignoreCase = true) }
                    val resolved = if (isAdmin) UserRole.ADMIN else UserRole.USER
                    resolved to true
                } catch (_: Exception) {
                    UserRole.USER to false
                }
            }
            is ApiResult.Error -> UserRole.USER to false
        }
    }

    suspend fun refreshSessionRole(): UserSession {
        val current = sessionManager.currentSession()
        if (!current.isAuthenticated) return current
        val (role, verified) = fetchVerifiedRole(current.userId, current.accessToken)
        sessionManager.updateVerifiedRole(role, verified)
        return current.copy(role = role, roleVerifiedFromDb = verified)
    }

    suspend fun fetchCurrentProfile(): ApiResult<ProfileDto?> {
        val session = sessionManager.currentSession()
        if (!session.isAuthenticated) return ApiResult.Error("Not signed in")
        val res = restClient.getRest(
            table = "profiles",
            queryParams = mapOf(
                "or" to "(id.eq.${session.userId},user_id.eq.${session.userId})",
                "limit" to "1"
            ),
            accessToken = session.accessToken
        )
        return when (res) {
            is ApiResult.Error -> res
            is ApiResult.Success -> {
                try {
                    val list = restClient.json.decodeFromString<List<ProfileDto>>(res.data)
                    ApiResult.Success(list.firstOrNull())
                } catch (e: Exception) {
                    ApiResult.Error("Could not decode profile: ${e.localizedMessage}")
                }
            }
        }
    }

    suspend fun signOut() {
        val session = sessionManager.currentSession()
        if (session.accessToken.isNotBlank()) {
            restClient.authRequest("logout", "POST", "{}", session.accessToken)
        }
        sessionManager.clearSession()
    }
}
