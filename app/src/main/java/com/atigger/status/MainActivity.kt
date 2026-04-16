package com.atigger.status

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atigger.status.data.FavoriteServerStore
import com.atigger.status.data.MonitorConfig
import com.atigger.status.data.MonitorConfigStore
import com.atigger.status.data.ServerGroupUiModel
import com.atigger.status.data.StatusRepository
import com.atigger.status.data.StatusStreamEvent
import com.atigger.status.i18n.AppStrings
import com.atigger.status.ui.NotificationPrompt
import com.atigger.status.ui.StatusLoadingStage
import com.atigger.status.ui.StatusScreen
import com.atigger.status.ui.StatusUiState
import com.atigger.status.ui.theme.StatusTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

class MainActivity : ComponentActivity() {
    private val repository = StatusRepository()
    private var uiState: StatusUiState by mutableStateOf(
        StatusUiState.Loading(stage = StatusLoadingStage.FetchingGroups)
    )
    private var favoriteServerId: Int? by mutableStateOf(null)
    private var monitorConfig: MonitorConfig by mutableStateOf(MonitorConfig())
    private var showSettings by mutableStateOf(false)
    private var settingsError: String? by mutableStateOf(null)
    private var notificationPrompt: NotificationPrompt? by mutableStateOf(null)
    private var lastBackPressedAt: Long = 0L
    private var streamJob: Job? = null

