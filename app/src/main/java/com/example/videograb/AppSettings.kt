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

    fun setWifiOnly(value: Boolean) {
        wifiOnly = value
        prefs.edit().putBoolean("wifi_only", value).apply()
    }

    fun setAutoUpdate(value: Boolean) {
        autoUpdate = value
        prefs.edit().putBoolean("auto_update", value).apply()
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) {
            prefs.edit().putLong("last_update_check", value).apply()
        }
}
