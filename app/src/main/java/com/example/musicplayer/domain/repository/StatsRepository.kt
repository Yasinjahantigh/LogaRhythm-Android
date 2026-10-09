package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.AlbumStat
import com.example.musicplayer.domain.model.ArtistStat
import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackStat
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    suspend fun recordPlayEvent(event: PlayEvent)

    fun getPlayEventsForTrack(trackId: String): Flow<List<PlayEvent>>

    fun getAllPlayEvents(): Flow<List<PlayEvent>>

    fun getTotalListenTimeMs(sinceTimestamp: Long = 0L): Flow<Long>

    fun getTopTracks(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<TrackStat>>

    fun getTopArtists(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<ArtistStat>>

    fun getTopAlbums(sinceTimestamp: Long = 0L, limit: Int = 50): Flow<List<AlbumStat>>

    fun getRecentlyPlayedTracks(limit: Int = 20): Flow<List<Track>>

    fun getMostPlayedTracks(limit: Int = 20): Flow<List<Track>>
}
