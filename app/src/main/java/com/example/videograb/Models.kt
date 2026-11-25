package com.example.videograb

import org.json.JSONObject

enum class Status { QUEUED, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELED }

/** One entry in the downloads list (persisted to disk as JSON). */
data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val uploader: String? = null,
    val selector: String,
    val audioOnly: Boolean,
    val qualityLabel: String,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val error: String? = null,
    val savedUri: String? = null,
    val legacyPath: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean
        get() = status == Status.QUEUED || status == Status.DOWNLOADING || status == Status.PROCESSING

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("title", title)
        put("thumbnail", thumbnail)
        put("uploader", uploader)
        put("selector", selector)
        put("audioOnly", audioOnly)
        put("qualityLabel", qualityLabel)
        put("status", status.name)
        put("error", error)
        put("savedUri", savedUri)
        put("legacyPath", legacyPath)
        put("sizeBytes", sizeBytes)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadItem = DownloadItem(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title"),
            thumbnail = o.optString("thumbnail").ifEmpty { null },
            uploader = o.optString("uploader").ifEmpty { null },
            selector = o.optString("selector"),
            audioOnly = o.optBoolean("audioOnly"),
            qualityLabel = o.optString("qualityLabel"),
            status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.FAILED),
            error = o.optString("error").ifEmpty { null },
            savedUri = o.optString("savedUri").ifEmpty { null },
            legacyPath = o.optString("legacyPath").ifEmpty { null },
            sizeBytes = o.optLong("sizeBytes"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** A quality the user can choose. height == 0 means "best available". */
data class QualityOption(
    val height: Int,
    val audioOnly: Boolean,
    val selector: String,
    val sizeBytes: Long,
)

data class VideoMeta(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Long,
    val qualities: List<QualityOption>,
)

/** An error whose message is already user-friendly and localized. */
class UserFacingException(message: String) : Exception(message)
