package com.yasinjahantigh.logarhythm.data.sources

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.yasinjahantigh.logarhythm.core.library.AudioLibraryFilter
import com.yasinjahantigh.logarhythm.data.room.TrackEntity
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEngines
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.source.MusicSource
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
            "Call Recording",
            "Voice Recorder",
            "Voice Notes",
            "Recordings",
            "Sound Recorder",
            "Ringtones",
            "Notifications",
            "Alarms",
            "Sounds"
        )
        private val AUDIO_EXT_REGEX = Regex("""(?i)\.(mp3|m4a|flac|wav|ogg|opus|aac|wma)$""")
    }

    override val sourceType: SourceType = SourceType.LOCAL

    var minDurationMs: Long = 15_000L

    override fun getTracks(): Flow<List<Track>> = flow {
        emit(scanTracks())
    }

    suspend fun scanTracks(
        minDurationMs: Long = this.minDurationMs,
        excludedFolders: Set<String> = emptySet(),
        showSystemAudio: Boolean = false,
        whitelistMode: Boolean = false,
        whitelistedFolders: Set<String> = emptySet(),
        existingById: Map<String, TrackEntity> = emptyMap()
    ): List<Track> = withContext(Dispatchers.IO) {
        val excludedLower = if (excludedFolders.isEmpty()) emptySet() else excludedFolders.mapTo(HashSet(excludedFolders.size * 2)) { it.lowercase() }
        val whitelistedLower = if (whitelistedFolders.isEmpty()) emptySet() else whitelistedFolders.mapTo(HashSet(whitelistedFolders.size * 2)) { it.lowercase() }
        val tracks = ArrayList<Track>()
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
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.IS_MUSIC
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
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.IS_MUSIC
            )
        }

        // Include audio tracks reliably even if the device scanner set IS_MUSIC to 0 or null,
        // but keep ringtones/notifications/alarms out unless the user opts in.
        val baseSelection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL OR ${MediaStore.Audio.Media.DURATION} > 30000) AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selection = if (showSystemAudio) {
            baseSelection
        } else {
            buildString {
                append(baseSelection)
                append(" AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0")
                append(" AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
                append(" AND ${MediaStore.Audio.Media.IS_ALARM} = 0")
                // Android 12+ flags files in the standard Recordings/ directory itself.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    append(" AND ${MediaStore.Audio.AudioColumns.IS_RECORDING} = 0")
                }
            }
        }
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
            // Older providers may not expose every optional column even when it is in the
            // projection; a missing index must degrade to the fallback, not crash the scan.
            val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val composerCol = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
            val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)

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
                var genre = MetadataNormalizer.canonicalizeGenre(genreRaw, "Unknown Genre")
                var albumArtist = if (albumArtistCol != -1) {
                    cleanMetadataField(cursor.getString(albumArtistCol) ?: "", "")
                } else ""
                var year = if (yearCol != -1) cursor.getInt(yearCol).coerceAtLeast(0) else 0
                // MediaStore packs disc and track into one number: disc * 1000 + track.
                val packedTrack = if (trackCol != -1) cursor.getInt(trackCol).coerceAtLeast(0) else 0
                var discNumber = packedTrack / 1000
                var trackNumber = packedTrack % 1000
                var composer = if (composerCol != -1) {
                    cleanMetadataField(cursor.getString(composerCol) ?: "", "")
                } else ""

                val folderName = extractParentFolderName(filePath) ?: "Music"
                val folderLower = folderName.lowercase()

                if (whitelistMode) {
                    if (folderLower !in whitelistedLower) {
                        continue
                    }
                } else if (folderLower in excludedLower) {
                    continue
                }
                val isMusicFlag = if (isMusicCol != -1 && !cursor.isNull(isMusicCol)) {
                    cursor.getInt(isMusicCol) != 0
                } else null
                val hasArtistOrAlbum = !MetadataNormalizer.isPlaceholderField(rawArtist) ||
                    !MetadataNormalizer.isPlaceholderField(rawAlbum)
                if (!showSystemAudio &&
                    AudioLibraryFilter.isHidden(
                        filePath = filePath,
                        folderName = folderName,
                        fileName = extractFileName(filePath),
                        isMusic = isMusicFlag,
                        durationMs = duration,
                        hasArtistOrAlbum = hasArtistOrAlbum
                    )
                ) {
                    continue
                }

                val trackId = "local:$mediaStoreId"
                var title = cleanTitle(rawTitle, filePath)
                var artist = cleanMetadataField(rawArtist, "Unknown Artist")
                var album = cleanMetadataField(rawAlbum, "Unknown Album")

                val existing = existingById[trackId]
                val unchangedInDb = existing != null &&
                    existing.dateModified == dateModified &&
                    existing.filePath == filePath.orEmpty()

                val needsEmbeddedFallback = !unchangedInDb && !filePath.isNullOrBlank() && (
                    MetadataNormalizer.isPlaceholderField(rawTitle) ||
                        MetadataNormalizer.isPlaceholderField(title) ||
                        MetadataNormalizer.isPlaceholderField(artist) ||
                        MetadataNormalizer.isPlaceholderField(album) ||
                        genre == "Unknown Genre" ||
                        year == 0 ||
                        albumArtist.isEmpty()
                    )

                if (needsEmbeddedFallback) {
                    val embedded = runCatching {
                        val file = File(filePath!!)
                        if (file.isFile && file.canRead()) TagEngines.detect(file)?.read(file) else null
                    }.getOrNull()

                    if (embedded != null) {
                        if (MetadataNormalizer.isPlaceholderField(rawTitle) || MetadataNormalizer.isPlaceholderField(title)) {
                            embedded.title?.takeUnless { MetadataNormalizer.isPlaceholderField(it) }?.let {
                                title = cleanTitle(it, filePath)
                            }
                        }
                        if (MetadataNormalizer.isPlaceholderField(artist)) {
                            embedded.artist?.takeUnless { MetadataNormalizer.isPlaceholderField(it) }?.let {
                                artist = cleanMetadataField(it, "Unknown Artist")
                            }
                        }
                        if (MetadataNormalizer.isPlaceholderField(album)) {
                            embedded.album?.takeUnless { MetadataNormalizer.isPlaceholderField(it) }?.let {
                                album = cleanMetadataField(it, "Unknown Album")
                            }
                        }
                        if (albumArtist.isEmpty()) {
                            embedded.albumArtist?.takeUnless { MetadataNormalizer.isPlaceholderField(it) }?.let {
                                albumArtist = cleanMetadataField(it, "")
                            }
                        }
                        if (genre == "Unknown Genre") {
                            genre = MetadataNormalizer.canonicalizeGenre(embedded.genre, "Unknown Genre")
                        }
                        if (year == 0) {
                            year = parseYear(embedded.year)
                        }
                        if (trackNumber == 0) {
                            trackNumber = (embedded.trackNumber ?: 0).coerceAtLeast(0)
                        }
                        if (discNumber == 0) {
                            discNumber = (embedded.discNumber ?: 0).coerceAtLeast(0)
                        }
                        if (composer.isEmpty()) {
                            composer = cleanMetadataField(embedded.composer.orEmpty(), "")
                        }
                    }
                }

                val cleanedTags = TagSuggestionCleaner.clean(
                    title = title,
                    artist = artist,
                    album = album,
                    genre = genre,
                    year = year,
                    albumArtist = albumArtist
                )
                title = cleanedTags.title.ifBlank { "Unknown Title" }
                artist = cleanedTags.artist.ifBlank { "Unknown Artist" }
                album = cleanedTags.album.ifBlank { "Unknown Album" }
                genre = cleanedTags.genre.ifBlank { "Unknown Genre" }
                albumArtist = cleanedTags.albumArtist

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
                        filePath = filePath.orEmpty(),
                        year = year,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        composer = composer
                    )
                )
            }
        }

        tracks
    }

    private fun parseYear(rawYear: String?): Int {
        val text = rawYear?.trim().orEmpty()
        if (text.length < 4) return 0
        return text.take(4).toIntOrNull()?.takeIf { it in 1900..2100 } ?: 0
    }

    private fun cleanMetadataField(value: String, fallback: String): String {
        val cleaned = MetadataNormalizer.cleanDisplayText(value)
        if (cleaned.isEmpty() || MetadataNormalizer.isPlaceholderField(cleaned)) {
            return fallback
        }
        return cleaned
    }

    private fun cleanTitle(title: String, filePath: String?): String {
        val cleaned = MetadataNormalizer.cleanDisplayText(title)
        if (cleaned.isEmpty() || MetadataNormalizer.isPlaceholderField(cleaned)) {
            if (!filePath.isNullOrBlank()) {
                val rawName = extractFileName(filePath).orEmpty()
                val baseName = rawName.substringBeforeLast('.', rawName)
                val fileName = MetadataNormalizer.cleanDisplayText(baseName)
                if (fileName.isNotBlank() && !MetadataNormalizer.isPlaceholderField(fileName)) {
                    return fileName.replace('_', ' ').trim()
                }
            }
            return "Unknown Title"
        }
        val clean = if (cleaned.indexOf('.') >= 0) {
            cleaned.replace(AUDIO_EXT_REGEX, "").trim()
        } else {
            cleaned
        }
        return clean.ifBlank { "Unknown Title" }
    }

    private fun extractFileName(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        val slash = filePath.lastIndexOf('/')
        return if (slash >= 0) filePath.substring(slash + 1) else filePath
    }

    private fun extractParentFolderName(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        val lastSlash = filePath.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val prevSlash = filePath.lastIndexOf('/', lastSlash - 1)
        val name = if (prevSlash >= 0) filePath.substring(prevSlash + 1, lastSlash) else filePath.substring(0, lastSlash)
        return name.ifBlank { null }
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
                        val parent = extractParentFolderName(cursor.getString(dataCol))
                        if (!parent.isNullOrBlank()) {
                            folders.add(parent)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        folders
    }
}
