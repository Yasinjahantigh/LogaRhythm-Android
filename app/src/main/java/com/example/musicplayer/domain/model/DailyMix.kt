package com.example.musicplayer.domain.model

/**
 * Represents an offline-generated smart mix for the current day.
 */
data class DailyMix(
    val id: String,
    val title: String,
    val description: String,
    val tracks: List<Track>,
    val seedDate: Long
)
