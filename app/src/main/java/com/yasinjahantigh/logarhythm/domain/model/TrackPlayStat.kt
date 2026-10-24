package com.yasinjahantigh.logarhythm.domain.model

/** Finished plays for one track, aggregated from the play history. */
data class TrackPlayStat(
    val trackId: String,
    val playCount: Int,
    val lastPlayedAt: Long
)
