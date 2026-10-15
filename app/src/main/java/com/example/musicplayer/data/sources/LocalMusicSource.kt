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
    @param:ApplicationContext private val context: Context
) : MusicSource {

    companion object {
        val DEFAULT_EXCLUDED_FOLDERS = setOf(
            "WhatsApp Voice Notes",
            "Call Recordings",
            "Ringtones",
            "Notifications",
            "Alarms"
        )
    }

    override val sourceType: SourceType = SourceType.LOCAL

    var minDurationMs: Long = 15_000L

    override fun getTracks(): Flow<List<Track>> = flow {
        emit(scanTracks())
    }

    suspend fun scanTracks(
        minDurationMs: Long = this.minDurationMs,
        excludedFolders: Set<String> = emptySet()
    ): List<Track> = withContext(Dispatchers.IO) {
        val effectiveExcludedFolders = excludedFolders
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.ALBUM_ARTIST
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA
            )
        }

        // Include audio tracks reliably even if the device scanner set IS_MUSIC to 0 or null
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL OR ${MediaStore.Audio.Media.DURATION} > 30000) AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val cursor = try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            ) ?: throw IllegalStateException("MediaStore returned no cursor for the audio library")
        } catch (exception: SecurityException) {
            // Propagate permission loss so a refresh cannot mistake it for an empty library.
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to read the audio library", exception)
        }

        cursor.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val genreCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else -1
            val albumArtistCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            } else -1

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idCol)
                val rawTitle = cursor.getString(titleCol) ?: ""
                val rawArtist = cursor.getString(artistCol) ?: ""
                val rawAlbum = cursor.getString(albumCol) ?: ""
                val duration = cursor.getLong(durationCol)
                val dateModified = cursor.getLong(dateModifiedCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val filePath = if (dataCol != -1) cursor.getString(dataCol) else null
                val genreRaw = if (genreCol != -1) cursor.getString(genreCol) else null
                val genre = genreRaw?.trim()?.ifBlank { "Unknown Genre" } ?: "Unknown Genre"
                val albumArtist = if (albumArtistCol != -1) {
                    cleanMetadataField(cursor.getString(albumArtistCol) ?: "", "")
                } else ""

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
                        dateAdded = dateAdded,
                        folder = folderName,
                        genre = genre,
                        albumArtist = albumArtist,
                        filePath = filePath.orEmpty()
                    )
                )
            }
        }

        tracks
    }

    private fun cleanMetadataField(value: String, fallback: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true)
        ) {
            return fallback
        }
        return trimmed
    }

    private fun cleanTitle(title: String, filePath: String?): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true)
        ) {
            if (!filePath.isNullOrBlank()) {
                val fileName = File(filePath).nameWithoutExtension
                if (fileName.isNotBlank() &&
                    !fileName.equals("unknown", ignoreCase = true) &&
                    !fileName.equals("null", ignoreCase = true)
                ) {
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

    suspend fun scanAllFolderNames(): Set<String> = withContext(Dispatchers.IO) {
        val folders = mutableSetOf<String>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.DATA} IS NOT NULL"
        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (dataCol != -1) {
                    while (cursor.moveToNext()) {
                        val filePath = cursor.getString(dataCol)
                        if (!filePath.isNullOrBlank()) {
                            val parent = File(filePath).parentFile?.name
                            if (!parent.isNullOrBlank()) {
                                folders.add(parent)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        folders
    }
}
