package com.example.musicplayer.domain.model

/**
 * Playback event logged for statistics.
 * Explicitly separate from Track model.
 */
data class PlayEvent(
    val id: Long = 0L,
    val trackId: String,
    val timestamp: Long,
    val listenedMs: Long
)
