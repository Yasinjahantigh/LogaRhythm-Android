package com.example.musicplayer.data.room

data class AlbumQueryResult(
    val album: String,
    val artist: String,
    val trackCount: Int,
    val artworkUri: String?
)
