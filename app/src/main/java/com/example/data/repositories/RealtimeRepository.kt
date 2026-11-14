package com.example.data.repositories

import com.example.core.network.PostgresChangeEvent
import com.example.core.network.RealtimeStatus
import com.example.core.network.SupabaseRealtimeClient
import com.example.core.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class RealtimeRepository(
    val realtimeClient: SupabaseRealtimeClient,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val status: StateFlow<RealtimeStatus> = realtimeClient.status
    val changeEvents: Flow<PostgresChangeEvent> = realtimeClient.changeEvents

    init {
        scope.launch {
            sessionManager.sessionFlow.collect { session ->
                if (session.isAuthenticated) {
                    realtimeClient.updateAccessToken(session.accessToken)
                }
            }
        }
    }

    fun startListening(tables: List<String> = listOf("payment_requests", "nida_requests", "profiles", "user_feedback")) {
        scope.launch {
            val session = sessionManager.currentSession()
            val token = if (session.isAuthenticated) session.accessToken else null
            realtimeClient.connect(token)
            for (table in tables) {
                realtimeClient.subscribeTable(table)
            }
        }
    }

    fun stopListening() {
        realtimeClient.disconnect()
    }

    fun observeTable(table: String): Flow<PostgresChangeEvent> {
        realtimeClient.subscribeTable(table)
        return realtimeClient.changeEvents.filter { it.table.equals(table, ignoreCase = true) }
    }

    fun observePaymentRequests(): Flow<PostgresChangeEvent> = observeTable("payment_requests")

    fun observeNidaRequests(): Flow<PostgresChangeEvent> = observeTable("nida_requests")

    fun observeProfiles(): Flow<PostgresChangeEvent> = observeTable("profiles")

    fun observeUserFeedback(): Flow<PostgresChangeEvent> = observeTable("user_feedback")

    fun observeSmsLogs(): Flow<PostgresChangeEvent> = observeTable("sms_logs")
}
