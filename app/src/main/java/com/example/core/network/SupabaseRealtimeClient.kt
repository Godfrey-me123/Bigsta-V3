package com.example.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

sealed class RealtimeStatus {
    data object Disconnected : RealtimeStatus()
    data object Connecting : RealtimeStatus()
    data object Connected : RealtimeStatus()
    data class Error(val message: String) : RealtimeStatus()
}

data class PostgresChangeEvent(
    val schema: String,
    val table: String,
    val eventType: String,
    val record: JsonObject? = null,
    val oldRecord: JsonObject? = null,
    val rawPayload: String = ""
)

class SupabaseRealtimeClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val configProvider: () -> SupabaseEnvironment = { SupabaseConfig.load() }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val refCounter = AtomicInteger(1)
    private val subscribedTables = CopyOnWriteArraySet<String>()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentAccessToken: String? = null
    private var isManualClose = false

    private val _status = MutableStateFlow<RealtimeStatus>(RealtimeStatus.Disconnected)
    val status: StateFlow<RealtimeStatus> = _status.asStateFlow()

    private val _changeEvents = MutableSharedFlow<PostgresChangeEvent>(extraBufferCapacity = 64)
    val changeEvents: SharedFlow<PostgresChangeEvent> = _changeEvents.asSharedFlow()

    fun connect(accessToken: String? = null) {
        isManualClose = false
        currentAccessToken = accessToken
        if (_status.value is RealtimeStatus.Connected || _status.value is RealtimeStatus.Connecting) {
            accessToken?.let { pushAccessTokenToChannels(it) }
            return
        }

        val env = configProvider()
        if (!env.isConfigured) {
            _status.value = RealtimeStatus.Error("Supabase environment is not configured")
            return
        }

        _status.value = RealtimeStatus.Connecting
        val request = Request.Builder()
            .url(env.realtimeWebsocketUrl)
            .addHeader("apikey", env.publishableKey)
            .apply {
                accessToken?.let { addHeader("Authorization", "Bearer $it") }
            }
            .build()

        webSocket = httpClient.newWebSocket(request, createWebSocketListener())
    }

    fun subscribeTable(table: String) {
        val cleanTable = table.trim()
        if (cleanTable.isBlank()) return
        subscribedTables.add(cleanTable)
        if (_status.value is RealtimeStatus.Connected) {
            sendJoinChannel(cleanTable)
        }
    }

    fun unsubscribeTable(table: String) {
        val cleanTable = table.trim()
        subscribedTables.remove(cleanTable)
        if (_status.value is RealtimeStatus.Connected) {
            sendLeaveChannel(cleanTable)
        }
    }

    fun updateAccessToken(accessToken: String?) {
        currentAccessToken = accessToken
        if (_status.value is RealtimeStatus.Connected && !accessToken.isNullOrBlank()) {
            pushAccessTokenToChannels(accessToken)
        }
    }

    fun disconnect() {
        isManualClose = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _status.value = RealtimeStatus.Disconnected
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _status.value = RealtimeStatus.Connected
                startHeartbeat()
                for (table in subscribedTables) {
                    sendJoinChannel(table)
                }
                currentAccessToken?.let { pushAccessTokenToChannels(it) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _status.value = RealtimeStatus.Disconnected
                if (!isManualClose) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                _status.value = RealtimeStatus.Error(t.message ?: "Realtime connection failure")
                if (!isManualClose) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        scope.launch {
            try {
                val element = json.parseToJsonElement(text).jsonObject
                val event = element["event"]?.jsonPrimitive?.content ?: ""
                val topic = element["topic"]?.jsonPrimitive?.content ?: ""

                if (event == "postgres_changes") {
                    val payload = element["payload"]?.jsonObject ?: return@launch
                    val data = payload["data"]?.jsonObject ?: payload
                    val schema = data["schema"]?.jsonPrimitive?.content ?: "public"
                    val table = data["table"]?.jsonPrimitive?.content
                        ?: topic.substringAfterLast(":", "")
                    val type = data["type"]?.jsonPrimitive?.content ?: "UNKNOWN"
                    val record = data["record"]?.jsonObject
                    val oldRecord = data["old_record"]?.jsonObject

                    _changeEvents.emit(
                        PostgresChangeEvent(
                            schema = schema,
                            table = table,
                            eventType = type,
                            record = record,
                            oldRecord = oldRecord,
                            rawPayload = text
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore malformed heartbeats or ping ACKs
            }
        }
    }

    private fun sendJoinChannel(table: String) {
        val ref = refCounter.getAndIncrement().toString()
        val topic = "realtime:public:$table"
        val payload = """
            {
              "topic": "$topic",
              "event": "phx_join",
              "payload": {
                "config": {
                  "postgres_changes": [
                    {
                      "event": "*",
                      "schema": "public",
                      "table": "$table"
                    }
                  ]
                }
              },
              "ref": "$ref"
            }
        """.trimIndent()
        webSocket?.send(payload)
    }

    private fun sendLeaveChannel(table: String) {
        val ref = refCounter.getAndIncrement().toString()
        val topic = "realtime:public:$table"
        val payload = """{"topic":"$topic","event":"phx_leave","payload":{},"ref":"$ref"}"""
        webSocket?.send(payload)
    }

    private fun pushAccessTokenToChannels(token: String) {
        for (table in subscribedTables) {
            val ref = refCounter.getAndIncrement().toString()
            val topic = "realtime:public:$table"
            val payload = """{"topic":"$topic","event":"access_token","payload":{"access_token":"$token"},"ref":"$ref"}"""
            webSocket?.send(payload)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _status.value is RealtimeStatus.Connected) {
                delay(25_000)
                val ref = refCounter.getAndIncrement().toString()
                val heartbeat = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""
                webSocket?.send(heartbeat)
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000)
            if (!isManualClose && _status.value !is RealtimeStatus.Connected) {
                connect(currentAccessToken)
            }
        }
    }
}
