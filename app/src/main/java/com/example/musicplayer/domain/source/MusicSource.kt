package com.example.musicplayer.domain.source

import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for any audio track provider (local MediaStore, future web source, etc.).
 */
interface MusicSource {
    val sourceType: SourceType

    fun getTracks(): Flow<List<Track>>

    suspend fun getTrackById(id: String): Track?

    suspend fun searchTracks(query: String): List<Track>

    suspend fun getPlaybackUri(track: Track): String
}
