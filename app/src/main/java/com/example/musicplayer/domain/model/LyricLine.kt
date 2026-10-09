package com.example.musicplayer.domain.model

/**
 * Single line of synced lyrics with its playback timestamp.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)
