package com.example.musicplayer.data.repository

import com.example.musicplayer.core.utils.SearchUtils
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.data.room.MusicDao
import com.example.musicplayer.data.room.TrackEntity
import com.example.musicplayer.data.sources.LocalMusicSource
import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Folder
import com.example.musicplayer.domain.model.Genre
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val localMusicSource: LocalMusicSource,
    private val musicDao: MusicDao,
    private val preferencesManager: PreferencesManager
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
        val normalized = SearchUtils.normalize(query)
        return musicDao.searchTracks(query, normalized).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        val normalized = SearchUtils.normalize(query)
        return musicDao.searchAlbums(query, normalized).map { list ->
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

    override fun searchArtists(query: String): Flow<List<Artist>> {
        val normalized = SearchUtils.normalize(query)
        return musicDao.searchArtists(query, normalized).map { list ->
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

    override fun searchGenres(query: String): Flow<List<Genre>> {
        val normalized = SearchUtils.normalize(query)
        return musicDao.searchGenres(query, normalized).map { list ->
            list.map {
                Genre(
                    id = "genre:${it.genre}",
                    name = it.genre,
                    trackCount = it.trackCount
                )
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
                toUpdate.add(newEntity)
            }
        }

        val toDeleteIds = existing.map { it.id }.filter { it !in seenIds }

        if (toInsert.isNotEmpty()) {
            musicDao.insertTracks(toInsert)
        }
        if (toUpdate.isNotEmpty()) {
            musicDao.updateTracks(toUpdate)
        }
        if (toDeleteIds.isNotEmpty()) {
            musicDao.deleteTracksByIds(toDeleteIds)
        }
    }
}
