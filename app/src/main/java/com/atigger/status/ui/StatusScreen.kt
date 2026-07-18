package com.atigger.status.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atigger.status.R
import com.atigger.status.data.AppLanguage
import com.atigger.status.data.LiveUpdateMetric
import com.atigger.status.data.MonitorConfig
import com.atigger.status.data.ServerGroupUiModel
import com.atigger.status.data.ServerUiModel
import com.atigger.status.i18n.AppStrings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface StatusUiState {
    data class Loading(
        val stage: StatusLoadingStage,
        val retryCount: Int = 0,
        val maxRetries: Int = 3,
        val requiresLogin: Boolean = false,
        val authDurationMs: Long? = null,
        val groupFallbackActive: Boolean = false,
        val groupsDurationMs: Long? = null,
        val webSocketConnectDurationMs: Long? = null,
        val notice: String? = null
    ) : StatusUiState
    data object SetupRequired : StatusUiState
    data class Error(val message: String) : StatusUiState
    data class Success(
        val servers: List<ServerUiModel>,
        val lastUpdated: String,
        val groups: List<ServerGroupUiModel>
    ) : StatusUiState
}

enum class StatusLoadingStage {
    FetchingToken,
    FetchingGroups,
    ConnectingLiveUpdates,
    WaitingForFirstSnapshot
}

enum class NotificationPrompt {
    REQUEST_PERMISSION,
    OPEN_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    uiState: StatusUiState,
    favoriteServerId: Int?,
    monitorConfig: MonitorConfig,
    showSettings: Boolean,
    settingsError: String?,
    notificationPrompt: NotificationPrompt?,
    onToggleFavorite: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveSettings: (MonitorConfig) -> Unit,
    onCancelSettings: () -> Unit,
    onConfirmNotificationPrompt: () -> Unit,
    onDismissNotificationPrompt: () -> Unit,
    onBack: () -> Unit
) {
    val strings = AppStrings.of(monitorConfig.language)
    val shouldShowSettings = showSettings || !monitorConfig.isConfigured

    BackHandler(onBack = onBack)

    notificationPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = onDismissNotificationPrompt,
            title = { Text(strings.notificationPermissionTitle) },
            text = {
                Text(
                    if (prompt == NotificationPrompt.REQUEST_PERMISSION) {
                        strings.notificationPermissionMessage
                    } else {
                        strings.notificationSettingsMessage
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmNotificationPrompt) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissNotificationPrompt) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (shouldShowSettings) {
        SettingsScaffold(
            strings = strings,
            monitorConfig = monitorConfig,
            errorMessage = settingsError,
            canCancel = monitorConfig.isConfigured,
            onSaveSettings = onSaveSettings,
            onCancelSettings = onCancelSettings
        )
        return
    }

    var showOnlineOnly by rememberSaveable { mutableStateOf(false) }
    var selectedGroupId by rememberSaveable { mutableStateOf<Int?>(null) }
    val favoriteServer = (uiState as? StatusUiState.Success)
        ?.servers
        ?.find { it.id == favoriteServerId }
    val groups = (uiState as? StatusUiState.Success)?.groups.orEmpty()
    val selectedTabIndex = groups.indexOfFirst { it.id == selectedGroupId }
        .takeIf { it >= 0 }
        ?.plus(1)
        ?: 0
    LaunchedEffect(groups) {
        if (selectedGroupId != null && groups.none { it.id == selectedGroupId }) {
            selectedGroupId = null
        }
    }
    val state = when (uiState) {
        is StatusUiState.Success -> uiState.copy(
            servers = uiState.servers
                .let { servers -> if (showOnlineOnly) servers.filter { it.isOnline } else servers }
        )
        else -> uiState
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(strings.appTitle, fontWeight = FontWeight.SemiBold)
                            if (state is StatusUiState.Success) {
                                Text(
                                    text = strings.updated(state.lastUpdated),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = showOnlineOnly,
                            onClick = { showOnlineOnly = !showOnlineOnly },
                            label = { Text(strings.online) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        IconButton(onClick = onRetry) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = strings.refresh
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = strings.settings
                            )
                        }
                    }
                )
                if (state is StatusUiState.Success && state.groups.isNotEmpty()) {
                    GroupTabs(
                        groups = state.groups,
                        selectedTabIndex = selectedTabIndex,
                        onSelectGroup = { selectedGroupId = it },
                        strings = strings
                    )
                }
            }
        }
    ) { innerPadding ->
        when (state) {
            is StatusUiState.Loading -> LoadingPane(innerPadding, state, strings)
            StatusUiState.SetupRequired -> SetupHintPane(innerPadding, onOpenSettings, strings)
            is StatusUiState.Error -> ErrorPane(innerPadding, state.message, onRetry, strings)
            is StatusUiState.Success -> SwipeableServerListPane(
                innerPadding = innerPadding,
                servers = state.servers,
                groups = state.groups,
                selectedGroupId = selectedGroupId,
                lastUpdated = state.lastUpdated,
                favoriteServer = favoriteServer,
                favoriteServerId = favoriteServerId,
                onToggleFavorite = onToggleFavorite,
                onRetry = onRetry,
                onSelectGroup = { selectedGroupId = it },
                strings = strings
            )
        }
    }
}

