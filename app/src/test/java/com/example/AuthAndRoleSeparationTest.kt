package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.network.ApiResult
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.core.security.UserRole
import com.example.core.security.UserSession
import com.example.data.repositories.AdminRepository
import com.example.data.repositories.PaymentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthAndRoleSeparationTest {

    @Test
    fun `UserSession isAdmin requires authenticated session, ADMIN role, and user_roles DB verification`() {
        val unverifiedAdmin = UserSession(
            accessToken = "jwt-token",
            userId = "uid-1",
            email = "admin@example.com",
            role = UserRole.ADMIN,
            roleVerifiedFromDb = false
        )
        assertFalse(unverifiedAdmin.isAdmin)

        val verifiedUser = UserSession(
            accessToken = "jwt-token",
            userId = "uid-2",
            email = "user@example.com",
            role = UserRole.USER,
            roleVerifiedFromDb = true
        )
        assertFalse(verifiedUser.isAdmin)

        val verifiedAdmin = UserSession(
            accessToken = "jwt-token",
            userId = "uid-1",
            email = "admin@example.com",
            role = UserRole.ADMIN,
            roleVerifiedFromDb = true
        )
        assertTrue(verifiedAdmin.isAdmin)
    }

    @Test
    fun `AdminRepository blocks non-admin and unauthenticated calls before network execution`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = SessionManager(context)
        val restClient = SupabaseRestClient()
        val adminRepository = AdminRepository(restClient, sessionManager)

        sessionManager.clearSession()
        val unauthRes = adminRepository.fetchAllPaymentRequests()
        assertTrue(unauthRes is ApiResult.Error)
        assertEquals(401, (unauthRes as ApiResult.Error).statusCode)

        sessionManager.saveSession(
            UserSession(
                accessToken = "jwt-user",
                userId = "uid-standard",
                email = "std@example.com",
                role = UserRole.USER,
                roleVerifiedFromDb = true
            )
        )
        val forbiddenRes = adminRepository.fetchAllPaymentRequests()
        assertTrue(forbiddenRes is ApiResult.Error)
        assertEquals(403, (forbiddenRes as ApiResult.Error).statusCode)
    }

    @Test
    fun `PaymentRepository rejects unauthenticated or invalid payment requests`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = SessionManager(context)
        val paymentRepository = PaymentRepository(SupabaseRestClient(), sessionManager)

        sessionManager.clearSession()
        val unauth = paymentRepository.submitPaymentRequest(
            packageId = "pkg-1",
            packageName = "Starter",
            amount = 5000.0,
            tokens = 10,
            paymentMethod = "M-Pesa",
            transactionRef = "REF123456",
            senderPhone = "0712345678"
        )
        assertTrue(unauth is ApiResult.Error)

        sessionManager.saveSession(
            UserSession(
                accessToken = "jwt-user",
                userId = "uid-1",
                email = "u@example.com",
                role = UserRole.USER,
                roleVerifiedFromDb = true
            )
        )
        val invalidRef = paymentRepository.submitPaymentRequest(
            packageId = "pkg-1",
            packageName = "Starter",
            amount = 0.0,
            tokens = 10,
            paymentMethod = "M-Pesa",
            transactionRef = "AB",
            senderPhone = null
        )
        assertTrue(invalidRef is ApiResult.Error)
    }
}
