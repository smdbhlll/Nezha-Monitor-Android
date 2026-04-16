package com.atigger.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atigger.status.data.FavoriteServerStore

class LiveUpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_UNFOLLOW -> {
                FavoriteServerStore(context.applicationContext).saveFavoriteServerId(null)
                ServerLiveUpdateService.cancelNotification(context)
                context.stopService(Intent(context, ServerLiveUpdateService::class.java))
            }
        }
    }

    companion object {
        private const val ACTION_UNFOLLOW = "com.atigger.status.action.UNFOLLOW_LIVE_UPDATE"

        fun createUnfollowIntent(context: Context) =
            Intent(context, LiveUpdateActionReceiver::class.java).apply {
                action = ACTION_UNFOLLOW
            }
    }
}