@Composable
private fun SwipeableServerListPane(
    innerPadding: PaddingValues,
    servers: List<ServerUiModel>,
    groups: List<ServerGroupUiModel>,
    selectedGroupId: Int?,
    lastUpdated: String,
    favoriteServer: ServerUiModel?,
    favoriteServerId: Int?,
    onToggleFavorite: (Int) -> Unit,
    onRetry: () -> Unit,
    onSelectGroup: (Int?) -> Unit,
    strings: AppStrings
) {
    val coroutineScope = rememberCoroutineScope()
    var dragOffsetPx by rememberSaveable(selectedGroupId, groups) { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        val currentIndex = groups.indexOfFirst { it.id == selectedGroupId } + 1
        val adjacentIndex = when {
            dragOffsetPx < 0f && currentIndex < groups.size -> currentIndex + 1
            dragOffsetPx > 0f && currentIndex > 0 -> currentIndex - 1
            else -> null
        }
        val swipeModifier = if (groups.isNotEmpty() && pageWidthPx > 0f) {
            Modifier.pointerInput(groups, selectedGroupId, pageWidthPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val proposedOffset = dragOffsetPx + dragAmount
                        dragOffsetPx = when {
                            proposedOffset < 0f && currentIndex >= groups.size -> 0f
                            proposedOffset > 0f && currentIndex <= 0 -> 0f
                            else -> proposedOffset.coerceIn(-pageWidthPx, pageWidthPx)
                        }
                    },
                    onDragEnd = {
                        val threshold = pageWidthPx * 0.18f
                        val targetIndex = when {
                            dragOffsetPx <= -threshold && currentIndex < groups.size -> currentIndex + 1
                            dragOffsetPx >= threshold && currentIndex > 0 -> currentIndex - 1
                            else -> currentIndex
                        }
                        coroutineScope.launch {
                            val targetOffset = when {
                                targetIndex > currentIndex -> -pageWidthPx
                                targetIndex < currentIndex -> pageWidthPx
                                else -> 0f
                            }
                            animateOffset(from = dragOffsetPx, to = targetOffset) { value ->
                                dragOffsetPx = value
                            }
                            if (targetIndex != currentIndex) {
                                onSelectGroup(groups.getOrNull(targetIndex - 1)?.id)
                            }
                            dragOffsetPx = 0f
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animateOffset(from = dragOffsetPx, to = 0f) { value ->
                                dragOffsetPx = value
                            }
                            dragOffsetPx = 0f
                        }
                    }
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(swipeModifier)
        ) {
            adjacentIndex?.let { index ->
                val adjacentGroupId = groups.getOrNull(index - 1)?.id
                val adjacentOffset = if (dragOffsetPx < 0f) {
                    pageWidthPx + dragOffsetPx
                } else {
                    -pageWidthPx + dragOffsetPx
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(adjacentOffset.roundToInt(), 0) }
                ) {
                    ServerListPane(
                        innerPadding = innerPadding,
                        servers = servers,
                        groups = groups,
                        selectedGroupId = adjacentGroupId,
                        lastUpdated = lastUpdated,
                        favoriteServer = favoriteServer,
                        favoriteServerId = favoriteServerId,
                        onToggleFavorite = onToggleFavorite,
                        onRetry = onRetry,
                        strings = strings
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
            ) {
                ServerListPane(
                    innerPadding = innerPadding,
                    servers = servers,
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    lastUpdated = lastUpdated,
                    favoriteServer = favoriteServer,
                    favoriteServerId = favoriteServerId,
                    onToggleFavorite = onToggleFavorite,
                    onRetry = onRetry,
                    strings = strings
                )
            }
        }
    }
}

private suspend fun animateOffset(
    from: Float,
    to: Float,
    onUpdate: (Float) -> Unit
) {
    animate(
        initialValue = from,
        targetValue = to,
        animationSpec = tween(durationMillis = 220)
    ) { value, _ ->
        onUpdate(value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    strings: AppStrings,
    monitorConfig: MonitorConfig,
    errorMessage: String?,
    canCancel: Boolean,
    onSaveSettings: (MonitorConfig) -> Unit,
    onCancelSettings: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text(strings.monitorSettings, fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (canCancel) {
                        TextButton(onClick = onCancelSettings) {
                            Text(strings.cancel)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        MonitorConfigPane(
            strings = strings,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            initialConfig = monitorConfig,
            errorMessage = errorMessage,
            onSaveSettings = onSaveSettings
        )
    }
}

@Composable
private fun MonitorConfigPane(
    strings: AppStrings,
    modifier: Modifier = Modifier,
    initialConfig: MonitorConfig,
    errorMessage: String?,
    onSaveSettings: (MonitorConfig) -> Unit
) {
    val initialUrlDraft = splitMonitorUrlDraft(initialConfig.baseUrl)
    var baseUrlScheme by rememberSaveable(initialConfig.baseUrl) { mutableStateOf(initialUrlDraft.scheme) }
    var baseUrlAddress by rememberSaveable(initialConfig.baseUrl) { mutableStateOf(initialUrlDraft.address) }
    var requiresLogin by rememberSaveable(initialConfig.requiresLogin) { mutableStateOf(initialConfig.requiresLogin) }
    var username by rememberSaveable(initialConfig.username) { mutableStateOf(initialConfig.username) }
    var password by rememberSaveable(initialConfig.password) { mutableStateOf(initialConfig.password) }
    var languageName by rememberSaveable(initialConfig.language.name) { mutableStateOf(initialConfig.language.name) }
    var baseUrlSchemeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var liveUpdateMetricName by rememberSaveable(initialConfig.liveUpdateMetric.name) {
        mutableStateOf(initialConfig.liveUpdateMetric.name)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = strings.connection,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.connectionHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { baseUrlSchemeMenuExpanded = true },
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("$baseUrlScheme://")
                        }
                        DropdownMenu(
                            expanded = baseUrlSchemeMenuExpanded,
                            onDismissRequest = { baseUrlSchemeMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("https://") },
                                onClick = {
                                    baseUrlScheme = "https"
                                    baseUrlSchemeMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("http://") },
                                onClick = {
                                    baseUrlScheme = "http"
                                    baseUrlSchemeMenuExpanded = false
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = baseUrlAddress,
                        onValueChange = { baseUrlAddress = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(strings.monitorUrl) },
                        placeholder = { Text("nezha.example.com") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.languageLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.languageHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = languageName == AppLanguage.ZH.name,
                        onClick = { languageName = AppLanguage.ZH.name },
                        label = { Text(strings.chinese) }
                    )
                    FilterChip(
                        selected = languageName == AppLanguage.EN.name,
                        onClick = { languageName = AppLanguage.EN.name },
                        label = { Text(strings.english) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.liveUpdateDisplayLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.liveUpdateDisplayHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = liveUpdateMetricName == LiveUpdateMetric.CPU.name,
                            onClick = { liveUpdateMetricName = LiveUpdateMetric.CPU.name },
                            label = { Text(strings.metricCpu) }
                        )
                        FilterChip(
                            selected = liveUpdateMetricName == LiveUpdateMetric.MEMORY.name,
                            onClick = { liveUpdateMetricName = LiveUpdateMetric.MEMORY.name },
                            label = { Text(strings.metricMemory) }
                        )
                    }
                    FilterChip(
                        selected = liveUpdateMetricName == LiveUpdateMetric.NETWORK.name,
                        onClick = { liveUpdateMetricName = LiveUpdateMetric.NETWORK.name },
                        label = { Text(strings.metricNetwork) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.requireLogin,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Switch(
                        checked = requiresLogin,
                        onCheckedChange = { requiresLogin = it }
                    )
                }

                if (requiresLogin) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(strings.username) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(strings.password) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = {
                        onSaveSettings(
                            MonitorConfig(
                                baseUrl = buildMonitorUrl(baseUrlScheme, baseUrlAddress),
                                requiresLogin = requiresLogin,
                                username = username.trim(),
                                password = password,
                                language = AppLanguage.valueOf(languageName),
                                liveUpdateMetric = LiveUpdateMetric.valueOf(liveUpdateMetricName)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.saveConfig)
                }
            }
        }
    }
}

private data class MonitorUrlDraft(
    val scheme: String,
    val address: String
)

private fun splitMonitorUrlDraft(baseUrl: String): MonitorUrlDraft {
    val trimmed = baseUrl.trim()
    val lowercased = trimmed.lowercase()
    return when {
        lowercased.startsWith("https://") -> MonitorUrlDraft("https", trimmed.substring(8))
        lowercased.startsWith("http://") -> MonitorUrlDraft("http", trimmed.substring(7))
        else -> MonitorUrlDraft("https", trimmed)
    }
}

private fun buildMonitorUrl(scheme: String, address: String): String {
    val trimmed = address.trim()
    if (trimmed.isBlank()) return ""
    val normalizedAddress = when {
        trimmed.lowercase().startsWith("https://") -> trimmed.substring(8)
        trimmed.lowercase().startsWith("http://") -> trimmed.substring(7)
        else -> trimmed
    }
    return "$scheme://$normalizedAddress"
}

@Composable
private fun LoadingPane(
    innerPadding: PaddingValues,
    state: StatusUiState.Loading,
    strings: AppStrings
) {
    val stage = state.stage
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stage.title(strings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stage.detail(strings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.retryCount > 0) {
                Text(
                    text = strings.loadingRetry(state.retryCount, state.maxRetries),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!state.notice.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = state.notice,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (state.requiresLogin) {
                        LoadingStepRow(
                            label = buildStepLabel(strings.loadingStepFetchingToken, state.authDurationMs),
                            state = when (stage) {
                                StatusLoadingStage.FetchingToken -> LoadingStepState.Active
                                StatusLoadingStage.FetchingGroups,
                                StatusLoadingStage.ConnectingLiveUpdates,
                                StatusLoadingStage.WaitingForFirstSnapshot -> LoadingStepState.Done
                            },
                            strings = strings
                        )
                    }
                    LoadingStepRow(
                        label = buildStepLabel(strings.loadingStepFetchingGroups, state.groupsDurationMs),
                        state = when (stage) {
                            StatusLoadingStage.FetchingToken -> LoadingStepState.Pending
                            StatusLoadingStage.FetchingGroups -> LoadingStepState.Active
                            StatusLoadingStage.ConnectingLiveUpdates,
                            StatusLoadingStage.WaitingForFirstSnapshot -> {
                                if (state.groupFallbackActive) LoadingStepState.Fallback
                                else LoadingStepState.Done
                            }
                        },
                        strings = strings
                    )
                    LoadingStepRow(
                        label = buildStepLabel(
                            strings.loadingStepConnectingWs,
                            state.webSocketConnectDurationMs
                        ),
                        state = when (stage) {
                            StatusLoadingStage.FetchingToken,
                            StatusLoadingStage.FetchingGroups -> LoadingStepState.Pending
                            StatusLoadingStage.ConnectingLiveUpdates -> LoadingStepState.Active
                            StatusLoadingStage.WaitingForFirstSnapshot -> LoadingStepState.Done
                        },
                        strings = strings
                    )
                    LoadingStepRow(
                        label = strings.loadingStepWaitingFirstFrame,
                        state = when (stage) {
                            StatusLoadingStage.WaitingForFirstSnapshot -> LoadingStepState.Active
                            StatusLoadingStage.FetchingToken,
                            StatusLoadingStage.FetchingGroups,
                            StatusLoadingStage.ConnectingLiveUpdates -> LoadingStepState.Pending
                        },
                        strings = strings
                    )
                }
            }
        }
    }
}

private enum class LoadingStepState {
    Done,
    Active,
    Pending,
    Fallback
}

@Composable
private fun LoadingStepRow(
    label: String,
    state: LoadingStepState,
    strings: AppStrings
) {
    val color = when (state) {
        LoadingStepState.Done -> Color(0xFF1B8A5A)
        LoadingStepState.Active -> MaterialTheme.colorScheme.primary
        LoadingStepState.Pending -> MaterialTheme.colorScheme.outline
        LoadingStepState.Fallback -> Color(0xFFB26A00)
    }
    val statusText = when (state) {
        LoadingStepState.Done -> strings.loadingStatusDone
        LoadingStepState.Active -> strings.loadingStatusActive
        LoadingStepState.Pending -> strings.loadingStatusPending
        LoadingStepState.Fallback -> strings.loadingStatusFallback
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

private fun buildStepLabel(label: String, durationMs: Long?): String {
    val durationText = durationMs?.let(::formatStepDuration) ?: return label
    return "$label  $durationText"
}

private fun formatStepDuration(durationMs: Long): String {
    return when {
        durationMs < 1000 -> "${durationMs}ms"
        durationMs < 10_000 -> String.format("%.1fs", durationMs / 1000.0)
        else -> "${durationMs / 1000}s"
    }
}

private fun StatusLoadingStage.title(strings: AppStrings): String = when (this) {
    StatusLoadingStage.FetchingToken -> strings.loadingStageFetchingTokenTitle
    StatusLoadingStage.FetchingGroups -> strings.loadingStageFetchingGroupsTitle
    StatusLoadingStage.ConnectingLiveUpdates -> strings.loadingStageConnectingWsTitle
    StatusLoadingStage.WaitingForFirstSnapshot -> strings.loadingStageWaitingFirstFrameTitle
}

private fun StatusLoadingStage.detail(strings: AppStrings): String = when (this) {
    StatusLoadingStage.FetchingToken -> strings.loadingStageFetchingTokenDetail
    StatusLoadingStage.FetchingGroups -> strings.loadingStageFetchingGroupsDetail
    StatusLoadingStage.ConnectingLiveUpdates -> strings.loadingStageConnectingWsDetail
    StatusLoadingStage.WaitingForFirstSnapshot -> strings.loadingStageWaitingFirstFrameDetail
}

@Composable
private fun SetupHintPane(innerPadding: PaddingValues, onOpenSettings: () -> Unit, strings: AppStrings) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(strings.noMonitorConfig, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = strings.setupHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onOpenSettings) {
                Text(strings.openSettings)
            }
        }
    }
}

@Composable
private fun ErrorPane(
    innerPadding: PaddingValues,
    message: String,
    onRetry: () -> Unit,
    strings: AppStrings
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(strings.loadFailed, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onRetry) {
                Text(strings.retry)
            }
        }
    }
}

@Composable
private fun ServerListPane(
    innerPadding: PaddingValues,
    servers: List<ServerUiModel>,
    groups: List<ServerGroupUiModel>,
    selectedGroupId: Int?,
    lastUpdated: String,
    favoriteServer: ServerUiModel?,
    favoriteServerId: Int?,
    onToggleFavorite: (Int) -> Unit,
    onRetry: () -> Unit,
    strings: AppStrings
) {
    val visibleServers = selectedGroupId
        ?.let { groupId -> servers.filter { groupId in it.groupIds } }
        ?: servers

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (selectedGroupId == null) {
            item {
                LiveUpdateCard(
                    favoriteServer = favoriteServer,
                    favoriteServerId = favoriteServerId,
                    lastUpdated = lastUpdated,
                    onToggleFavorite = onToggleFavorite,
                    strings = strings
                )
            }
        }
        item {
            Text(
                text = strings.nodes(visibleServers.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (visibleServers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = strings.noNodes,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = onRetry) {
                            Text(strings.reconnect)
                        }
                    }
                }
            }
        } else if (groups.isNotEmpty()) {
            val visibleGroups = groups.filter { group ->
                selectedGroupId == null || selectedGroupId == group.id
            }
            visibleGroups.forEach { group ->
                val groupServers = visibleServers.filter { server -> group.id in server.groupIds }
                if (groupServers.isNotEmpty()) {
                    item(key = "group-${group.id}") {
                        GroupHeader(group.name)
                    }
                    items(groupServers, key = { "${group.id}-${it.id}" }) { server ->
                        ServerCard(
                            server = server,
                            isFavorite = favoriteServerId == server.id,
                            onToggleFavorite = onToggleFavorite,
                            strings = strings
                        )
                    }
                }
            }

            val ungroupedServers = visibleServers.filter { it.groupIds.isEmpty() }
            if (selectedGroupId == null && ungroupedServers.isNotEmpty()) {
                item(key = "group-ungrouped") {
                    GroupHeader(strings.ungrouped)
                }
                items(ungroupedServers, key = { "ungrouped-${it.id}" }) { server ->
                    ServerCard(
                        server = server,
                        isFavorite = favoriteServerId == server.id,
                        onToggleFavorite = onToggleFavorite,
                        strings = strings
                    )
                }
            }
        } else {
            items(visibleServers, key = { it.id }) { server ->
                ServerCard(
                    server = server,
                    isFavorite = favoriteServerId == server.id,
                    onToggleFavorite = onToggleFavorite,
                    strings = strings
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun LiveUpdateCard(
    favoriteServer: ServerUiModel?,
    favoriteServerId: Int?,
    lastUpdated: String,
    onToggleFavorite: (Int) -> Unit,
    strings: AppStrings
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = strings.followedNode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (favoriteServer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = favoriteServer.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.updated(lastUpdated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusBadge(
                            favoriteServer.isOnline,
                            if (favoriteServer.isOnline) strings.onlineStatus else strings.offlineStatus
                        )
                        TextButton(onClick = { onToggleFavorite(favoriteServer.id) }) {
                            Text(strings.unfollow)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                ResourceGrid(
                    cpuPercent = favoriteServer.cpuPercent,
                    memoryPercent = favoriteServer.memoryPercent,
                    diskPercent = favoriteServer.diskPercent
                )
                Spacer(modifier = Modifier.height(8.dp))
                SpeedIndicator(
                    downloadSpeed = favoriteServer.netInSpeed,
                    uploadSpeed = favoriteServer.netOutSpeed
                )
                Spacer(modifier = Modifier.height(8.dp))
                NetworkTrafficRow(
                    netInTransfer = favoriteServer.netInTransfer,
                    netOutTransfer = favoriteServer.netOutTransfer
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConnectionRow(
                    tcp = favoriteServer.tcpConnCount,
                    udp = favoriteServer.udpConnCount,
                    process = favoriteServer.processCount
                )
                favoriteServer.uptimeLine?.let { InfoLine(strings.uptime, it) }
                favoriteServer.ipLine?.let { InfoLine(strings.ip, it) }
            } else {
                Text(
                    text = if (favoriteServerId == null) strings.followHint else strings.followMissingHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GroupTabs(
    groups: List<ServerGroupUiModel>,
    selectedTabIndex: Int,
    onSelectGroup: (Int?) -> Unit,
    strings: AppStrings
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 12.dp
    ) {
        Tab(
            selected = selectedTabIndex == 0,
            onClick = { onSelectGroup(null) },
            text = { Text(strings.allGroups) }
        )
        groups.forEachIndexed { index, group ->
            Tab(
                selected = selectedTabIndex == index + 1,
                onClick = { onSelectGroup(group.id) },
                text = { Text(group.name) }
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: ServerUiModel,
    isFavorite: Boolean,
    onToggleFavorite: (Int) -> Unit,
    strings: AppStrings
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = server.platformLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(
                        server.isOnline,
                        if (server.isOnline) strings.onlineStatus else strings.offlineStatus
                    )
                    TextButton(onClick = { onToggleFavorite(server.id) }) {
                        Text(if (isFavorite) strings.followed else strings.follow)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ResourceGrid(
                cpuPercent = server.cpuPercent,
                memoryPercent = server.memoryPercent,
                diskPercent = server.diskPercent
            )
            Spacer(modifier = Modifier.height(8.dp))
            SpeedIndicator(
                downloadSpeed = server.netInSpeed,
                uploadSpeed = server.netOutSpeed
            )
            Spacer(modifier = Modifier.height(8.dp))
            NetworkTrafficRow(
                netInTransfer = server.netInTransfer,
                netOutTransfer = server.netOutTransfer
            )
            Spacer(modifier = Modifier.height(8.dp))
            ConnectionRow(
                tcp = server.tcpConnCount,
                udp = server.udpConnCount,
                process = server.processCount
            )
            server.planLine?.let { InfoLine(strings.plan, it) }
            server.billingLine?.let { InfoLine(strings.billing, it) }
            server.ipLine?.let { InfoLine(strings.ip, it) }
            server.uptimeLine?.let { InfoLine(strings.uptime, it) }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                server.locationTag?.let { TagChip(it) }
                server.versionTag?.let { TagChip(strings.agentVersion(it)) }
            }
        }
    }
}

@Composable
private fun StatusBadge(isOnline: Boolean, text: String) {
    val color = if (isOnline) Color(0xFF1B8A5A) else Color(0xFFB3261E)
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
