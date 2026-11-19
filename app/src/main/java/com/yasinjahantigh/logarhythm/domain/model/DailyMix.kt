package com.yasinjahantigh.logarhythm.domain.model

import androidx.compose.runtime.Immutable

enum class MixTrackReason {
    FAVORITE,
    HEAVY_ROTATION,
    ARTIST_AFFINITY,
    GENRE_MOOD_MATCH,
    REDISCOVER_GEM,
    FRESH_ADDITION
}

/**
 * Represents an offline-generated smart mix for the current day.
 */
@Immutable
data class DailyMix(
    val id: String,
    val tracks: List<Track>,
    val seedDate: Long,
    val trackReasons: Map<String, MixTrackReason> = emptyMap()
)
