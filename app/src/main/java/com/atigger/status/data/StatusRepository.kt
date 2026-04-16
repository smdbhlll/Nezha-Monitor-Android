package com.atigger.status.data

import android.util.Log
import com.google.gson.Gson
import com.atigger.status.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StatusRepository(
    private val gson: Gson = Gson()
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    private val webSocketClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    fun observeServerSnapshots(
        config: MonitorConfig,
        authToken: String? = null
    ): Flow<StatusStreamEvent> =
        callbackFlow {
            val strings = AppStrings.of(config.language)
            val closedByClient = AtomicBoolean(false)
            trySend(StatusStreamEvent.Connecting)
            val resolvedAuthToken = runCatching {
                resolveAuthToken(config, strings, authToken)
            }.getOrElse { throwable ->
                close(throwable)
                return@callbackFlow
            }

            val requestBuilder = Request.Builder()
                .url(config.websocketUrl())
                .header("User-Agent", USER_AGENT)

            if (!resolvedAuthToken.isNullOrBlank()) {
                requestBuilder.header("Cookie", "nz-jwt=$resolvedAuthToken")
            }

            val request = requestBuilder.build()
            Log.d(
                TAG,
                "Opening WebSocket: url=${request.url}, requiresLogin=${config.requiresLogin}, " +
                    "hasToken=${!resolvedAuthToken.isNullOrBlank()}, headers=${request.headers}"
            )

            val webSocket = webSocketClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(
                            TAG,
                            "WebSocket opened: url=${response.request.url}, code=${response.code}, " +
                                "message=${response.message}, headers=${response.headers}"
                        )
                        trySend(StatusStreamEvent.WaitingForFirstSnapshot)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val snapshot = runCatching { parseSnapshot(text, strings) }.getOrElse { throwable ->
                            close(IllegalStateException(strings.failedToParseWebsocketPayload, throwable))
                            return
                        }
                        trySend(StatusStreamEvent.SnapshotReceived(snapshot))
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (closedByClient.get()) return
                        val responseBodyPreview = runCatching {
                            response?.peekBody(LOG_BODY_PREVIEW_LENGTH.toLong())?.string()
                        }.getOrNull()
                        Log.e(
                            TAG,
                            "WebSocket failure: url=${response?.request?.url ?: request.url}, " +
                                "code=${response?.code}, message=${response?.message}, " +
                                "headers=${response?.headers}, body=$responseBodyPreview, " +
                                "requestHeaders=${request.headers}, error=${t.message}",
                            t
                        )
                        close(
                            IllegalStateException(
                                response?.let { strings.webSocketRequestFailedHttp(it.code) }
                                    ?: (t.message ?: strings.webSocketRequestFailed),
                                t
                            )
                        )
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (closedByClient.get()) return
                        val closeReason = reason.ifBlank { "no reason provided" }
                        Log.w(
                            TAG,
                            "WebSocket closed unexpectedly: url=${request.url}, code=$code, reason=$closeReason"
                        )
                        close(
                            IllegalStateException(
                                strings.webSocketClosedUnexpectedly(code, closeReason)
                            )
                        )
                    }
                }
            )

            awaitClose {
                closedByClient.set(true)
                webSocket.close(NORMAL_CLOSURE_STATUS, "client closed")
                webSocket.cancel()
            }
        }.conflate()
            .flowOn(Dispatchers.IO)

    suspend fun fetchAuthToken(config: MonitorConfig): String? =
        withContext(Dispatchers.IO) {
            val strings = AppStrings.of(config.language)
            authenticate(config, strings)
        }

    suspend fun fetchServerGroups(
        config: MonitorConfig,
        authToken: String? = null
    ): List<ServerGroupUiModel> =
        withContext(Dispatchers.IO) {
            val strings = AppStrings.of(config.language)
            fetchServerGroupsInternal(config, resolveAuthToken(config, strings, authToken))
        }

    private fun authenticate(config: MonitorConfig, strings: AppStrings): String? {
        if (!config.requiresLogin) return null
        val requestBody = gson.toJson(
            LoginRequest(
                username = config.username,
                password = config.password
            )
        ).toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(config.loginUrl())
            .post(requestBody)
            .header("Accept", "*/*")
            .header("Content-Type", "application/json")
            .header("Origin", config.originUrl())
            .header("Referer", "${config.originUrl()}/dashboard/login")
            .header("User-Agent", USER_AGENT)
            .build()

        Log.d(
            TAG,
            "Login request: url=${request.url}, requiresLogin=${config.requiresLogin}, " +
                "origin=${config.originUrl()}, usernameLength=${config.username.length}"
        )
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            Log.d(
                TAG,
                "Login response: url=${request.url}, code=${response.code}, message=${response.message}, " +
                    "headers=${response.headers}, body=${body.take(LOG_BODY_PREVIEW_LENGTH)}"
            )
            if (!response.isSuccessful) {
                throw IllegalStateException("${strings.loginFailedCheckConfig} HTTP ${response.code}")
            }
            val payload = runCatching { gson.fromJson(body, LoginResponse::class.java) }.getOrElse { throwable ->
                throw IllegalStateException(strings.failedToParseLoginResponse, throwable)
            }
            val token = payload.data?.token?.takeIf { it.isNotBlank() }
            if (!payload.success || token == null) {
                throw IllegalStateException(payload.message ?: strings.loginFailedCheckConfig)
            }
            return token
        }
    }

    private fun resolveAuthToken(
        config: MonitorConfig,
        strings: AppStrings,
        authToken: String?
    ): String? {
        if (!config.requiresLogin) return null
        return authToken ?: authenticate(config, strings)
    }

    private fun fetchServerGroupsInternal(config: MonitorConfig, authToken: String?): List<ServerGroupUiModel> {
        val strings = AppStrings.of(config.language)
        val requestBuilder = Request.Builder()
            .url(config.serverGroupUrl())
            .get()
            .header("Accept", "application/json")
            .header("Origin", config.originUrl())
            .header("Referer", "${config.originUrl()}/dashboard")
            .header("User-Agent", USER_AGENT)

        if (!authToken.isNullOrBlank()) {
            requestBuilder.header("Cookie", "nz-jwt=$authToken")
        }

        val request = requestBuilder.build()
        Log.d(
            TAG,
            "Server group request: url=${request.url}, requiresLogin=${config.requiresLogin}, " +
                "hasToken=${!authToken.isNullOrBlank()}, origin=${config.originUrl()}"
        )
        return httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            Log.d(
                TAG,
                "Server group response: url=${request.url}, code=${response.code}, message=${response.message}, " +
                    "headers=${response.headers}, body=${body.take(LOG_BODY_PREVIEW_LENGTH)}"
            )
            if (!response.isSuccessful) {
                throw IllegalStateException(strings.serverGroupRequestFailedHttp(response.code))
            }
            val payload = runCatching {
                gson.fromJson(body, ServerGroupResponse::class.java)
            }.getOrElse { throwable ->
                throw IllegalStateException(strings.failedToParseServerGroupResponse, throwable)
            }
            if (!payload.success) {
                throw IllegalStateException(payload.message ?: strings.serverGroupRequestFailed)
            }
            payload.data.toUiModels()
        }
    }

    private fun parseSnapshot(message: String, strings: AppStrings): StatusSnapshot {
        val payload = gson.fromJson(message, StatusWsResponse::class.java)
        val now = Instant.now()
        return StatusSnapshot(
            lastUpdated = formatUpdatedAt(payload.now),
            servers = payload.servers
                .sortedByDescending { it.displayIndex }
                .map { server ->
                    server.toUiModel(
                        gson = gson,
                        strings = strings,
                        now = now,
                        groupIds = emptySet()
                    )
                }
        )
    }

    private fun formatUpdatedAt(timestamp: Long?): String {
        val instant = timestamp?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        return DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    companion object {
        private const val TAG = "StatusRepository"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val LOG_BODY_PREVIEW_LENGTH = 512
        private const val TIMEOUT_MS = 15000L
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
    }
}

private data class LoginRequest(
    val username: String,
    val password: String
)

private data class LoginResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: LoginTokenData? = null
)

private data class LoginTokenData(
    val token: String? = null,
    val expire: String? = null
)
