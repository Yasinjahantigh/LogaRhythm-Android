package com.yasinjahantigh.logarhythm.domain.source

import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track

/**
 * Abstraction for retrieving lyrics from a specific provider (e.g. local .lrc file, online API).
 */
interface LyricsSource {
    val name: String
    suspend fun getLyrics(track: Track): Lyrics?
}
