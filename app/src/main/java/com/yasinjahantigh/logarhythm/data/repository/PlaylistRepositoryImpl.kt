package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.room.PlaylistDao
import com.yasinjahantigh.logarhythm.data.room.PlaylistEntity
import com.yasinjahantigh.logarhythm.data.room.PlaylistTrackEntity
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            ensureFavoritesPlaylist()
        }
    }

    private suspend fun ensureFavoritesPlaylist() {
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = Playlist.FAVORITES_ID,
                name = Playlist.FAVORITES_NAME,
                isBuiltIn = true
            )
        )
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getPlaylistById(playlistId: Long): Flow<Playlist?> {
        return playlistDao.getPlaylistById(playlistId).map { it?.toDomain() }
    }

    override fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> {
        return playlistDao.getPlaylistTracks(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext -1L
        playlistDao.insertPlaylist(PlaylistEntity(name = trimmed))
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank() && playlistId != Playlist.FAVORITES_ID) {
            playlistDao.renamePlaylist(playlistId, trimmed)
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        if (playlistId != Playlist.FAVORITES_ID) {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        ensureFavoritesPlaylist()
        val nextIndex = (playlistDao.getMaxOrderIndex(playlistId) ?: -1) + 1
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                orderIndex = nextIndex
            )
        )
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistTrack(playlistId, trackId)
    }

    override suspend fun reorderPlaylistTracks(
        playlistId: Long,
        fromIndex: Int,
        toIndex: Int
    ) = withContext(Dispatchers.IO) {
        playlistDao.reorderPlaylist(playlistId, fromIndex, toIndex)
    }

    override fun isTrackFavorite(trackId: String): Flow<Boolean> {
        return playlistDao.isTrackInPlaylist(Playlist.FAVORITES_ID, trackId)
    }

    override suspend fun toggleFavorite(trackId: String) = withContext(Dispatchers.IO) {
        ensureFavoritesPlaylist()
        val isFav = playlistDao.isTrackInPlaylist(Playlist.FAVORITES_ID, trackId).first()
        if (isFav) {
            playlistDao.deletePlaylistTrack(Playlist.FAVORITES_ID, trackId)
        } else {
            val nextIndex = (playlistDao.getMaxOrderIndex(Playlist.FAVORITES_ID) ?: -1) + 1
            playlistDao.insertPlaylistTrack(
                PlaylistTrackEntity(
                    playlistId = Playlist.FAVORITES_ID,
                    trackId = trackId,
                    orderIndex = nextIndex
                )
            )
        }
    }

    override suspend fun addFavorites(trackIds: List<String>) = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) return@withContext
        ensureFavoritesPlaylist()
        var nextIndex = (playlistDao.getMaxOrderIndex(Playlist.FAVORITES_ID) ?: -1) + 1
        trackIds.distinct().forEach { trackId ->
            if (!playlistDao.isTrackInPlaylist(Playlist.FAVORITES_ID, trackId).first()) {
                playlistDao.insertPlaylistTrack(
                    PlaylistTrackEntity(
                        playlistId = Playlist.FAVORITES_ID,
                        trackId = trackId,
                        orderIndex = nextIndex++
                    )
                )
            }
        }
    }
}
