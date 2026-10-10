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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localMusicSource: LocalMusicSource,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val preferencesManager: PreferencesManager,
    private val audioFileTagWriter: AudioFileTagWriter
) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return musicDao.getAllTracks().map { list ->
            list.map { it.toDomain() }
        }
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
        }
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

    override fun getArtists(): Flow<List<Artist>> {
        return musicDao.getAllArtists().map { list ->
            list.map {
                Artist(
                    id = "artist:${it.artist}",
                    name = it.artist,
                    trackCount = it.trackCount,
                    albumCount = it.albumCount
                )
            }
        }
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

    override fun getTracksByArtist(artist: String): Flow<List<Track>> {
        return musicDao.getTracksByArtist(artist).map { list ->
            list.map { it.toDomain() }
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

    override fun getArtistByName(artist: String): Flow<Artist?> {
        return musicDao.getArtistByName(artist).map { result ->
            result?.let {
                Artist(
                    id = "artist:${it.artist}",
                    name = it.artist,
                    trackCount = it.trackCount,
                    albumCount = it.albumCount
                )
            }
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
        val cleanTitle = title.trim().ifBlank { "Unknown Title" }
        val cleanArtist = artist.trim().ifBlank { "Unknown Artist" }
        val cleanAlbum = album.trim().ifBlank { "Unknown Album" }
        val cleanGenre = genre.trim().ifBlank { "Unknown Genre" }

        val existingEntity = musicDao.getTrackByIdOnce(id)

        musicDao.updateTrackMetadata(id, cleanTitle, cleanArtist, cleanAlbum, cleanGenre)
        val entity = musicDao.getTrackByIdOnce(id)
        val updatedTrack = entity?.toDomain()

        if (updatedTrack != null) {
            try {
                audioFileTagWriter.writeTagsToFileAndMediaStore(
                    track = updatedTrack,
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = cleanAlbum,
                    genre = cleanGenre
                )
            } catch (_: Exception) {
            }
        }

        updatedTrack
    }

    override suspend fun deleteTrack(track: Track): Boolean = withContext(Dispatchers.IO) {
        var fileDeleted = false
        // 1. Try deleting physical file from disk if path can be resolved
        val audioFilePath = resolveAudioFilePath(track)
        if (!audioFilePath.isNullOrBlank()) {
            try {
                val file = File(audioFilePath)
                if (file.exists()) {
                    fileDeleted = file.delete()
                }
            } catch (_: Exception) {
            }
        }

        // 2. Try deleting from MediaStore
        if (track.id.startsWith("local:")) {
            val mediaStoreId = track.id.removePrefix("local:").toLongOrNull()
            if (mediaStoreId != null) {
                try {
                    val uri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaStoreId.toString()
                    )
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                }
            }
        }

        // 3. Remove track from Room database and all playlists
        musicDao.deleteTracksByIds(listOf(track.id))
        playlistDao.removeTrackFromAllPlaylists(track.id)

        true
    }

    private fun resolveAudioFilePath(track: Track): String? {
        if (!track.id.startsWith("local:")) return null
        val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return null

        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(mediaStoreId.toString())

        try {
            val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (dataCol != -1 && cursor.moveToFirst()) {
                    return cursor.getString(dataCol)
                }
            }
        } catch (_: Exception) {
        }

        return null
    }
}
