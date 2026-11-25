package com.example.videograb

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppSettings {
    private lateinit var prefs: SharedPreferences

    var wifiOnly by mutableStateOf(false)
        private set
    var autoUpdate by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        wifiOnly = prefs.getBoolean("wifi_only", false)
        autoUpdate = prefs.getBoolean("auto_update", true)
    }

    fun updateWifiOnly(value: Boolean) {
        wifiOnly = value
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean("wifi_only", value).apply()
        }
    }

    fun updateAutoUpdate(value: Boolean) {
        autoUpdate = value
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean("auto_update", value).apply()
        }
    }

    var lastUpdateCheck: Long
        get() = if (::prefs.isInitialized) prefs.getLong("last_update_check", 0L) else 0L
        set(value) {
            if (::prefs.isInitialized) {
                prefs.edit().putLong("last_update_check", value).apply()
            }
        }
}
