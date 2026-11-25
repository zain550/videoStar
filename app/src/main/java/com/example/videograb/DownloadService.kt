package com.example.videograb

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Foreground service that works through the download queue one item at a time,
 * so downloads continue while the app is in the background or the screen is off.
 */
class DownloadService : Service() {

    companion object {
        const val ACTION_CANCEL = "com.example.videograb.action.CANCEL"
        const val EXTRA_ID = "download_id"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                // Could not start from the background; the item stays queued and can be retried.
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var currentId: String? = null
    @Volatile private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(getString(R.string.preparing), 0, true, null)

        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_ID)?.let { DownloadRepository.cancel(it) }
        }

        scope.launch {
            mutex.withLock { drainQueue() }
            // Only stops the service if no newer start request arrived in the meantime.
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        currentId?.let { runCatching { YoutubeDL.getInstance().destroyProcessById(it) } }
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(title: String, percent: Int, indeterminate: Boolean, cancelId: String?) {
        val n = Notifications.progress(this, title, percent, indeterminate, cancelId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.ID_FOREGROUND, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifications.ID_FOREGROUND, n)
        }
    }

    private fun showProgress(title: String, percent: Int, indeterminate: Boolean, cancelId: String?) {
        Notifications.update(this, Notifications.progress(this, title, percent, indeterminate, cancelId))
    }

    private suspend fun drainQueue() {
        while (true) {
            val next = DownloadRepository.nextQueued() ?: break
            runDownload(next)
        }
    }

    private suspend fun runDownload(item: DownloadItem) {
        val repo = DownloadRepository
        val tmpDir = File(cacheDir, "dl/${item.id}")
        currentId = item.id
        lastPercent = -1
        try {
            repo.modify(item.id) {
                it.copy(status = Status.DOWNLOADING, progress = 0f, etaSeconds = -1, error = null)
            }
            showProgress(item.title, 0, true, item.id)

            if (AppSettings.wifiOnly && !isOnWifi()) {
                throw UserFacingException(getString(R.string.err_wifi_only))
            }

            Engine.ensureInit(this)
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()

            val request = buildRequest(item, tmpDir)
            YoutubeDL.getInstance().execute(request, item.id) { progress, eta, line ->
                onProgress(item, progress, eta, line)
            }

            if (repo.get(item.id)?.status == Status.CANCELED) return

            val output = pickOutput(tmpDir) ?: throw UserFacingException(getString(R.string.err_no_output))

            repo.modify(item.id) { it.copy(status = Status.PROCESSING, progress = 1f, etaSeconds = -1) }
            showProgress(item.title, 100, true, item.id)

            val saved = try {
                MediaSaver.save(this, output, item.audioOnly)
            } catch (e: Exception) {
                throw UserFacingException(getString(R.string.err_save_failed))
            }

            if (repo.get(item.id)?.status == Status.CANCELED) {
                FileActions.deleteSaved(this, saved.uri, saved.legacyPath)
                return
            }

            repo.modify(item.id) {
                it.copy(
                    status = Status.COMPLETED, progress = 1f, etaSeconds = -1, error = null,
                    savedUri = saved.uri, legacyPath = saved.legacyPath, sizeBytes = saved.size,
                )
            }
            Notifications.done(this, item.title, true, item.id.hashCode())
        } catch (e: CancellationException) {
            if (repo.get(item.id)?.isActive == true) {
                repo.modify(item.id) { it.copy(status = Status.FAILED, error = null) }
            }
            throw e
        } catch (e: Exception) {
            if (repo.get(item.id)?.status != Status.CANCELED) {
                val message = (e as? UserFacingException)?.message ?: cleanError(e.message)
                repo.modify(item.id) { it.copy(status = Status.FAILED, error = message, etaSeconds = -1) }
                Notifications.done(this, item.title, false, item.id.hashCode())
            }
        } finally {
            currentId = null
            tmpDir.deleteRecursively()
        }
    }

    private fun buildRequest(item: DownloadItem, dir: File): YoutubeDLRequest {
        val req = YoutubeDLRequest(item.url)
        req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("--concurrent-fragments", "4")
        req.addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
        if (item.audioOnly) {
            req.addOption("-f", "ba/b")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "0")
        } else {
            req.addOption("-f", item.selector)
            req.addOption("--merge-output-format", "mp4")
        }
        return req
    }

    private fun onProgress(item: DownloadItem, progress: Float, eta: Long, line: String?) {
        val repo = DownloadRepository
        val current = repo.get(item.id) ?: return
        if (current.status == Status.CANCELED) return

        if (line != null && (line.contains("[Merger]") || line.contains("[ExtractAudio]") ||
                    line.contains("[VideoConvertor]") || line.contains("[Fixup"))
        ) {
            repo.modify(item.id, save = false) { it.copy(status = Status.PROCESSING, progress = 1f, etaSeconds = -1) }
            showProgress(item.title, 100, true, item.id)
            return
        }

        if (progress < 0) return
        // Keep the bar monotonic: video and audio streams are downloaded one after the other.
        val fraction = maxOf(current.progress, progress / 100f).coerceIn(0f, 1f)
        repo.modify(item.id, save = false) {
            it.copy(status = Status.DOWNLOADING, progress = fraction, etaSeconds = eta)
        }
        val percent = (fraction * 100).toInt()
        if (percent != lastPercent) {
            lastPercent = percent
            showProgress(item.title, percent, false, item.id)
        }
    }

    private fun pickOutput(dir: File): File? {
        val ignored = setOf("part", "ytdl", "temp", "tmp", "jpg", "jpeg", "png", "webp", "json", "vtt", "srt", "description")
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() !in ignored }
            ?.maxByOrNull { it.length() }
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
