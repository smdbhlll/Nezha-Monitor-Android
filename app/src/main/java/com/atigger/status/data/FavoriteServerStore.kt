package com.atigger.status.data

import android.content.Context
import android.content.SharedPreferences

class FavoriteServerStore(
    context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readFavoriteServerId(): Int? {
        val savedValue = preferences.getInt(KEY_FAVORITE_SERVER_ID, NO_FAVORITE_SERVER)
        return savedValue.takeIf { it != NO_FAVORITE_SERVER }
    }

    fun saveFavoriteServerId(serverId: Int?) {
        preferences.edit().apply {
            if (serverId == null) {
                remove(KEY_FAVORITE_SERVER_ID)
            } else {
                putInt(KEY_FAVORITE_SERVER_ID, serverId)
            }
        }.apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "status_prefs"
        const val KEY_FAVORITE_SERVER_ID = "favorite_server_id"
        private const val NO_FAVORITE_SERVER = -1
    }
}
