package com.example.videograb

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

/** Single source of truth for the downloads list. Persisted in filesDir/downloads.json. */
object DownloadRepository {
    private lateinit var file: File
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "downloads.json")
        val loaded = load().map {
            // Anything that was running when the app died is marked as failed (can be retried).
            if (it.isActive) it.copy(status = Status.FAILED, error = null, etaSeconds = -1) else it
        }
        _items.value = loaded
        persist()
    }

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun add(item: DownloadItem) {
        _items.update { listOf(item) + it }
        persist()
    }

    fun modify(id: String, save: Boolean = true, block: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) block(it) else it } }
        if (save) persist()
    }

    fun remove(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.isActive } }
        persist()
    }

    /** Oldest queued item first. */
    fun nextQueued(): DownloadItem? = _items.value.lastOrNull { it.status == Status.QUEUED }

    fun cancel(id: String) {
        val current = get(id) ?: return
        if (!current.isActive) return
        modify(id) { it.copy(status = Status.CANCELED, error = null, etaSeconds = -1) }
        if (current.status == Status.DOWNLOADING || current.status == Status.PROCESSING) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        }
    }

    fun retry(id: String) {
        modify(id) {
            it.copy(status = Status.QUEUED, progress = 0f, etaSeconds = -1, error = null,
                savedUri = null, legacyPath = null)
        }
    }

    private fun load(): List<DownloadItem> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { DownloadItem.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun persist() {
        ioScope.launch {
            writeMutex.withLock {
                runCatching {
                    val arr = JSONArray()
                    _items.value.forEach { arr.put(it.toJson()) }
                    val tmp = File(file.parentFile, "downloads.json.tmp")
                    tmp.writeText(arr.toString())
                    if (!tmp.renameTo(file)) {
                        file.writeText(arr.toString())
                        tmp.delete()
                    }
                }
            }
        }
    }
}
