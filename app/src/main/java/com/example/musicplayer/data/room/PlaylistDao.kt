package com.example.musicplayer.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("""
        SELECT p.id, p.name, p.createdAt, p.isBuiltIn, COUNT(pt.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        GROUP BY p.id
        ORDER BY p.isBuiltIn DESC, p.name COLLATE NOCASE ASC
    """)
    fun getAllPlaylists(): Flow<List<PlaylistQueryResult>>

    @Query("""
        SELECT p.id, p.name, p.createdAt, p.isBuiltIn, COUNT(pt.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        WHERE p.id = :playlistId
        GROUP BY p.id
        LIMIT 1
    """)
    fun getPlaylistById(playlistId: Long): Flow<PlaylistQueryResult?>

    @Query("""
        SELECT t.*
        FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.orderIndex ASC
    """)
    fun getPlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT MAX(orderIndex) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxOrderIndex(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrack(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun removeTrackFromAllPlaylists(trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<PlaylistTrackEntity>)

    @Query("SELECT COUNT(*) > 0 FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    fun isTrackInPlaylist(playlistId: Long, trackId: String): Flow<Boolean>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistTrackIds(playlistId: Long): List<String>

    @Transaction
    suspend fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val current = getPlaylistTrackIds(playlistId).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        clearPlaylistTracks(playlistId)
        val entities = current.mapIndexed { idx, id ->
            PlaylistTrackEntity(playlistId = playlistId, trackId = id, orderIndex = idx)
        }
        insertPlaylistTracks(entities)
    }
}
