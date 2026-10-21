package com.yasinjahantigh.logarhythm.domain.model

/**
 * Represents an offline-generated smart mix for the current day.
 */
data class DailyMix(
    val id: String,
    val tracks: List<Track>,
    val seedDate: Long
)
