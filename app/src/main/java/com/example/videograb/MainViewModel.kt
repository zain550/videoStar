package com.example.videograb

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface InfoState {
    data object Idle : InfoState
    data object Loading : InfoState
    data class Loaded(val meta: VideoMeta) : InfoState
    data class Error(val message: String) : InfoState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    /** 0 = home, 1 = downloads, 2 = settings */
    var tab by mutableIntStateOf(0)

    var url by mutableStateOf("")
        private set
    var infoState by mutableStateOf<InfoState>(InfoState.Idle)
        private set
    var selected by mutableStateOf<QualityOption?>(null)

    val downloads = DownloadRepository.items

    private var fetchJob: Job? = null

    fun onUrlChange(value: String) {
        url = value
    }

    /** Called when another app shares a link to us. */
    fun onSharedText(text: String?) {
        val link = extractUrl(text) ?: return
        tab = 0
        url = link
        fetch()
    }

    fun fetch() {
        val app = getApplication<Application>()
        val link = extractUrl(url)
        if (link == null) {
            infoState = InfoState.Error(app.getString(R.string.err_invalid_url))
            return
        }
        url = link
        fetchJob?.cancel()
        infoState = InfoState.Loading
        fetchJob = viewModelScope.launch {
            try {
                val meta = Engine.fetchInfo(app, link)
                selected = meta.qualities.firstOrNull()
                infoState = InfoState.Loaded(meta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                infoState = InfoState.Error(cleanError(e.message) ?: app.getString(R.string.err_fetch_failed))
            }
        }
    }

    /** Adds the selected video to the queue and starts the foreground service. */
    fun startDownload(context: Context) {
        val meta = (infoState as? InfoState.Loaded)?.meta ?: return
        val q = selected ?: return
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = meta.url,
            title = meta.title,
            thumbnail = meta.thumbnail,
            uploader = meta.uploader,
            selector = q.selector,
            audioOnly = q.audioOnly,
            qualityLabel = qualityLabel(context, q),
        )
        DownloadRepository.add(item)
        DownloadService.start(context)
        url = ""
        infoState = InfoState.Idle
        selected = null
        tab = 1
    }

    fun cancel(id: String) = DownloadRepository.cancel(id)

    fun retry(context: Context, id: String) {
        DownloadRepository.retry(id)
        DownloadService.start(context)
    }

    fun remove(context: Context, item: DownloadItem, deleteFile: Boolean) {
        if (deleteFile) FileActions.deleteSaved(context, item.savedUri, item.legacyPath)
        DownloadRepository.remove(item.id)
    }

    fun clearFinished() = DownloadRepository.clearFinished()
}
