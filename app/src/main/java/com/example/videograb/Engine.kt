package com.example.videograb

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class UpdateResult { UPDATED, UP_TO_DATE, UNKNOWN }

/** Thin wrapper around yt-dlp (via youtubedl-android). */
object Engine {
    private val mutex = Mutex()
    @Volatile
    private var ready = false

    /** Extracts Python/yt-dlp/ffmpeg on first run. Safe to call many times. */
    suspend fun ensureInit(context: Context) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
            }
            ready = true
        }
    }

    /** Reads video info (title, thumbnail, available qualities) without downloading. */
    suspend fun fetchInfo(context: Context, inputUrl: String): VideoMeta = withContext(Dispatchers.IO) {
        ensureInit(context)
        val request = YoutubeDLRequest(inputUrl).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--playlist-items", "1")
            addOption("--no-warnings")
            addOption("--skip-download")
        }
        val out = YoutubeDL.getInstance().execute(request).out
        parse(inputUrl, out ?: "")
    }

    suspend fun update(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        ensureInit(context)
        val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
        val text = status?.toString().orEmpty()
        when {
            text.contains("ALREADY", ignoreCase = true) -> UpdateResult.UP_TO_DATE
            text.contains("DONE", ignoreCase = true) -> UpdateResult.UPDATED
            else -> UpdateResult.UNKNOWN
        }
    }

    private class Fmt(val height: Int, val hasVideo: Boolean, val hasAudio: Boolean, val size: Long)

    private fun parse(inputUrl: String, out: String): VideoMeta {
        val start = out.indexOf('{')
        require(start >= 0) { "No JSON in yt-dlp output" }
        var json = JSONObject(out.substring(start).trim())
        var finalUrl = inputUrl

        // The link pointed to a playlist: use its first video.
        if (json.optString("_type") == "playlist") {
            val first = json.optJSONArray("entries")?.optJSONObject(0)
                ?: throw IllegalStateException("Empty playlist")
            json = first
            finalUrl = json.optString("webpage_url").ifEmpty { json.optString("url") }.ifEmpty { inputUrl }
        }

        val formats = mutableListOf<Fmt>()
        val arr = json.optJSONArray("formats")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val v = if (f.isNull("vcodec")) "unknown" else f.optString("vcodec")
                val a = if (f.isNull("acodec")) "unknown" else f.optString("acodec")
                val size = when {
                    !f.isNull("filesize") -> f.optLong("filesize")
                    !f.isNull("filesize_approx") -> f.optLong("filesize_approx")
                    else -> 0L
                }
                formats += Fmt(f.optInt("height", 0), v != "none", a != "none", size)
            }
        }

        val heights = formats.filter { it.hasVideo && it.height > 0 }.map { it.height }.distinct().sortedDescending()
        val audioSize = formats.filter { !it.hasVideo && it.hasAudio }.maxOfOrNull { it.size } ?: 0L

        fun sizeFor(h: Int): Long {
            val v = formats.filter { it.hasVideo && it.height == h }.maxOfOrNull { it.size } ?: 0L
            return if (v > 0) v + audioSize else 0L
        }

        val options = mutableListOf<QualityOption>()
        options += QualityOption(
            height = 0,
            audioOnly = false,
            selector = "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b",
            sizeBytes = heights.firstOrNull()?.let { sizeFor(it) } ?: 0L,
        )
        for (h in heights) {
            options += QualityOption(
                height = h,
                audioOnly = false,
                selector = "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/b",
                sizeBytes = sizeFor(h),
            )
        }
        options += QualityOption(height = 0, audioOnly = true, selector = "ba/b", sizeBytes = audioSize)

        return VideoMeta(
            url = finalUrl,
            title = json.optString("title").ifEmpty { "Video" },
            thumbnail = json.optString("thumbnail").ifEmpty { null },
            uploader = json.optString("uploader").ifEmpty { json.optString("channel") }.ifEmpty { null },
            durationSec = json.optDouble("duration", 0.0).let { if (it.isNaN()) 0L else it.toLong() },
            qualities = options,
        )
    }
}
