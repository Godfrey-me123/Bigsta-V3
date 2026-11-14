package com.example.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val isNetworkError: Boolean = false
    ) : ApiResult<Nothing>()
}

class SupabaseRestClient(
    val configProvider: () -> SupabaseEnvironment = { SupabaseConfig.load() }
) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getRest(
        table: String,
        queryParams: Map<String, String> = emptyMap(),
        accessToken: String? = null
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.isConfigured) {
            return@withContext ApiResult.Error("Supabase configuration missing. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets.")
        }
        val queryString = if (queryParams.isEmpty()) "" else {
            "?" + queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        }
        val request = Request.Builder()
            .url("${env.url}/rest/v1/$table$queryString")
            .get()
            .addHeader("apikey", env.publishableKey)
            .addHeader("Authorization", "Bearer ${accessToken ?: env.publishableKey}")
            .addHeader("Accept", "application/json")
            .build()

        executeRequest(request)
    }

    suspend fun postRest(
        table: String,
        bodyJson: String,
        accessToken: String? = null,
        preferReturnRepresentation: Boolean = true,
        upsertOnConflict: String? = null
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.isConfigured) {
            return@withContext ApiResult.Error("Supabase configuration missing. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets.")
        }
        val query = if (!upsertOnConflict.isNullOrBlank()) "?on_conflict=$upsertOnConflict" else ""
        val builder = Request.Builder()
            .url("${env.url}/rest/v1/$table$query")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .addHeader("apikey", env.publishableKey)
            .addHeader("Authorization", "Bearer ${accessToken ?: env.publishableKey}")
            .addHeader("Content-Type", "application/json")

        val preferParts = mutableListOf<String>()
        if (preferReturnRepresentation) preferParts.add("return=representation")
        if (!upsertOnConflict.isNullOrBlank()) preferParts.add("resolution=merge-duplicates")
        if (preferParts.isNotEmpty()) {
            builder.addHeader("Prefer", preferParts.joinToString(","))
        }

        executeRequest(builder.build())
    }

    suspend fun patchRest(
        table: String,
        filterQuery: String,
        bodyJson: String,
        accessToken: String? = null
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.isConfigured) {
            return@withContext ApiResult.Error("Supabase configuration missing. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets.")
        }
        val request = Request.Builder()
            .url("${env.url}/rest/v1/$table?$filterQuery")
            .patch(bodyJson.toRequestBody(jsonMediaType))
            .addHeader("apikey", env.publishableKey)
            .addHeader("Authorization", "Bearer ${accessToken ?: env.publishableKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .build()

        executeRequest(request)
    }

    suspend fun postRpc(
        functionName: String,
        bodyJson: String,
        accessToken: String? = null
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.isConfigured) {
            return@withContext ApiResult.Error("Supabase configuration missing.")
        }
        val request = Request.Builder()
            .url("${env.url}/rest/v1/rpc/$functionName")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .addHeader("apikey", env.publishableKey)
            .addHeader("Authorization", "Bearer ${accessToken ?: env.publishableKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        executeRequest(request)
    }

    suspend fun authRequest(
        path: String,
        method: String = "POST",
        bodyJson: String? = null,
        accessToken: String? = null
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.isConfigured) {
            return@withContext ApiResult.Error("Supabase configuration missing. Set SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in Secrets.")
        }
        val builder = Request.Builder()
            .url("${env.url}/auth/v1/$path")
            .addHeader("apikey", env.publishableKey)
            .addHeader("Content-Type", "application/json")

        if (!accessToken.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $accessToken")
        }
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((bodyJson ?: "{}").toRequestBody(jsonMediaType))
            "PUT" -> builder.put((bodyJson ?: "{}").toRequestBody(jsonMediaType))
        }
        executeRequest(builder.build())
    }

    suspend fun uploadStorageObject(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String,
        accessToken: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.isConfigured) {
            return@withContext ApiResult.Error("Supabase configuration missing.")
        }
        val cleanPath = objectPath.trimStart('/')
        val body: RequestBody = bytes.toRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url("${env.url}/storage/v1/object/$bucket/$cleanPath")
            .post(body)
            .addHeader("apikey", env.publishableKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("x-upsert", "true")
            .build()

        when (val result = executeRequest(request)) {
            is ApiResult.Success -> ApiResult.Success("${env.url}/storage/v1/object/public/$bucket/$cleanPath")
            is ApiResult.Error -> result
        }
    }

    suspend fun callApiEndpoint(
        path: String,
        method: String = "GET",
        bodyJson: String? = null,
        accessToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val env = configProvider()
        if (!env.hasApiBackend) {
            return@withContext ApiResult.Error("API backend URL is not configured (BIGSTA_API_BASE_URL).")
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val builder = Request.Builder()
            .url("${env.apiBaseUrl}$cleanPath")
            .addHeader("Accept", "application/json")

        if (!accessToken.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $accessToken")
        }
        extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((bodyJson ?: "{}").toRequestBody(jsonMediaType))
            "PUT" -> builder.put((bodyJson ?: "{}").toRequestBody(jsonMediaType))
        }
        executeRequest(builder.build())
    }

    private fun executeRequest(request: Request): ApiResult<String> {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ApiResult.Success(raw)
                } else {
                    ApiResult.Error(
                        message = parseErrorMessage(raw, response.code),
                        statusCode = response.code,
                        isNetworkError = false
                    )
                }
            }
        } catch (e: IOException) {
            ApiResult.Error(
                message = e.localizedMessage ?: "Network connection unavailable",
                statusCode = null,
                isNetworkError = true
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.localizedMessage ?: "Unexpected request failure",
                statusCode = null,
                isNetworkError = false
            )
        }
    }

    private fun parseErrorMessage(raw: String, code: Int): String {
        if (raw.isBlank()) return "Server returned HTTP $code"
        return try {
            val map = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(raw)
            val msg = map["msg"] ?: map["message"] ?: map["error_description"] ?: map["error"]
            msg?.toString()?.trim('"') ?: "Server returned HTTP $code"
        } catch (_: Exception) {
            raw.take(200)
        }
    }
}
