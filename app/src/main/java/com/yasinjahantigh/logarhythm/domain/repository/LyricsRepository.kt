package com.yasinjahantigh.logarhythm.domain.repository

import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun getLyrics(track: Track): Flow<Lyrics?>
    suspend fun loadLyrics(track: Track): Lyrics?
    suspend fun saveLyrics(track: Track, content: String, source: String): Boolean
    suspend fun deleteLyrics(trackId: String)
    /** Hides lyrics in-app and, when [removeFromFile] is set, strips the copy embedded in the audio file. Returns false only if an embedded copy could not be removed. */
    suspend fun deleteLyrics(track: Track, removeFromFile: Boolean = true): Boolean
    suspend fun resetLyricsToDefault(trackId: String)
    suspend fun fetchOnlineLyrics(track: Track): Lyrics?
    suspend fun searchLyricsCandidates(title: String, artist: String): List<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate>

    /** Per-song timing correction in ms (positive = lyrics appear earlier); 0 when none is set. */
    fun getOffset(trackId: String): Flow<Long>
    suspend fun setOffset(trackId: String, offsetMs: Long)
    /** Ids of songs whose lyrics the user hid; they can be restored with [resetLyricsToDefault]. */
    fun getHiddenTrackIds(): Flow<List<String>>
}
