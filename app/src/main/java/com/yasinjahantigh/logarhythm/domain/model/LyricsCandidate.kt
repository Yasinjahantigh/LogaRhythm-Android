package com.yasinjahantigh.logarhythm.domain.model

data class LyricsCandidate(
    val id: String,
    val trackName: String,
    val artistName: String,
    val albumName: String = "",
    val durationMs: Long = 0L,
    val isSynced: Boolean = false,
    val sourceName: String = "Online",
    val lyrics: Lyrics
)
