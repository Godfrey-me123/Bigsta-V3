package com.example.core.network

import com.example.BuildConfig

data class SupabaseEnvironment(
    val url: String,
    val publishableKey: String,
    val projectId: String,
    val apiBaseUrl: String
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && publishableKey.isNotBlank()

    val hasApiBackend: Boolean
        get() = apiBaseUrl.isNotBlank()

    val realtimeWebsocketUrl: String
        get() {
            val wsBase = url.replace("https://", "wss://").replace("http://", "ws://")
            return "$wsBase/realtime/v1/websocket?apikey=$publishableKey&vsn=1.0.0"
        }
}

object SupabaseConfig {
    const val DEFAULT_SUPABASE_URL = "https://fpkgkxyihbqlrsstvnnh.supabase.co"
    const val DEFAULT_SUPABASE_PUBLISHABLE_KEY = "sb_publishable_JMqbpjQlM_0ZUYxHDpZH1g_yq4iKMzG"
    const val DEFAULT_SUPABASE_PROJECT_ID = "fpkgkxyihbqlrsstvnnh"

    fun load(): SupabaseEnvironment {
        val url = sequenceOf(
            BuildConfig.SUPABASE_URL,
            BuildConfig.VITE_SUPABASE_URL,
            DEFAULT_SUPABASE_URL
        ).firstOrNull { it.isNotBlank() }?.trimEnd('/') ?: DEFAULT_SUPABASE_URL

        val key = sequenceOf(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            BuildConfig.VITE_SUPABASE_PUBLISHABLE_KEY,
            DEFAULT_SUPABASE_PUBLISHABLE_KEY
        ).firstOrNull { it.isNotBlank() }?.trim() ?: DEFAULT_SUPABASE_PUBLISHABLE_KEY

        val projectId = sequenceOf(
            BuildConfig.SUPABASE_PROJECT_ID,
            BuildConfig.VITE_SUPABASE_PROJECT_ID,
            DEFAULT_SUPABASE_PROJECT_ID
        ).firstOrNull { it.isNotBlank() }?.trim()
            ?: url.substringAfter("https://", "").substringBefore(".", "")

        val apiBaseUrl = sequenceOf(
            BuildConfig.BIGSTA_API_BASE_URL,
            url
        ).firstOrNull { it.isNotBlank() }?.trim()?.trimEnd('/') ?: url

        return SupabaseEnvironment(
            url = url,
            publishableKey = key,
            projectId = projectId,
            apiBaseUrl = apiBaseUrl
        )
    }
}
