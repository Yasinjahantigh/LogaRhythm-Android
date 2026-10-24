package com.yasinjahantigh.logarhythm.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.yasinjahantigh.logarhythm.core.artwork.ArtworkVersionStore
import com.yasinjahantigh.logarhythm.core.utils.SearchUtils
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.DownloadDao
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.PlaylistDao
import com.yasinjahantigh.logarhythm.data.room.TrackEntity
import com.yasinjahantigh.logarhythm.data.sources.LocalMusicSource
import com.yasinjahantigh.logarhythm.data.tagger.AudioFileTagWriter
import com.yasinjahantigh.logarhythm.data.tagger.TagWriteOutcome
import com.yasinjahantigh.logarhythm.data.tagger.engine.TagEdit
import com.yasinjahantigh.logarhythm.domain.library.ArtistIndex
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.ArtistProfile
import com.yasinjahantigh.logarhythm.domain.model.Folder
import com.yasinjahantigh.logarhythm.domain.model.Genre
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditFailure
import com.yasinjahantigh.logarhythm.domain.model.TrackEditResult
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localMusicSource: LocalMusicSource,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val musicDatabase: com.yasinjahantigh.logarhythm.data.room.MusicDatabase,
    private val preferencesManager: PreferencesManager,
    private val audioFileTagWriter: AudioFileTagWriter
) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return combine(musicDao.getAllTracks(), preferencesManager.hiddenTrackIds) { list, hiddenIds ->
            list.map { it.toDomain() }.filter { it.id !in hiddenIds }
        }.flowOn(Dispatchers.Default)
    }

    override fun getHiddenTracks(): Flow<List<Track>> {
        return combine(musicDao.getAllTracks(), preferencesManager.hiddenTrackIds) { list, hiddenIds ->
            list.map { it.toDomain() }.filter { it.id in hiddenIds }
        }.flowOn(Dispatchers.Default)
    }

    override fun getTrackById(id: String): Flow<Track?> {
        return musicDao.getTrackById(id).map { it?.toDomain() }
    }

    override fun searchTracks(query: String): Flow<List<Track>> {
        return getAllTracks().map { allTracks ->
            allTracks.filter { track ->
                val searchable = "${track.title} ${track.artist} ${track.album} ${track.genre}"
                SearchUtils.matchesTokens(searchable, query)
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        return getAlbums().map { allAlbums ->
            allAlbums.filter { album ->
                val searchable = "${album.name} ${album.artist}"
                SearchUtils.matchesTokens(searchable, query)
            }
        }
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        return getArtists().map { allArtists ->
            allArtists.filter { artist ->
                SearchUtils.matchesTokens(artist.name, query)
            }
        }
    }

    override fun searchGenres(query: String): Flow<List<Genre>> {
        return getGenres().map { allGenres ->
            allGenres.filter { genre ->
                SearchUtils.matchesTokens(genre.name, query)
            }
        }
    }

    override fun getAlbums(): Flow<List<Album>> {
        return getAllTracks().map { tracks ->
            class AlbumAcc(
                val name: String,
                val artist: String,
                var trackCount: Int,
                var artworkUri: String?,
                var year: Int
            )
            val map = linkedMapOf<String, AlbumAcc>()
            for (track in tracks) {
                // The full album-artist string stays the grouping key: "A & B" is its own album
                // artist and must not collapse into either solo artist's discography.
                val artistKey = track.albumArtist.ifBlank { track.artist }
                val key = "${track.album}|$artistKey"
                val existing = map[key]
                if (existing == null) {
                    map[key] = AlbumAcc(track.album, artistKey, 1, track.artworkUri, track.year)
                } else {
                    existing.trackCount++
                    if (existing.artworkUri.isNullOrBlank() && !track.artworkUri.isNullOrBlank()) {
                        existing.artworkUri = track.artworkUri
                    }
                    if (track.year > existing.year) existing.year = track.year
                }
            }
            map.values.map {
                Album(
                    id = "album:${it.name}:${it.artist}",
                    name = it.name,
                    artist = it.artist,
                    trackCount = it.trackCount,
                    artworkUri = it.artworkUri,
                    year = it.year
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }.flowOn(Dispatchers.Default)
    }

    private fun splitConfig(): Flow<ArtistSplitConfig> =
        combine(
            preferencesManager.splitArtists,
            preferencesManager.artistSeparators,
            preferencesManager.artistNeverSplit
        ) { enabled, raw, neverSplit ->
            if (enabled) {
                ArtistSplitConfig(
                    enabled = true,
                    separators = ArtistSplitter.parseSeparators(raw),
                    neverSplit = neverSplit
                )
            } else {
                ArtistSplitConfig.OFF
            }
        }

    private fun artistIndexFlow(): Flow<ArtistIndex> =
        splitConfig().flatMapLatest { config ->
            getAllTracks().map { tracks -> ArtistIndex.build(tracks, config) }
                .flowOn(Dispatchers.Default)
        }

    override fun getArtists(): Flow<List<Artist>> = artistIndexFlow().map { it.artists }

    override fun getArtistProfile(artist: String): Flow<ArtistProfile?> =
        combine(artistIndexFlow(), getPlayStats()) { index, playStats ->
            index.profileOf(artist)?.let { profile ->
                profile.copy(topTracks = rankTopTracks(profile.tracks, playStats))
            }
        }

    private fun rankTopTracks(tracks: List<Track>, playStats: Map<String, TrackPlayStat>): List<Track> =
        tracks.sortedWith(
            compareByDescending<Track> { playStats[it.id]?.playCount ?: 0 }
                .thenByDescending { playStats[it.id]?.lastPlayedAt ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        ).take(5)

    override fun getPlayStats(): Flow<Map<String, TrackPlayStat>> =
        musicDao.getPlayStats().map { stats -> stats.associateBy { it.trackId } }

    override fun getFavoriteTrackIds(): Flow<Set<String>> =
        playlistDao.observePlaylistTrackIds(Playlist.FAVORITES_ID)
            .map { ids -> ids.toSet() }
            .flowOn(Dispatchers.Default)

    override fun getDownloadedTrackUris(): Flow<Set<String>> =
        downloadDao.observeSavedContentUris()
            .map { uris -> uris.filter { it.isNotBlank() }.toSet() }
            .flowOn(Dispatchers.Default)

    override fun getFolders(): Flow<List<Folder>> {
        return getAllTracks().map { tracks ->
            val byFolder = LinkedHashMap<String, Int>()
            for (track in tracks) {
                if (track.folder.isNotBlank()) {
                    byFolder[track.folder] = (byFolder[track.folder] ?: 0) + 1
                }
            }
            byFolder.entries.map {
                Folder(
                    id = "folder:${it.key}",
                    name = it.key,
                    trackCount = it.value
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }.flowOn(Dispatchers.Default)
    }

    override fun getGenres(): Flow<List<Genre>> {
        return getAllTracks().map { tracks ->
            val byGenre = LinkedHashMap<String, Int>()
            for (track in tracks) {
                if (track.genre.isNotBlank() && !track.genre.equals("Unknown Genre", ignoreCase = true)) {
                    byGenre[track.genre] = (byGenre[track.genre] ?: 0) + 1
                }
            }
            byGenre.entries.map {
                Genre(
                    id = "genre:${it.key}",
                    name = it.key,
                    trackCount = it.value
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }.flowOn(Dispatchers.Default)
    }

    override fun getTracksByAlbum(album: String): Flow<List<Track>> {
        return getAllTracks().map { tracks ->
            tracks.filter { it.album.equals(album, ignoreCase = true) }
                .sortedWith(
                    compareBy<Track> { it.discNumber.coerceAtLeast(0) }
                        .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                )
        }.flowOn(Dispatchers.Default)
    }

    override fun getTracksByArtist(artist: String): Flow<List<Track>> =
        artistIndexFlow().map { it.tracksOf(artist) }

    override fun getTracksByFolder(folder: String): Flow<List<Track>> {
        return getAllTracks().map { tracks ->
            tracks.filter { it.folder.equals(folder, ignoreCase = true) }
        }
    }

    override fun getTracksByGenre(genre: String): Flow<List<Track>> {
        return getAllTracks().map { tracks ->
            tracks.filter { it.genre.equals(genre, ignoreCase = true) }
        }
    }

    override fun getAlbumByName(album: String): Flow<Album?> {
        return getAlbums().map { list -> list.firstOrNull { it.name.equals(album, ignoreCase = true) } }
    }

    override fun getArtistByName(artist: String): Flow<Artist?> =
        artistIndexFlow().map { it.artistOf(artist) }

    override fun getGenreByName(genre: String): Flow<Genre?> {
        return getGenres().map { list -> list.firstOrNull { it.name.equals(genre, ignoreCase = true) } }
    }

    override suspend fun refreshTracks() = withContext(Dispatchers.IO) {
        val minSec = preferencesManager.minTrackLengthSec.first()
        val showSystemAudio = preferencesManager.showSystemAudio.first()
        // Turning system audio on drops the built-in folder defaults, but keeps exclusions
        // the user selected explicitly.
        val excludedFolders = if (showSystemAudio) {
            preferencesManager.customExcludedFolders.first()
        } else {
            preferencesManager.excludedFolders.first()
        }
        val scanned = localMusicSource.scanTracks(
            minDurationMs = minSec * 1000L,
            excludedFolders = excludedFolders,
            showSystemAudio = showSystemAudio
        )
        val existing = musicDao.getTracksBySourceType(SourceType.LOCAL.prefix)
        val existingMap = existing.associateBy { it.id }

        val toInsert = mutableListOf<TrackEntity>()
        val toUpdate = mutableListOf<TrackEntity>()
        val seenIds = mutableSetOf<String>()

        for (track in scanned) {
            seenIds.add(track.id)
            val current = existingMap[track.id]
            val newEntity = TrackEntity.fromDomain(track)

            if (current == null) {
                toInsert.add(newEntity)
            } else if (current != newEntity) {
                // If file timestamp hasn't changed externally, preserve user's edited tags
                val isStaleScan = current.dateModified == newEntity.dateModified &&
                        (current.title != newEntity.title || current.artist != newEntity.artist || current.album != newEntity.album)
                if (isStaleScan) {
                    val preservedEntity = newEntity.copy(
                        title = current.title,
                        artist = current.artist,
                        album = current.album,
                        genre = current.genre,
                        albumArtist = current.albumArtist,
                        year = current.year,
                        trackNumber = current.trackNumber,
                        discNumber = current.discNumber,
                        composer = current.composer
                    )
                    if (current != preservedEntity) {
                        toUpdate.add(preservedEntity)
                    }
                } else {
                    toUpdate.add(newEntity)
                }
            }
        }

        val toDeleteIds = existing.map { it.id }.filter { it !in seenIds }

        musicDao.syncLocalTracks(toInsert, toUpdate, toDeleteIds)
    }

    override suspend fun updateTrackMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Track? = withContext(Dispatchers.IO) {
        val existing = musicDao.getTrackByIdOnce(id) ?: return@withContext null
        val cleanTitle = title.trim().ifBlank { "Unknown Title" }
        val cleanArtist = artist.trim().ifBlank { "Unknown Artist" }
        val cleanAlbum = album.trim().ifBlank { "Unknown Album" }
        val cleanGenre = genre.trim().ifBlank { "Unknown Genre" }

        // Do not mutate Room until the physical file's embedded tags have been written
        // and read back. Remote/non-local tracks are not local file-tag targets.
        withContext(NonCancellable) {
            // Once the user-authorized file write begins, finish the file/Room commit pair
            // despite navigation or cancellation so the UI cannot leave stale Room metadata.
            val wroteAndVerified = audioFileTagWriter.writeTagsToFileAndMediaStore(
                track = existing.toDomain(), // diffed against the file's current values
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                genre = cleanGenre
            )
            if (!wroteAndVerified) return@withContext null

            musicDao.updateTrackMetadata(
                id = id,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                albumArtist = existing.albumArtist,
                genre = cleanGenre,
                year = existing.year,
                trackNumber = existing.trackNumber,
                discNumber = existing.discNumber,
                composer = existing.composer,
                dateModified = existing.dateModified
            )
            musicDao.getTrackByIdOnce(id)?.toDomain()
        }
    }

    override suspend fun updateTrackDetails(id: String, edit: TrackEdit): TrackEditResult =
        withContext(Dispatchers.IO) {
            val existing = musicDao.getTrackByIdOnce(id)
                ?: return@withContext TrackEditResult.Failure(TrackEditFailure.FAILED)
            if (!id.startsWith("local:")) {
                return@withContext TrackEditResult.Failure(TrackEditFailure.NOT_LOCAL)
            }

            val cleanTitle = edit.title.trim().ifBlank { "Unknown Title" }
            val cleanArtist = edit.artist.trim().ifBlank { "Unknown Artist" }
            val cleanAlbum = edit.album.trim().ifBlank { "Unknown Album" }
            val cleanAlbumArtist = edit.albumArtist.trim()
            val cleanGenre = edit.genre.trim().ifBlank { "Unknown Genre" }
            val cleanComposer = edit.composer.trim()

            fun textEdit(newValue: String, oldValue: String, placeholder: String): String? {
                val old = oldValue.trim()
                if (newValue == old) return null
                return if (newValue.equals(placeholder, ignoreCase = true)) "" else newValue
            }
            fun rawEdit(newValue: String, oldValue: String): String? =
                if (newValue == oldValue.trim()) null else newValue

            val year = edit.year.coerceAtLeast(0)
            val trackNumber = edit.trackNumber.coerceAtLeast(0)
            val discNumber = edit.discNumber.coerceAtLeast(0)
            val artworkEdit: ByteArray? = when {
                edit.removeArtwork -> ByteArray(0)
                edit.artwork != null && edit.artwork.isNotEmpty() -> edit.artwork
                else -> null
            }
            val tagEdit = TagEdit(
                title = textEdit(cleanTitle, existing.title, "Unknown Title"),
                artist = textEdit(cleanArtist, existing.artist, "Unknown Artist"),
                album = textEdit(cleanAlbum, existing.album, "Unknown Album"),
                albumArtist = rawEdit(cleanAlbumArtist, existing.albumArtist),
                genre = textEdit(cleanGenre, existing.genre, "Unknown Genre"),
                year = if (year == existing.year) null else year.takeIf { it > 0 }?.toString() ?: "",
                trackNumber = if (trackNumber == existing.trackNumber) null else trackNumber,
                discNumber = if (discNumber == existing.discNumber) null else discNumber,
                composer = rawEdit(cleanComposer, existing.composer),
                artwork = artworkEdit,
                artworkMime = edit.artworkMime.takeIf { artworkEdit != null && artworkEdit.isNotEmpty() }
            )

            val outcome = if (tagEdit.isEmpty) {
                TagWriteOutcome.Success
            } else {
                withContext(NonCancellable) {
                    audioFileTagWriter.applyEdit(existing.toDomain(), tagEdit)
                }
            }
            when (outcome) {
                TagWriteOutcome.Success -> Unit
                is TagWriteOutcome.Unsupported ->
                    return@withContext TrackEditResult.Failure(TrackEditFailure.UNSUPPORTED, outcome.reason)
                TagWriteOutcome.Denied ->
                    return@withContext TrackEditResult.Failure(TrackEditFailure.DENIED)
                TagWriteOutcome.NoSpace ->
                    return@withContext TrackEditResult.Failure(TrackEditFailure.NO_SPACE)
                TagWriteOutcome.Failed ->
                    return@withContext TrackEditResult.Failure(TrackEditFailure.FAILED)
            }

            musicDao.updateTrackMetadata(
                id = id,
                title = cleanTitle,
                artist = cleanArtist,
                album = cleanAlbum,
                albumArtist = cleanAlbumArtist,
                genre = cleanGenre,
                year = year,
                trackNumber = trackNumber,
                discNumber = discNumber,
                composer = cleanComposer,
                dateModified = System.currentTimeMillis() / 1000
            )
            ArtworkVersionStore.bump(existing.uri)
            musicDao.getTrackByIdOnce(id)?.toDomain()
                ?.let { TrackEditResult.Success(it) }
                ?: TrackEditResult.Failure(TrackEditFailure.FAILED)
        }

    /**
     * Delete through the MediaStore URI, not a stale DATA filesystem path. On modern
     * Android the platform can surface recoverable consent as a SecurityException;
     * callers receive false and the Room/playlist records are deliberately retained.
     */
    override suspend fun deleteTrack(track: Track): Boolean = withContext(Dispatchers.IO) {
        if (!track.id.startsWith("local:")) return@withContext false
        val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return@withContext false
        // Never reconstruct a URI from the numeric ID: IDs can be volume-scoped. Use the
        // exact MediaStore URI carried by the track and refuse malformed/mismatched identities.
        val uri = runCatching { Uri.parse(track.uri) }.getOrNull()
            ?.takeIf {
                it.scheme.equals("content", ignoreCase = true) &&
                    it.authority.equals("media", ignoreCase = true) &&
                    it.lastPathSegment?.toLongOrNull() == mediaStoreId
            } ?: return@withContext false

        try {
            val initialCursor = context.contentResolver.query(
                uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null
            ) ?: return@withContext false // Unknown provider/query state is not proof of absence.
            val queriedBefore = initialCursor.use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getLong(0)
            }
            if (queriedBefore == null) {
                // The underlying item is already gone; finish removing the stale app entry.
                withContext(NonCancellable) {
                    musicDatabase.withTransaction {
                        musicDao.deleteTracksByIds(listOf(track.id))
                        playlistDao.removeTrackFromAllPlaylists(track.id)
                    }
                }
                return@withContext true
            }
            if (queriedBefore != mediaStoreId) return@withContext false

            // Once deletion starts, complete the verify-and-DB-commit section even if the
            // caller navigates away or its coroutine is cancelled. Otherwise a successful
            // provider delete could leave a stale Room row/playlist reference.
            withContext(NonCancellable) deleteAndCommit@ {
                // Some providers can throw after changing the item. Don't infer the final
                // state from the exception alone; query the exact URI and clean Room only if
                // absence is confirmed.
                runCatching { context.contentResolver.delete(uri, null, null) }
                val remains = try {
                    context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.Audio.Media._ID),
                        null,
                        null,
                        null
                    )?.use { it.moveToFirst() }
                } catch (_: Exception) {
                    null
                } ?: return@deleteAndCommit false // Unknown is not the same as deleted.
                if (remains) return@deleteAndCommit false

                // Storage deletion is verified. Only now remove the track and playlist refs.
                musicDatabase.withTransaction {
                    musicDao.deleteTracksByIds(listOf(track.id))
                    playlistDao.removeTrackFromAllPlaylists(track.id)
                }
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            // Permission/consent wasn't granted. Preserve local records for a retry.
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun getAllDeviceFolderNames(): Flow<List<String>> =
        combine(
            musicDao.getAllFolders(),
            preferencesManager.excludedFolders
        ) { dbFolders, excluded ->
            val onDevice = localMusicSource.scanAllFolderNames()
            val all = (dbFolders.map { it.folder } + excluded + onDevice + LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS)
                .filter { it.isNotBlank() }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
            all
        }.flowOn(Dispatchers.IO)

    override suspend fun hideTrack(trackId: String) {
        preferencesManager.hideTrack(trackId)
    }

    override suspend fun unhideTrack(trackId: String) {
        preferencesManager.unhideTrack(trackId)
    }

    override suspend fun unhideAllTracks() {
        preferencesManager.unhideAllTracks()
    }

    override suspend fun excludeFolder(folder: String) {
        preferencesManager.excludeFolder(folder)
        refreshTracks()
    }
}
