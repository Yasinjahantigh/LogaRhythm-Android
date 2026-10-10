package com.example.musicplayer.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE sourceType = :sourceType")
    suspend fun getTracksBySourceType(sourceType: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    fun getTrackById(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackByIdOnce(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIdsOnce(ids: List<String>): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%'
           OR genre LIKE '%' || :query || '%'
           OR REPLACE(REPLACE(title, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
           OR REPLACE(REPLACE(artist, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
           OR REPLACE(REPLACE(album, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
           OR REPLACE(REPLACE(genre, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchTracks(query: String, normalizedQuery: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT album, artist, COUNT(*) AS trackCount, MIN(artworkUri) AS artworkUri 
        FROM tracks 
        WHERE album LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%'
           OR REPLACE(REPLACE(album, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
           OR REPLACE(REPLACE(artist, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
        GROUP BY album, artist 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun searchAlbums(query: String, normalizedQuery: String): Flow<List<AlbumQueryResult>>

    @Query("""
        SELECT artist, COUNT(*) AS trackCount, COUNT(DISTINCT album) AS albumCount 
        FROM tracks 
        WHERE artist LIKE '%' || :query || '%'
           OR REPLACE(REPLACE(artist, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
        GROUP BY artist 
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun searchArtists(query: String, normalizedQuery: String): Flow<List<ArtistQueryResult>>

    @Query("""
        SELECT genre, COUNT(*) AS trackCount 
        FROM tracks 
        WHERE genre != '' AND (
            genre LIKE '%' || :query || '%'
            OR REPLACE(REPLACE(genre, 'ي', 'ی'), 'ك', 'ک') LIKE '%' || :normalizedQuery || '%'
        )
        GROUP BY genre 
        ORDER BY genre COLLATE NOCASE ASC
    """)
    fun searchGenres(query: String, normalizedQuery: String): Flow<List<GenreQueryResult>>

    @Query("""
        SELECT album, artist, COUNT(*) AS trackCount, MIN(artworkUri) AS artworkUri 
        FROM tracks 
        GROUP BY album, artist 
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAllAlbums(): Flow<List<AlbumQueryResult>>

    @Query("""
        SELECT artist, COUNT(*) AS trackCount, COUNT(DISTINCT album) AS albumCount 
        FROM tracks 
        GROUP BY artist 
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun getAllArtists(): Flow<List<ArtistQueryResult>>

    @Query("""
        SELECT folder, COUNT(*) AS trackCount 
        FROM tracks 
        WHERE folder != '' 
        GROUP BY folder 
        ORDER BY folder COLLATE NOCASE ASC
    """)
    fun getAllFolders(): Flow<List<FolderQueryResult>>

    @Query("""
        SELECT genre, COUNT(*) AS trackCount 
        FROM tracks 
        WHERE genre != '' 
        GROUP BY genre 
        ORDER BY genre COLLATE NOCASE ASC
    """)
    fun getAllGenres(): Flow<List<GenreQueryResult>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folder = :folder ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByFolder(folder: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun getTracksByGenre(genre: String): Flow<List<TrackEntity>>

    @Query("""
        SELECT album, artist, COUNT(*) AS trackCount, MIN(artworkUri) AS artworkUri 
        FROM tracks 
        WHERE album = :album
        GROUP BY album, artist 
        LIMIT 1
    """)
    fun getAlbumByName(album: String): Flow<AlbumQueryResult?>

    @Query("""
        SELECT artist, COUNT(*) AS trackCount, COUNT(DISTINCT album) AS albumCount 
        FROM tracks 
        WHERE artist = :artist 
        GROUP BY artist 
        LIMIT 1
    """)
    fun getArtistByName(artist: String): Flow<ArtistQueryResult?>

    @Query("""
        SELECT genre, COUNT(*) AS trackCount 
        FROM tracks 
        WHERE genre = :genre 
        GROUP BY genre 
        LIMIT 1
    """)
    fun getGenreByName(genre: String): Flow<GenreQueryResult?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, genre = :genre WHERE id = :id")
    suspend fun updateTrackMetadata(id: String, title: String, artist: String, album: String, genre: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<String>)

    /** Apply a completed scan atomically so a database error cannot leave a half-synced library. */
    @Transaction
    suspend fun syncLocalTracks(
        toInsert: List<TrackEntity>,
        toUpdate: List<TrackEntity>,
        toDeleteIds: List<String>
    ) {
        if (toInsert.isNotEmpty()) insertTracks(toInsert)
        if (toUpdate.isNotEmpty()) updateTracks(toUpdate)
        if (toDeleteIds.isNotEmpty()) deleteTracksByIds(toDeleteIds)
    }

    @Query("SELECT * FROM play_events WHERE trackId = :trackId ORDER BY timestamp DESC")
    fun getPlayEventsForTrack(trackId: String): Flow<List<PlayEventEntity>>

    @Query("SELECT * FROM play_events ORDER BY timestamp DESC")
    fun getAllPlayEvents(): Flow<List<PlayEventEntity>>

    @Insert
    suspend fun insertPlayEvent(event: PlayEventEntity)

    /** Replaces the provisional listened time recorded at the play threshold with the final total. */
    @Query("UPDATE play_events SET listenedMs = :listenedMs WHERE trackId = :trackId AND timestamp = :timestamp AND skipped = 0")
    suspend fun updatePlayEventListened(trackId: String, timestamp: Long, listenedMs: Long)

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events")
    fun getTotalListenTimeMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events WHERE timestamp >= :sinceTimestamp")
    fun getTotalListenTimeMs(sinceTimestamp: Long): Flow<Long>

    @Query("""
        SELECT t.id, t.title, t.artist, t.album, t.durationMs, t.artworkUri, t.sourceType, t.uri, t.dateModified, t.folder, t.genre, t.dateAdded,
               COUNT(pe.id) AS playCount,
               COALESCE(SUM(pe.listenedMs), 0) AS totalListenedMs
        FROM play_events pe
        INNER JOIN tracks t ON pe.trackId = t.id
        WHERE pe.timestamp >= :sinceTimestamp AND pe.skipped = 0
        GROUP BY pe.trackId
        ORDER BY playCount DESC, totalListenedMs DESC
        LIMIT :limit
    """)
    fun getTopTracks(sinceTimestamp: Long, limit: Int = 50): Flow<List<TrackStatQueryResult>>

    @Query("""
        SELECT t.artist,
               COUNT(pe.id) AS playCount,
               COALESCE(SUM(pe.listenedMs), 0) AS totalListenedMs,
               COUNT(DISTINCT t.id) AS trackCount
        FROM play_events pe
        INNER JOIN tracks t ON pe.trackId = t.id
        WHERE pe.timestamp >= :sinceTimestamp AND pe.skipped = 0 AND t.artist != ''
        GROUP BY t.artist
        ORDER BY playCount DESC, totalListenedMs DESC
        LIMIT :limit
    """)
    fun getTopArtists(sinceTimestamp: Long, limit: Int = 50): Flow<List<ArtistStatQueryResult>>

    @Query("""
        SELECT t.album,
               t.artist,
               MIN(t.artworkUri) AS artworkUri,
               COUNT(pe.id) AS playCount,
               COALESCE(SUM(pe.listenedMs), 0) AS totalListenedMs,
               COUNT(DISTINCT t.id) AS trackCount
        FROM play_events pe
        INNER JOIN tracks t ON pe.trackId = t.id
        WHERE pe.timestamp >= :sinceTimestamp AND pe.skipped = 0 AND t.album != ''
        GROUP BY t.album, t.artist
        ORDER BY playCount DESC, totalListenedMs DESC
        LIMIT :limit
    """)
    fun getTopAlbums(sinceTimestamp: Long, limit: Int = 50): Flow<List<AlbumStatQueryResult>>

    @Query("""
        SELECT t.*
        FROM play_events pe
        INNER JOIN tracks t ON pe.trackId = t.id
        WHERE pe.skipped = 0
        GROUP BY pe.trackId
        ORDER BY MAX(pe.timestamp) DESC
        LIMIT :limit
    """)
    fun getRecentlyPlayedTracks(limit: Int = 20): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.*
        FROM play_events pe
        INNER JOIN tracks t ON pe.trackId = t.id
        WHERE pe.skipped = 0
        GROUP BY pe.trackId
        ORDER BY COUNT(pe.id) DESC, SUM(pe.listenedMs) DESC
        LIMIT :limit
    """)
    fun getMostPlayedTracks(limit: Int = 20): Flow<List<TrackEntity>>
}
