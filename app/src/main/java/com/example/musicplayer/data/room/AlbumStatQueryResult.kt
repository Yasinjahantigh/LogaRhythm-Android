package com.example.musicplayer.data.room

data class AlbumStatQueryResult(
    val album: String,
    val artist: String,
    val artworkUri: String?,
    val playCount: Int,
    val totalListenedMs: Long,
    val trackCount: Int
)
