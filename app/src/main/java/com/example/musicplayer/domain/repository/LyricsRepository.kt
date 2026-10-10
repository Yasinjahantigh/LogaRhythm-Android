package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun getLyrics(track: Track): Flow<Lyrics?>
    suspend fun loadLyrics(track: Track): Lyrics?
    suspend fun saveLyrics(track: Track, content: String, source: String): Boolean
    suspend fun deleteLyrics(trackId: String)
    suspend fun deleteLyrics(track: Track)
    suspend fun resetLyricsToDefault(trackId: String)
    suspend fun fetchOnlineLyrics(track: Track): Lyrics?
    suspend fun fetchOnlineLyricsWithCustomQuery(track: Track, title: String, artist: String): Lyrics?
    suspend fun searchLyricsCandidates(title: String, artist: String): List<com.example.musicplayer.domain.model.LyricsCandidate>
}
