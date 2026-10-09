package com.example.musicplayer.data.room

data class ArtistStatQueryResult(
    val artist: String,
    val playCount: Int,
    val totalListenedMs: Long,
    val trackCount: Int
)
