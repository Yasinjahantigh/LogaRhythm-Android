package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getPlaylistById(playlistId: Long): Flow<Playlist?>
    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)
    suspend fun reorderPlaylistTracks(playlistId: Long, fromIndex: Int, toIndex: Int)
    fun isTrackFavorite(trackId: String): Flow<Boolean>
    suspend fun toggleFavorite(trackId: String)
}
