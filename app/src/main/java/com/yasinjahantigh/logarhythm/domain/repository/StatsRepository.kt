package com.yasinjahantigh.logarhythm.domain.repository

import com.yasinjahantigh.logarhythm.domain.model.AlbumStat
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    suspend fun recordPlayEvent(event: PlayEvent)
    /** Corrects a provisional event's listened time once the track has actually finished or been left. */
    suspend fun updatePlayEventListened(trackId: String, timestamp: Long, listenedMs: Long)

    fun getPlayEventsForTrack(trackId: String): Flow<List<PlayEvent>>

    fun getAllPlayEvents(): Flow<List<PlayEvent>>

    fun getTotalListenTimeMs(sinceTimestamp: Long = 0L): Flow<Long>

    fun getTopTracks(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<TrackStat>>

    fun getTopArtists(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<ArtistStat>>

    fun getTopAlbums(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<AlbumStat>>

    fun getRecentlyPlayedTracks(limit: Int = 20): Flow<List<Track>>

    fun getMostPlayedTracks(limit: Int = 20): Flow<List<Track>>
}
