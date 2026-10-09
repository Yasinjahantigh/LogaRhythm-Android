package com.example.musicplayer.domain.source

import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track

/**
 * Abstraction for retrieving lyrics from a specific provider (e.g. local .lrc file, online API).
 */
interface LyricsSource {
    val name: String
    suspend fun getLyrics(track: Track): Lyrics?
}
