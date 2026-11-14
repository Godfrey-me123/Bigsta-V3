package com.example.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectivityState(
    val hasTransport: Boolean = false,
    val backendReachable: Boolean = false,
    val databaseStatus: String = "Unverified",
    val lastCheckedAtMs: Long = 0L
)

class ConnectivityMonitor(
    context: Context,
    private val restClient: SupabaseRestClient
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(
        ConnectivityState(hasTransport = isCurrentlyOnline())
    )
    val state: StateFlow<ConnectivityState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _state.value = _state.value.copy(hasTransport = true)
        }

        override fun onLost(network: Network) {
            _state.value = _state.value.copy(
                hasTransport = isCurrentlyOnline(),
                backendReachable = false
            )
        }
    }

    init {
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(req, callback)
        } catch (_: Exception) {
        }
    }

    fun isCurrentlyOnline(): Boolean {
        return try {
            val active = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun probeBackend(): ConnectivityState {
        val online = isCurrentlyOnline()
        val env = restClient.configProvider()
        if (!online) {
            val offline = ConnectivityState(
                hasTransport = false,
                backendReachable = false,
                databaseStatus = "Offline (no device internet)",
                lastCheckedAtMs = System.currentTimeMillis()
            )
            _state.value = offline
            return offline
        }
        if (!env.isConfigured) {
            val unconfigured = ConnectivityState(
                hasTransport = true,
                backendReachable = false,
                databaseStatus = "Missing SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY",
                lastCheckedAtMs = System.currentTimeMillis()
            )
            _state.value = unconfigured
            return unconfigured
        }

        // Probe /api/health?deep=1 if API backend is configured, otherwise probe Supabase REST token_packages
        val probe = if (env.hasApiBackend) {
            restClient.callApiEndpoint("/api/health?deep=1", method = "GET")
        } else {
            restClient.getRest("token_packages", mapOf("select" to "id", "limit" to "1"))
        }

        val updated = when (probe) {
            is ApiResult.Success -> ConnectivityState(
                hasTransport = true,
                backendReachable = true,
                databaseStatus = "Connected (${env.projectId.ifBlank { "Supabase" }})",
                lastCheckedAtMs = System.currentTimeMillis()
            )
            is ApiResult.Error -> ConnectivityState(
                hasTransport = true,
                backendReachable = false,
                databaseStatus = probe.message,
                lastCheckedAtMs = System.currentTimeMillis()
            )
        }
        _state.value = updated
        return updated
    }
}
