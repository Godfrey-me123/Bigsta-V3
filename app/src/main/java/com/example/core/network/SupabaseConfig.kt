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
}

object SupabaseConfig {
    fun load(): SupabaseEnvironment {
        val url = sequenceOf(
            BuildConfig.SUPABASE_URL,
            BuildConfig.VITE_SUPABASE_URL
        ).firstOrNull { it.isNotBlank() }?.trimEnd('/') ?: ""

        val key = sequenceOf(
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            BuildConfig.VITE_SUPABASE_PUBLISHABLE_KEY
        ).firstOrNull { it.isNotBlank() }?.trim() ?: ""

        val projectId = sequenceOf(
            BuildConfig.SUPABASE_PROJECT_ID,
            BuildConfig.VITE_SUPABASE_PROJECT_ID
        ).firstOrNull { it.isNotBlank() }?.trim()
            ?: url.substringAfter("https://", "").substringBefore(".", "")

        val apiBaseUrl = BuildConfig.BIGSTA_API_BASE_URL.trim().trimEnd('/')

        return SupabaseEnvironment(
            url = url,
            publishableKey = key,
            projectId = projectId,
            apiBaseUrl = apiBaseUrl
        )
    }
}
