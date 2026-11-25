package com.example.videograb

import android.content.Context
import java.util.Locale

private val URL_REGEX = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

/** Finds the first http(s) link inside arbitrary text (e.g. text shared from another app). */
fun extractUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val found = URL_REGEX.find(text)?.value ?: return null
    return found.trimEnd('.', ',', ')', ']', ';')
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1048576.0
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.2f GB", mb / 1024)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%d KB", maxOf(1L, bytes / 1024))
    }
}

fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun qualityLabel(context: Context, q: QualityOption): String = when {
    q.audioOnly -> context.getString(R.string.quality_audio)
    q.height > 0 -> "${q.height}p"
    else -> context.getString(R.string.quality_best)
}

/** Extracts the useful part of a yt-dlp error message. */
fun cleanError(message: String?): String? {
    if (message.isNullOrBlank()) return null
    val lines = message.lines().filter { it.isNotBlank() }
    val line = lines.lastOrNull { it.contains("ERROR", ignoreCase = true) } ?: lines.lastOrNull() ?: return null
    return line.replace(Regex("^.*?ERROR:\\s*"), "").trim().take(240)
}
