package com.example.videograb

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

data class SavedFile(val uri: String, val legacyPath: String?, val name: String, val size: Long)

/** Copies a finished download into public storage (Movies/VideoGrab or Music/VideoGrab). */
object MediaSaver {

    fun save(context: Context, file: File, audioOnly: Boolean): SavedFile {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (audioOnly) "audio/mpeg" else "video/mp4"
        val isAudio = audioOnly || mime.startsWith("audio/")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, file, mime, isAudio)
        } else {
            saveLegacy(context, file, mime, isAudio)
        }
    }

    private fun saveWithMediaStore(context: Context, file: File, mime: String, isAudio: Boolean): SavedFile {
        val resolver = context.contentResolver
        val folder = (if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/VideoGrab"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output stream")
            out.use { o -> file.inputStream().use { it.copyTo(o, 256 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return SavedFile(uri.toString(), null, file.name, file.length())
    }

    private fun saveLegacy(context: Context, file: File, mime: String, isAudio: Boolean): SavedFile {
        val base = Environment.getExternalStoragePublicDirectory(
            if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        )
        val dir = File(base, "VideoGrab")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create ${dir.absolutePath}")

        var dest = File(dir, file.name)
        var n = 1
        while (dest.exists()) {
            dest = File(dir, "${file.nameWithoutExtension} ($n).${file.extension}")
            n++
        }
        file.copyTo(dest, overwrite = false)
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        return SavedFile(uri.toString(), dest.absolutePath, dest.name, dest.length())
    }
}

/** Open / share / delete a saved file. */
object FileActions {

    /** Returns false if the file is missing. */
    fun exists(context: Context, item: DownloadItem): Boolean {
        val uriStr = item.savedUri ?: return false
        return try {
            context.contentResolver.openFileDescriptor(Uri.parse(uriStr), "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    enum class OpenResult { OK, MISSING, NO_APP }

    fun open(context: Context, item: DownloadItem): OpenResult {
        if (!exists(context, item)) return OpenResult.MISSING
        val uri = Uri.parse(item.savedUri)
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            OpenResult.OK
        } catch (e: ActivityNotFoundException) {
            OpenResult.NO_APP
        }
    }

    fun share(context: Context, item: DownloadItem): OpenResult {
        if (!exists(context, item)) return OpenResult.MISSING
        val uri = Uri.parse(item.savedUri)
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            OpenResult.OK
        } catch (e: ActivityNotFoundException) {
            OpenResult.NO_APP
        }
    }

    fun deleteSaved(context: Context, savedUri: String?, legacyPath: String?) {
        runCatching {
            if (legacyPath != null) {
                File(legacyPath).delete()
            } else if (savedUri != null) {
                context.contentResolver.delete(Uri.parse(savedUri), null, null)
            }
        }
    }
}
