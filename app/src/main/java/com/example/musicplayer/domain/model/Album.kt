package com.example.musicplayer.domain.model

/**
 * Domain model representing an audio album.
 */
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val trackCount: Int,
    val artworkUri: String? = null
)
