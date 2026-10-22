package com.yasinjahantigh.logarhythm.data.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Copies the finished audio file into shared Music storage through MediaStore / the legacy scanner. */
@Singleton
class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class SavedFile(
        val mediaStoreId: Long,
        val contentUri: String,
        val filePath: String?,
        val sizeBytes: Long
    )

    suspend fun save(
        source: File,
        title: String,
        artist: String,
        album: String
    ): SavedFile = withContext(Dispatchers.IO) {
        val extension = source.extension.lowercase().takeIf { it in MIME_TYPES } ?: "m4a"
        val mimeType = MIME_TYPES.getValue(extension)
        val displayName = buildDisplayName(title, artist, extension)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(source, displayName, mimeType, title, artist, album)
        } else {
            saveLegacy(source, displayName, extension, mimeType)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(
        source: File,
        displayName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String
    ): SavedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist.ifBlank { MediaStore.UNKNOWN_STRING })
            put(MediaStore.Audio.Media.ALBUM, album.ifBlank { FOLDER })
            put(
                MediaStore.Audio.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/$FOLDER"
            )
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw DownloadPipelineException(
                DownloadErrorKind.STORAGE,
                "Could not create the music file."
            )

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: throw DownloadPipelineException(
                DownloadErrorKind.STORAGE,
                "Could not open the new music file."
            )
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null
            )
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            if (error is DownloadPipelineException) throw error
            throw DownloadPipelineException(
                DownloadErrorKind.STORAGE,
                "Could not save the downloaded file.",
                error
            )
        }

        val id = ContentUris.parseId(uri)
        if (id < 0) {
            throw DownloadPipelineException(DownloadErrorKind.STORAGE, "The saved file has no id.")
        }
        return SavedFile(id, uri.toString(), null, source.length())
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(
        source: File,
        displayName: String,
        extension: String,
        mimeType: String
    ): SavedFile {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(root, FOLDER)
        if (!dir.exists() && !dir.mkdirs()) {
            throw DownloadPipelineException(
                DownloadErrorKind.STORAGE,
                "Could not create the music folder."
            )
        }

        val target = uniqueTarget(dir, displayName, extension)
        try {
            source.copyTo(target, overwrite = false)
        } catch (error: Exception) {
            throw DownloadPipelineException(
                DownloadErrorKind.STORAGE,
                "Could not write to shared storage.",
                error
            )
        }

        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath),
                    arrayOf(mimeType)
                ) { _, _ ->
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }

        var mediaStoreId: Long? = null
        repeat(4) {
            if (mediaStoreId == null) {
                mediaStoreId = queryMediaStoreId(target)
                if (mediaStoreId == null) delay(400)
            }
        }
        val id = mediaStoreId ?: run {
            target.delete()
            throw DownloadPipelineException(
                DownloadErrorKind.STORAGE,
                "The saved file could not be indexed."
            )
        }

        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id
        )
        return SavedFile(id, uri.toString(), target.absolutePath, target.length())
    }

    private fun queryMediaStoreId(file: File): Long? = runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DATA} = ?",
            arrayOf(file.absolutePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun uniqueTarget(dir: File, displayName: String, extension: String): File {
        val base = displayName.substringBeforeLast(".$extension", displayName)
        var candidate = File(dir, "$base.$extension")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index).$extension")
            index++
        }
        return candidate
    }

    private fun buildDisplayName(title: String, artist: String, extension: String): String {
        val cleanTitle = DownloadTitleParser.sanitizeFileName(title.ifBlank { "Track" })
        val cleanArtist = DownloadTitleParser.sanitizeFileName(artist)
        val base = if (cleanArtist.isBlank() || DownloadMetadataMerge.isPlaceholder(cleanArtist)) {
            cleanTitle
        } else {
            "$cleanArtist - $cleanTitle"
        }
        return "${DownloadTitleParser.sanitizeFileName(base)}.$extension"
    }

    private companion object {
        const val FOLDER = "LogaRhythm"
        val MIME_TYPES = mapOf(
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "opus" to "audio/opus",
            "ogg" to "audio/ogg",
            "oga" to "audio/ogg",
            "webm" to "audio/webm",
            "flac" to "audio/flac"
        )
    }
}
