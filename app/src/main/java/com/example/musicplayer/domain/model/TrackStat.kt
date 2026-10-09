package com.example.musicplayer.domain.model

data class TrackStat(
    val track: Track,
    val playCount: Int,
    val totalListenTimeMs: Long
)
