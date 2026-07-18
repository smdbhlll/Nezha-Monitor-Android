package com.atigger.status

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atigger.status.data.FavoriteServerStore
import com.atigger.status.data.MonitorConfig
import com.atigger.status.data.MonitorConfigStore
import com.atigger.status.data.ServerUiModel
import com.atigger.status.data.StatusRepository
import com.atigger.status.data.StatusStreamEvent
import com.atigger.status.data.criticalStatusText
import com.atigger.status.data.formatBytes
import com.atigger.status.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class ServerLiveUpdateService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = StatusRepository()
    private lateinit var favoriteServerStore: FavoriteServerStore
    private lateinit var monitorConfigStore: MonitorConfigStore
    private var streamJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        favoriteServerStore = FavoriteServerStore(applicationContext)
        monitorConfigStore = MonitorConfigStore(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                favoriteServerStore.saveFavoriteServerId(null)
                stopStreaming()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                val monitorConfig = monitorConfigStore.readConfig()
                val strings = AppStrings.of(monitorConfig.language)
                if (!monitorConfig.isConfigured) {
                    stopStreaming()
                    return START_NOT_STICKY
                }

                val requestedServerId = intent?.getIntExtra(EXTRA_SERVER_ID, NO_SERVER_ID)
                    ?.takeIf { it != NO_SERVER_ID }
                    ?: favoriteServerStore.readFavoriteServerId()
                if (requestedServerId == null) {
                    stopStreaming()
                    return START_NOT_STICKY
                }

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = strings.liveUpdate,
                        content = strings.connectingFollowedNode(requestedServerId),
                        detail = strings.connectingDetail,
                        actionText = strings.unfollow,
                        shortCriticalText = null
                    )
                )
                beginStreaming(monitorConfig, requestedServerId, strings)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        streamJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginStreaming(config: MonitorConfig, serverId: Int, strings: AppStrings) {
        streamJob?.cancel()
        streamJob = serviceScope.launch {
            var lastError: Throwable? = null
            repeat(MAX_CONNECT_ATTEMPTS) { index ->
                try {
                    repository.observeServerSnapshots(config).collectLatest { event ->
                        when (event) {
                            StatusStreamEvent.Connecting,
                            StatusStreamEvent.WaitingForFirstSnapshot -> Unit

                            is StatusStreamEvent.SnapshotReceived -> {
                                val snapshot = event.snapshot
                                val server = snapshot.servers.find { it.id == serverId }
                                val notification = if (server != null) {
                                    buildServerNotification(server, snapshot.lastUpdated, config, strings)
                                } else {
                                    buildNotification(
                                        title = strings.liveUpdate,
                                        content = strings.waitingForFollowedNode(serverId),
                                        detail = strings.latestFrame(snapshot.lastUpdated),
                                        actionText = strings.unfollow,
                                        shortCriticalText = null
                                    )
                                }
                                NotificationManagerCompat.from(this@ServerLiveUpdateService)
                                    .notify(NOTIFICATION_ID, notification)
                            }
                        }
                    }
                    return@launch
                } catch (throwable: Throwable) {
                    lastError = throwable
                    if (index < MAX_RETRY_COUNT) {
                        delay(RETRY_DELAY_MS)
                    }
                }
            }
            NotificationManagerCompat.from(this@ServerLiveUpdateService).notify(
                NOTIFICATION_ID,
                buildNotification(
                    title = strings.liveUpdate,
                    content = strings.liveUpdateConnectionFailed,
                    detail = lastError?.message ?: strings.openSettingsAfterConnectionFailure,
                    actionText = strings.unfollow,
                    shortCriticalText = null
                )
            )
        }
    }

    private fun buildServerNotification(
        server: ServerUiModel,
        lastUpdated: String,
        config: MonitorConfig,
        strings: AppStrings
    ): Notification {
        val customView = RemoteViews(packageName, R.layout.notification_live_update).apply {
            // Status dot color
            val dotColor = if (server.isOnline) 0xFF1B8A5A.toInt() else 0xFFB3261E.toInt()
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            setImageViewBitmap(R.id.status_dot, drawableToBitmap(dot))

            // Server name & status
            setTextViewText(R.id.tv_server_name, server.name)
            setTextViewText(R.id.tv_status,
                if (server.isOnline) strings.onlineStatus else strings.offlineStatus)
            setTextColor(R.id.tv_status, dotColor)

            // CPU gauge
            val cpuVal = server.cpuPercent?.toInt() ?: 0
            setProgressBar(R.id.pb_cpu, 100, cpuVal, false)
            setTextViewText(R.id.tv_cpu,
                server.cpuPercent?.let { "${"%.1f".format(Locale.US, it)}%" } ?: "--")

            // Memory gauge
            val memVal = server.memoryPercent?.toInt() ?: 0
            setProgressBar(R.id.pb_mem, 100, memVal, false)
            setTextViewText(R.id.tv_mem,
                server.memoryPercent?.let { "${"%.1f".format(Locale.US, it)}%" } ?: "--")

            // Disk gauge
            val diskVal = server.diskPercent?.toInt() ?: 0
            setProgressBar(R.id.pb_disk, 100, diskVal, false)
            setTextViewText(R.id.tv_disk,
                server.diskPercent?.let { "${"%.1f".format(Locale.US, it)}%" } ?: "--")

            // Network speed
            val dlText = server.netInSpeed?.let { "▼ ${formatBytes(it)}/s" } ?: "▼ --"
            val ulText = server.netOutSpeed?.let { "▲ ${formatBytes(it)}/s" } ?: "▲ --"
            setTextViewText(R.id.tv_net_down, dlText)
            setTextViewText(R.id.tv_net_up, ulText)
            setTextColor(R.id.tv_net_down, 0xFF1976D2.toInt())
            setTextColor(R.id.tv_net_up, 0xFFE65100.toInt())

            // Updated time
            setTextViewText(R.id.tv_updated, strings.updatedAt(lastUpdated))
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(createOpenAppPendingIntent())
            .setDeleteIntent(createStopPendingIntent())
            .addAction(0, strings.unfollow, createStopPendingIntent())
            .setRequestPromotedOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT < 36) return notification

        val shortText = server.criticalStatusText(config.liveUpdateMetric, strings)
        return Notification.Builder.recoverBuilder(this, notification)
            .setShortCriticalText(shortText)
            .build()
    }

    private fun drawableToBitmap(drawable: GradientDrawable): android.graphics.Bitmap {
        val size = (8 * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun buildNotification(
        title: String,
        content: String,
        detail: String,
        actionText: String,
        shortCriticalText: String?
    ): Notification {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(createOpenAppPendingIntent())
            .setDeleteIntent(createStopPendingIntent())
            .addAction(0, actionText, createStopPendingIntent())
            .setRequestPromotedOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT < 36 || shortCriticalText.isNullOrBlank()) {
            return notification
        }

        return Notification.Builder.recoverBuilder(this, notification)
            .setShortCriticalText(shortCriticalText)
            .build()
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createStopPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            REQUEST_STOP,
            LiveUpdateActionReceiver.createUnfollowIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val strings = AppStrings.of(monitorConfigStore.readConfig().language)
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.liveUpdateChannelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = strings.channelDescription
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_START = "com.atigger.status.action.START_LIVE_UPDATE"
        private const val ACTION_STOP = "com.atigger.status.action.STOP_LIVE_UPDATE"
        private const val EXTRA_SERVER_ID = "extra_server_id"
        private const val CHANNEL_ID = "status_live_update"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 1002
        private const val REQUEST_STOP = 1003
        private const val NO_SERVER_ID = -1
        private const val MAX_RETRY_COUNT = 3
        private const val MAX_CONNECT_ATTEMPTS = MAX_RETRY_COUNT + 1
        private const val RETRY_DELAY_MS = 1500L

        fun createStartIntent(context: Context, serverId: Int) =
            Intent(context, ServerLiveUpdateService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_ID, serverId)
            }

        fun createStopIntent(context: Context) =
            Intent(context, ServerLiveUpdateService::class.java).apply {
                action = ACTION_STOP
            }

        fun cancelNotification(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}
