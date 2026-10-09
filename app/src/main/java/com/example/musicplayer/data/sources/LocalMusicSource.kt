package com.example.musicplayer.data.sources

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.source.MusicSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicSource {

    companion object {
        val DEFAULT_EXCLUDED_FOLDERS = setOf(
            "WhatsApp Audio",
            "WhatsApp Voice Notes",
            "Telegram Audio",
            "Telegram",
            "Recordings",
            "Voice Recorder",
            "Call Recordings",
            "Ringtones",
            "Notifications",
            "Alarms"
        )
    }

    override val sourceType: SourceType = SourceType.LOCAL

    var minDurationMs: Long = 60_000L

    override fun getTracks(): Flow<List<Track>> = flow {
        emit(scanTracks())
    }

    suspend fun scanTracks(
        minDurationMs: Long = this.minDurationMs,
        excludedFolders: Set<String> = emptySet()
    ): List<Track> = withContext(Dispatchers.IO) {
        val effectiveExcludedFolders = if (excludedFolders.isEmpty()) DEFAULT_EXCLUDED_FOLDERS else excludedFolders
        val tracks = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.GENRE
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATA
            )
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                } else -1

                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: ""
                    val rawArtist = cursor.getString(artistCol) ?: ""
                    val rawAlbum = cursor.getString(albumCol) ?: ""
                    val duration = cursor.getLong(durationCol)
                    val dateModified = cursor.getLong(dateModifiedCol)
                    val filePath = if (dataCol != -1) cursor.getString(dataCol) else null
                    val genreRaw = if (genreCol != -1) cursor.getString(genreCol) else null
                    val genre = genreRaw?.trim()?.ifBlank { "Unknown Genre" } ?: "Unknown Genre"

                    val folderName = when {
                        !filePath.isNullOrBlank() -> {
                            File(filePath).parentFile?.name ?: "Music"
                        }
                        else -> "Music"
                    }

                    if (effectiveExcludedFolders.any { excluded -> folderName.equals(excluded, ignoreCase = true) }) {
                        continue
                    }

                    val title = cleanTitle(rawTitle, filePath)
                    val artist = cleanMetadataField(rawArtist, "Unknown Artist")
                    val album = cleanMetadataField(rawAlbum, "Unknown Album")

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaStoreId
                    ).toString()

                    tracks.add(
                        Track(
                            id = "local:$mediaStoreId",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            artworkUri = contentUri,
                            sourceType = SourceType.LOCAL,
                            uri = contentUri,
                            dateModified = dateModified,
                            folder = folderName,
                            genre = genre
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission denied or revoked
        } catch (_: Exception) {
            // MediaStore query error fallback
        }

        tracks
    }

    private fun cleanMetadataField(value: String, fallback: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true)
        ) {
            return fallback
        }
        return trimmed
    }

    private fun cleanTitle(title: String, filePath: String?): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true)
        ) {
            if (!filePath.isNullOrBlank()) {
                val fileName = File(filePath).nameWithoutExtension
                if (fileName.isNotBlank() && !fileName.equals("unknown", ignoreCase = true)) {
                    return fileName.replace('_', ' ').replace('-', ' ').trim()
                }
            }
            return "Unknown Title"
        }
        val clean = trimmed
            .removeSuffix(".mp3")
            .removeSuffix(".m4a")
            .removeSuffix(".flac")
            .removeSuffix(".wav")
            .removeSuffix(".ogg")
        return clean.ifBlank { "Unknown Title" }
    }

    override suspend fun getTrackById(id: String): Track? = withContext(Dispatchers.IO) {
        scanTracks().firstOrNull { it.id == id }
    }

    override suspend fun searchTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val lowerQuery = query.lowercase()
        scanTracks().filter {
            it.title.lowercase().contains(lowerQuery) ||
                it.artist.lowercase().contains(lowerQuery) ||
                it.album.lowercase().contains(lowerQuery) ||
                it.genre.lowercase().contains(lowerQuery)
        }
    }

    override suspend fun getPlaybackUri(track: Track): String = track.uri
}
