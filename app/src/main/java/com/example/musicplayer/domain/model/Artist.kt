package com.example.musicplayer.domain.model

/**
 * Domain model representing an audio artist.
 */
data class Artist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)
