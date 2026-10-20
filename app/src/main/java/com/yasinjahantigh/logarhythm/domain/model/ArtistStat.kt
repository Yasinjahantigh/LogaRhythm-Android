package com.yasinjahantigh.logarhythm.domain.model

data class ArtistStat(
    val artist: String,
    val playCount: Int,
    val totalListenTimeMs: Long,
    val trackCount: Int
)
