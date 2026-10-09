package com.example.musicplayer.data.room

data class TrackStatQueryResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String?,
    val sourceType: String,
    val uri: String,
    val dateModified: Long = 0L,
    val folder: String = "",
    val genre: String = "",
    val playCount: Int,
    val totalListenedMs: Long
)
