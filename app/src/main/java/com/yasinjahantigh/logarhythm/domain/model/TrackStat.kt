package com.yasinjahantigh.logarhythm.domain.model

data class TrackStat(
    val track: Track,
    val playCount: Int,
    val totalListenTimeMs: Long
)
