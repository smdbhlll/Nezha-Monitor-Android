package com.atigger.status.data

import android.content.Context
import android.content.SharedPreferences
import java.net.URI

data class MonitorConfig(
    val baseUrl: String = "",
    val requiresLogin: Boolean = false,
    val username: String = "",
    val password: String = "",
    val language: AppLanguage = AppLanguage.ZH,
    val liveUpdateMetric: LiveUpdateMetric = LiveUpdateMetric.CPU
) {
    val isConfigured: Boolean
        get() = normalizedBaseUrlOrNull() != null

    fun normalizedBaseUrlOrNull(): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return null
        var normalized = trimmed.removeSuffix("/")
        ENDPOINT_SUFFIXES.forEach { suffix ->
            if (normalized.endsWith(suffix, ignoreCase = true)) {
                normalized = normalized.dropLast(suffix.length)
            }
        }
        return normalized.takeIf { it.isNotBlank() }
    }

    fun websocketUrl(): String = buildEndpointUrl("/api/v1/ws/server", websocket = true)

    fun loginUrl(): String = buildEndpointUrl("/api/v1/login", websocket = false)

    fun serverGroupUrl(): String = buildEndpointUrl("/api/v1/server-group", websocket = false)

    fun originUrl(): String {
        val uri = URI(normalizedBaseUrlOrNull() ?: "")
        val scheme = when (uri.scheme?.lowercase()) {
            "https", "wss" -> "https"
            "http", "ws" -> "http"
            else -> uri.scheme ?: "https"
        }
        return buildString {
            append(scheme)
            append("://")
            append(uri.host)
            if (uri.port != -1) {
                append(":")
                append(uri.port)
            }
        }
    }

    fun validationErrorOrNull(strings: com.atigger.status.i18n.AppStrings): String? {
        val normalized = normalizedBaseUrlOrNull() ?: return strings.enterMonitorUrl
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return strings.invalidMonitorUrl
        val scheme = uri.scheme?.lowercase()
        if (scheme !in listOf("http", "https", "ws", "wss") || uri.host.isNullOrBlank()) {
            return strings.invalidMonitorUrl
        }
        if (requiresLogin && (username.isBlank() || password.isBlank())) {
            return strings.loginRequiresCredentials
        }
        return null
    }

    private fun buildEndpointUrl(path: String, websocket: Boolean): String {
        val uri = URI(normalizedBaseUrlOrNull() ?: "")
        val scheme = when (uri.scheme?.lowercase()) {
            "https" -> if (websocket) "wss" else "https"
            "http" -> if (websocket) "ws" else "http"
            "wss" -> if (websocket) "wss" else "https"
            "ws" -> if (websocket) "ws" else "http"
            else -> uri.scheme ?: if (websocket) "wss" else "https"
        }
        return buildString {
            append(scheme)
            append("://")
            append(uri.host)
            if (uri.port != -1) {
                append(":")
                append(uri.port)
            }
            append(path)
        }
    }

    companion object {
        private val ENDPOINT_SUFFIXES = listOf(
            "/api/v1/ws/server",
            "/api/v1/login",
            "/api/v1/server",
            "/api/v1/server-group",
            "/api/v1"
        )
    }
}

enum class AppLanguage {
    ZH,
    EN
}

enum class LiveUpdateMetric {
    CPU,
    MEMORY,
    NETWORK
}

class MonitorConfigStore(
    context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readConfig(): MonitorConfig = MonitorConfig(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        requiresLogin = preferences.getBoolean(KEY_REQUIRES_LOGIN, false),
        username = preferences.getString(KEY_USERNAME, "").orEmpty(),
        password = preferences.getString(KEY_PASSWORD, "").orEmpty(),
        language = preferences.getString(KEY_LANGUAGE, AppLanguage.ZH.name)
            ?.let { value -> AppLanguage.entries.find { it.name == value } }
            ?: AppLanguage.ZH,
        liveUpdateMetric = preferences.getString(KEY_LIVE_UPDATE_METRIC, LiveUpdateMetric.CPU.name)
            ?.let { value -> LiveUpdateMetric.entries.find { it.name == value } }
            ?: LiveUpdateMetric.CPU
    )

    fun saveConfig(config: MonitorConfig) {
        preferences.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putBoolean(KEY_REQUIRES_LOGIN, config.requiresLogin)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_LANGUAGE, config.language.name)
            .putString(KEY_LIVE_UPDATE_METRIC, config.liveUpdateMetric.name)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val KEY_BASE_URL = "monitor_base_url"
        const val KEY_REQUIRES_LOGIN = "monitor_requires_login"
        const val KEY_USERNAME = "monitor_username"
        const val KEY_PASSWORD = "monitor_password"
        const val KEY_LANGUAGE = "monitor_language"
        const val KEY_LIVE_UPDATE_METRIC = "live_update_metric"

        private const val PREFS_NAME = "status_monitor_config"
    }
}
