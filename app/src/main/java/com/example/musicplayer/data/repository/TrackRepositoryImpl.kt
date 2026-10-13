package com.example.musicplayer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.musicplayer.core.utils.SearchUtils
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.data.room.MusicDao
import com.example.musicplayer.data.room.PlaylistDao
import com.example.musicplayer.data.room.TrackEntity
import com.example.musicplayer.data.sources.LocalMusicSource
import com.example.musicplayer.data.tagger.AudioFileTagWriter
import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Folder
import com.example.musicplayer.domain.model.Genre
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import com.example.musicplayer.domain.library.ArtistSplitter
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
    private val musicDatabase: com.example.musicplayer.data.room.MusicDatabase,
    private val preferencesManager: PreferencesManager,
    private val audioFileTagWriter: AudioFileTagWriter
) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return musicDao.getAllTracks().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getTrackById(id: String): Flow<Track?> {
        return musicDao.getTrackById(id).map { it?.toDomain() }
    }

    override fun searchTracks(query: String): Flow<List<Track>> {
        return musicDao.getAllTracks().map { allTracks ->
            allTracks.map { it.toDomain() }.filter { track ->
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
        return musicDao.getAllAlbums().map { list ->
            list.map {
                Album(
                    id = "album:${it.album}:${it.artist}",
                    name = it.album,
                    artist = it.artist,
                    trackCount = it.trackCount,
                    artworkUri = it.artworkUri
                )
            }
        }
    }

    /** Separators when multi-artist splitting is on, null when it is off. */
    private fun splitConfig(): Flow<List<String>?> =
        combine(preferencesManager.splitArtists, preferencesManager.artistSeparators) { on, raw ->
            if (on) ArtistSplitter.parseSeparators(raw) else null
        }

    override fun getArtists(): Flow<List<Artist>> = splitConfig().flatMapLatest { separators ->
        if (separators == null) {
            musicDao.getAllArtists().map { list ->
                list.map {
                    Artist(
                        id = "artist:${it.artist}",
                        name = it.artist,
                        trackCount = it.trackCount,
                        albumCount = it.albumCount
                    )
                }
            }
        } else {
            musicDao.getAllTracks().map { entities -> buildArtists(entities, separators) }
                .flowOn(Dispatchers.Default)
        }
    }

    private fun buildArtists(entities: List<TrackEntity>, separators: List<String>): List<Artist> {
        class Acc(val name: String) { var tracks = 0; val albums = HashSet<String>() }
        val byName = HashMap<String, Acc>()
        for (entity in entities) {
            for (name in ArtistSplitter.split(entity.artist, separators)) {
                val acc = byName.getOrPut(name.lowercase()) { Acc(name) }
                acc.tracks++
                acc.albums.add(entity.album)
            }
        }
        return byName.values
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { Artist(id = "artist:${it.name}", name = it.name, trackCount = it.tracks, albumCount = it.albums.size) }
    }

    override fun getFolders(): Flow<List<Folder>> {
        return musicDao.getAllFolders().map { list ->
            list.map {
                Folder(
                    id = "folder:${it.folder}",
                    name = it.folder,
                    trackCount = it.trackCount
                )
            }
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return musicDao.getAllGenres().map { list ->
            list.map {
                Genre(
                    id = "genre:${it.genre}",
                    name = it.genre,
                    trackCount = it.trackCount
                )
            }
        }
    }

    override fun getTracksByAlbum(album: String): Flow<List<Track>> {
        return musicDao.getTracksByAlbum(album).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTracksByArtist(artist: String): Flow<List<Track>> =
        splitConfig().flatMapLatest { separators ->
            if (separators == null) {
                musicDao.getTracksByArtist(artist).map { list -> list.map { it.toDomain() } }
            } else {
                musicDao.getAllTracks().map { entities ->
                    entities.filter { e ->
                        ArtistSplitter.split(e.artist, separators).any { it.equals(artist, ignoreCase = true) }
                    }.map { it.toDomain() }
                }.flowOn(Dispatchers.Default)
            }
        }

    override fun getTracksByFolder(folder: String): Flow<List<Track>> {
        return musicDao.getTracksByFolder(folder).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTracksByGenre(genre: String): Flow<List<Track>> {
        return musicDao.getTracksByGenre(genre).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getAlbumByName(album: String): Flow<Album?> {
        return musicDao.getAlbumByName(album).map { result ->
            result?.let {
                Album(
                    id = "album:${it.album}:${it.artist}",
                    name = it.album,
                    artist = it.artist,
                    trackCount = it.trackCount,
                    artworkUri = it.artworkUri
                )
            }
        }
    }

    override fun getArtistByName(artist: String): Flow<Artist?> =
        splitConfig().flatMapLatest { separators ->
            if (separators == null) {
                musicDao.getArtistByName(artist).map { result ->
                    result?.let {
                        Artist(
                            id = "artist:${it.artist}",
                            name = it.artist,
                            trackCount = it.trackCount,
                            albumCount = it.albumCount
                        )
                    }
                }
            } else {
                getArtists().map { list -> list.firstOrNull { it.name.equals(artist, ignoreCase = true) } }
            }
        }

    override fun getGenreByName(genre: String): Flow<Genre?> {
        return musicDao.getGenreByName(genre).map { result ->
            result?.let {
                Genre(
                    id = "genre:${it.genre}",
                    name = it.genre,
                    trackCount = it.trackCount
                )
            }
        }
    }

    override suspend fun refreshTracks() = withContext(Dispatchers.IO) {
        val minSec = preferencesManager.minTrackLengthSec.first()
        val excludedFolders = preferencesManager.excludedFolders.first()
        val scanned = localMusicSource.scanTracks(
            minDurationMs = minSec * 1000L,
            excludedFolders = excludedFolders
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
                        genre = current.genre
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

            musicDao.updateTrackMetadata(id, cleanTitle, cleanArtist, cleanAlbum, cleanGenre)
            musicDao.getTrackByIdOnce(id)?.toDomain()
        }
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

}
