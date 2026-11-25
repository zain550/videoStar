package com.example.videograb

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoGrabApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        DownloadRepository.init(this)
        Notifications.createChannels(this)

        appScope.launch {
            // Extract the bundled yt-dlp/Python/ffmpeg in the background on first launch.
            try {
                Engine.ensureInit(this@VideoGrabApp)
            } catch (e: Exception) {
                return@launch
            }
            maybeAutoUpdate()
        }
    }

    /** yt-dlp breaks when sites change, so refresh it at most once a day (only when idle). */
    private suspend fun maybeAutoUpdate() {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        if (!AppSettings.autoUpdate) return
        if (now - AppSettings.lastUpdateCheck < dayMs) return
        if (DownloadRepository.items.value.any { it.isActive }) return
        try {
            Engine.update(this)
            AppSettings.lastUpdateCheck = now
        } catch (e: Exception) {
            // No network or update server unreachable: try again next launch.
        }
    }
}
