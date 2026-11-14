package com.example.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "bigsta_session")

enum class UserRole(val wireValue: String) {
    USER("user"),
    ADMIN("admin");

    companion object {
        fun fromWire(value: String?): UserRole =
            if (value.equals("admin", ignoreCase = true)) ADMIN else USER
    }
}

data class UserSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: String = "",
    val email: String = "",
    val role: UserRole = UserRole.USER,
    val roleVerifiedFromDb: Boolean = false
) {
    val isAuthenticated: Boolean
        get() = accessToken.isNotBlank() && userId.isNotBlank()

    val isAdmin: Boolean
        get() = isAuthenticated && roleVerifiedFromDb && role == UserRole.ADMIN
}

class SessionManager(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val ROLE = stringPreferencesKey("role")
        val ROLE_VERIFIED = stringPreferencesKey("role_verified")
    }

    val sessionFlow: Flow<UserSession> = context.sessionDataStore.data.map { prefs ->
        UserSession(
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
            userId = prefs[Keys.USER_ID].orEmpty(),
            email = prefs[Keys.EMAIL].orEmpty(),
            role = UserRole.fromWire(prefs[Keys.ROLE]),
            roleVerifiedFromDb = prefs[Keys.ROLE_VERIFIED] == "true"
        )
    }

    suspend fun currentSession(): UserSession = sessionFlow.first()

    suspend fun saveSession(session: UserSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
            prefs[Keys.ROLE] = session.role.wireValue
            prefs[Keys.ROLE_VERIFIED] = session.roleVerifiedFromDb.toString()
        }
    }

    suspend fun updateVerifiedRole(role: UserRole, verified: Boolean) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.ROLE] = role.wireValue
            prefs[Keys.ROLE_VERIFIED] = verified.toString()
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
