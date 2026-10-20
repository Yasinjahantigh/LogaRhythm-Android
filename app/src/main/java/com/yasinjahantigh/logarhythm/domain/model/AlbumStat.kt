package com.yasinjahantigh.logarhythm.domain.model

data class AlbumStat(
    val album: String,
    val artist: String,
    val artworkUri: String?,
    val playCount: Int,
    val totalListenTimeMs: Long,
    val trackCount: Int
)