    private val favoriteServerStore by lazy { FavoriteServerStore(applicationContext) }
    private val monitorConfigStore by lazy { MonitorConfigStore(applicationContext) }
    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPrompt = null
            if (granted) syncLiveUpdateService()
        }
    private val favoriteChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == FavoriteServerStore.KEY_FAVORITE_SERVER_ID) {
            favoriteServerId = favoriteServerStore.readFavoriteServerId()
            syncLiveUpdateService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favoriteServerId = favoriteServerStore.readFavoriteServerId()
        monitorConfig = monitorConfigStore.readConfig()
        showSettings = !monitorConfig.isConfigured

        setContent {
            StatusTheme {
                StatusScreen(
                    uiState = uiState,
                    favoriteServerId = favoriteServerId,
                    monitorConfig = monitorConfig,
                    showSettings = showSettings,
                    settingsError = settingsError,
                    notificationPrompt = notificationPrompt,
                    onToggleFavorite = ::toggleFavoriteServer,
                    onRetry = ::refreshServers,
                    onOpenSettings = ::openSettings,
                    onSaveSettings = ::saveSettings,
                    onCancelSettings = ::cancelSettings,
                    onConfirmNotificationPrompt = ::confirmNotificationPrompt,
                    onDismissNotificationPrompt = ::dismissNotificationPrompt,
                    onBack = ::handleBackPress
                )
            }
        }

        if (monitorConfig.isConfigured) {
            updateNotificationPrompt()
            syncLiveUpdateService()
            refreshServers()
        } else {
            uiState = StatusUiState.SetupRequired
        }
    }

    override fun onStart() {
        super.onStart()
        favoriteServerStore.registerListener(favoriteChangeListener)
    }

    override fun onResume() {
        super.onResume()
        favoriteServerId = favoriteServerStore.readFavoriteServerId()
        monitorConfig = monitorConfigStore.readConfig()
        if (!monitorConfig.isConfigured) {
            streamJob?.cancel()
            uiState = StatusUiState.SetupRequired
            showSettings = true
            notificationPrompt = null
            stopService(ServerLiveUpdateService.createStopIntent(this))
            return
        }
        updateNotificationPrompt()
        syncLiveUpdateService()
        if (uiState is StatusUiState.SetupRequired) {
            refreshServers()
        }
    }

    override fun onStop() {
        favoriteServerStore.unregisterListener(favoriteChangeListener)
        super.onStop()
    }

    override fun onDestroy() {
        streamJob?.cancel()
        super.onDestroy()
    }

    private fun handleBackPress() {
        if (notificationPrompt != null) {
            dismissNotificationPrompt()
            return
        }
        if (showSettings && monitorConfig.isConfigured) {
            cancelSettings()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressedAt <= EXIT_CONFIRM_WINDOW_MS) {
            finish()
            return
        }

        lastBackPressedAt = now
        Toast.makeText(
            this,
            AppStrings.of(monitorConfig.language).pressBackAgainToExit,
            Toast.LENGTH_SHORT
        ).show()
    }

    private data class GroupLoadResult(
        val groups: List<ServerGroupUiModel>,
        val durationMs: Long,
        val error: Throwable? = null
    ) {
        val fallbackUsed: Boolean
            get() = error != null
    }

    private data class TokenLoadResult(
        val authToken: String?,
        val durationMs: Long
    )

    private fun refreshServers() {
        if (!monitorConfig.isConfigured) {
            streamJob?.cancel()
            uiState = StatusUiState.SetupRequired
            showSettings = true
            return
        }

        streamJob?.cancel()
        settingsError = null
        uiState = StatusUiState.Loading(
            stage = if (monitorConfig.requiresLogin) {
                StatusLoadingStage.FetchingToken
            } else {
                StatusLoadingStage.FetchingGroups
            },
            requiresLogin = monitorConfig.requiresLogin
        )
        streamJob = lifecycleScope.launch {
            val strings = AppStrings.of(monitorConfig.language)
            val tokenLoadResult = if (monitorConfig.requiresLogin) {
                runCatching { fetchAuthTokenWithRetries(strings) }.getOrElse { return@launch }
            } else {
                TokenLoadResult(authToken = null, durationMs = 0L)
            }
            val groupLoadResult = fetchGroupsWithRetries(strings, tokenLoadResult)
            val groups = groupLoadResult.groups
            val serverGroupMap = buildServerGroupMap(groups)
            observeSnapshotsWithRetries(
                strings = strings,
                authToken = tokenLoadResult.authToken,
                authDurationMs = tokenLoadResult.durationMs.takeIf { monitorConfig.requiresLogin },
                groups = groups,
                serverGroupMap = serverGroupMap,
                groupFallbackUsed = groupLoadResult.fallbackUsed,
                groupsDurationMs = groupLoadResult.durationMs
            )
        }
    }

    private suspend fun fetchAuthTokenWithRetries(strings: AppStrings): TokenLoadResult {
        val startedAt = SystemClock.elapsedRealtime()
        repeat(MAX_CONNECT_ATTEMPTS) { index ->
            val retryCount = index
            Log.d(TAG, "Fetching auth token attempt=${retryCount + 1}/$MAX_CONNECT_ATTEMPTS")
            uiState = StatusUiState.Loading(
                stage = StatusLoadingStage.FetchingToken,
                retryCount = retryCount,
                maxRetries = MAX_RETRY_COUNT,
                requiresLogin = true
            )
            try {
                return TokenLoadResult(
                    authToken = repository.fetchAuthToken(monitorConfig),
                    durationMs = SystemClock.elapsedRealtime() - startedAt
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "Fetch auth token failed on attempt=${retryCount + 1}", throwable)
                if (retryCount >= MAX_RETRY_COUNT) {
                    val message = throwable.message ?: strings.loginFailedCheckConfig
                    uiState = StatusUiState.Error(message)
                    settingsError = message
                    showSettings = true
                    throw IllegalStateException(message, throwable)
                }
                delay(RETRY_DELAY_MS)
            }
        }
        error("unreachable")
    }

    private suspend fun fetchGroupsWithRetries(
        strings: AppStrings,
        tokenLoadResult: TokenLoadResult
    ): GroupLoadResult {
        val startedAt = SystemClock.elapsedRealtime()
        var lastError: Throwable? = null
        repeat(MAX_CONNECT_ATTEMPTS) { index ->
            val retryCount = index
            Log.d(TAG, "Fetching groups attempt=${retryCount + 1}/$MAX_CONNECT_ATTEMPTS")
            uiState = StatusUiState.Loading(
                stage = StatusLoadingStage.FetchingGroups,
                retryCount = retryCount,
                maxRetries = MAX_RETRY_COUNT,
                requiresLogin = monitorConfig.requiresLogin,
                authDurationMs = tokenLoadResult.durationMs.takeIf { monitorConfig.requiresLogin }
            )
            try {
                return GroupLoadResult(
                    groups = repository.fetchServerGroups(monitorConfig, tokenLoadResult.authToken),
                    durationMs = SystemClock.elapsedRealtime() - startedAt
                )
            } catch (throwable: Throwable) {
                lastError = throwable
                Log.e(TAG, "Fetch groups failed on attempt=${retryCount + 1}", throwable)
                if (retryCount < MAX_RETRY_COUNT) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        return GroupLoadResult(
            groups = emptyList(),
            durationMs = SystemClock.elapsedRealtime() - startedAt,
            error = lastError
        )
    }

    private suspend fun observeSnapshotsWithRetries(
        strings: AppStrings,
        authToken: String?,
        authDurationMs: Long?,
        groups: List<ServerGroupUiModel>,
        serverGroupMap: Map<Int, Set<Int>>,
        groupFallbackUsed: Boolean,
        groupsDurationMs: Long
    ) {
        val wsStartedAt = SystemClock.elapsedRealtime()
        var hasReceivedSnapshot = false
        repeat(MAX_CONNECT_ATTEMPTS) { index ->
            val retryCount = index
            Log.d(
                TAG,
                "Opening websocket attempt=${retryCount + 1}/$MAX_CONNECT_ATTEMPTS, " +
                    "hasToken=${!authToken.isNullOrBlank()}, groupFallbackUsed=$groupFallbackUsed"
            )
            if (!hasReceivedSnapshot) {
                uiState = loadingState(
                    stage = StatusLoadingStage.ConnectingLiveUpdates,
                    retryCount = retryCount,
                    strings = strings,
                    authDurationMs = authDurationMs,
                    groupFallbackUsed = groupFallbackUsed,
                    groupsDurationMs = groupsDurationMs
                )
            }
            try {
                repository.observeServerSnapshots(monitorConfig, authToken).collect { event ->
                    when (event) {
                        StatusStreamEvent.Connecting -> {
                            if (!hasReceivedSnapshot) {
                                uiState = loadingState(
                                    stage = StatusLoadingStage.ConnectingLiveUpdates,
                                    retryCount = retryCount,
                                    strings = strings,
                                    authDurationMs = authDurationMs,
                                    groupFallbackUsed = groupFallbackUsed,
                                    groupsDurationMs = groupsDurationMs
                                )
                            }
                        }

                        StatusStreamEvent.WaitingForFirstSnapshot -> {
                            if (!hasReceivedSnapshot) {
                                uiState = loadingState(
                                    stage = StatusLoadingStage.WaitingForFirstSnapshot,
                                    retryCount = retryCount,
                                    strings = strings,
                                    authDurationMs = authDurationMs,
                                    groupFallbackUsed = groupFallbackUsed,
                                    groupsDurationMs = groupsDurationMs,
                                    webSocketConnectDurationMs = SystemClock.elapsedRealtime() - wsStartedAt
                                )
                            }
                        }

                        is StatusStreamEvent.SnapshotReceived -> {
                            hasReceivedSnapshot = true
                            val snapshot = event.snapshot
                            uiState = StatusUiState.Success(
                                servers = snapshot.servers.map { server ->
                                    server.copy(groupIds = serverGroupMap[server.id].orEmpty())
                                },
                                lastUpdated = snapshot.lastUpdated,
                                groups = groups
                            )
                        }
                    }
                }
                return
            } catch (throwable: Throwable) {
                Log.e(TAG, "Observe snapshots failed on attempt=${retryCount + 1}", throwable)
                if (retryCount >= MAX_RETRY_COUNT) {
                    val message = throwable.message ?: strings.openSettingsAfterConnectionFailure
                    uiState = StatusUiState.Error(message)
                    if (!hasReceivedSnapshot) {
                        settingsError = message
                        showSettings = true
                    }
                    return
                }
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun loadingState(
        stage: StatusLoadingStage,
        retryCount: Int,
        strings: AppStrings,
        authDurationMs: Long?,
        groupFallbackUsed: Boolean,
        groupsDurationMs: Long? = null,
        webSocketConnectDurationMs: Long? = null
    ): StatusUiState.Loading {
        return StatusUiState.Loading(
            stage = stage,
            retryCount = retryCount,
            maxRetries = MAX_RETRY_COUNT,
            requiresLogin = monitorConfig.requiresLogin,
            authDurationMs = authDurationMs,
            groupFallbackActive = groupFallbackUsed,
            groupsDurationMs = groupsDurationMs,
            webSocketConnectDurationMs = webSocketConnectDurationMs,
            notice = if (groupFallbackUsed) strings.loadingGroupFallbackNotice else null
        )
    }

    private fun toggleFavoriteServer(serverId: Int) {
        favoriteServerId = if (favoriteServerId == serverId) null else serverId
        favoriteServerStore.saveFavoriteServerId(favoriteServerId)
        updateNotificationPrompt()
        syncLiveUpdateService()
    }

    private fun openSettings() {
        settingsError = null
        notificationPrompt = null
        showSettings = true
    }

    private fun cancelSettings() {
        if (monitorConfig.isConfigured) {
            settingsError = null
            showSettings = false
        }
    }

    private fun saveSettings(config: MonitorConfig) {
        val error = config.validationErrorOrNull(AppStrings.of(config.language))
        if (error != null) {
            settingsError = error
            return
        }

        monitorConfigStore.saveConfig(config)
        monitorConfig = monitorConfigStore.readConfig()
        settingsError = null
        showSettings = false
        refreshServers()
        updateNotificationPrompt()
        syncLiveUpdateService()
    }

    private fun updateNotificationPrompt() {
        notificationPrompt = when {
            !monitorConfig.isConfigured || showSettings -> null
            requiresNotificationRuntimePermission() && !hasNotificationRuntimePermission() ->
                NotificationPrompt.REQUEST_PERMISSION
            !areNotificationsEnabled() -> NotificationPrompt.OPEN_SETTINGS
            else -> null
        }
    }

    private fun confirmNotificationPrompt() {
        when (notificationPrompt) {
            NotificationPrompt.REQUEST_PERMISSION -> {
                notificationPrompt = null
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            NotificationPrompt.OPEN_SETTINGS -> {
                notificationPrompt = null
                openAppSettings()
            }

            null -> Unit
        }
    }

    private fun dismissNotificationPrompt() {
        notificationPrompt = null
    }

    private fun syncLiveUpdateService() {
        val favoriteId = favoriteServerId
        if (favoriteId == null || !monitorConfig.isConfigured) {
            stopService(ServerLiveUpdateService.createStopIntent(this))
            return
        }
        if (requiresNotificationRuntimePermission() && !hasNotificationRuntimePermission()) {
            stopService(ServerLiveUpdateService.createStopIntent(this))
            return
        }
        if (!areNotificationsEnabled()) {
            stopService(ServerLiveUpdateService.createStopIntent(this))
            return
        }
        ContextCompat.startForegroundService(
            this,
            ServerLiveUpdateService.createStartIntent(this, favoriteId)
        )
    }

    private fun hasNotificationRuntimePermission(): Boolean {
        if (!requiresNotificationRuntimePermission()) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiresNotificationRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun buildServerGroupMap(groups: List<ServerGroupUiModel>): Map<Int, Set<Int>> {
        val mapping = LinkedHashMap<Int, MutableSet<Int>>()
        groups.forEach { group ->
            group.serverIds.forEach { serverId ->
                mapping.getOrPut(serverId) { linkedSetOf() }.add(group.id)
            }
        }
        return mapping
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val MAX_RETRY_COUNT = 3
        private const val MAX_CONNECT_ATTEMPTS = MAX_RETRY_COUNT + 1
        private const val RETRY_DELAY_MS = 1500L
        private const val EXIT_CONFIRM_WINDOW_MS = 2000L
    }
}
