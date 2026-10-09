package com.example.musicplayer.domain.model

/**
 * Domain model representing a musical genre.
 */
data class Genre(
    val id: String,
    val name: String,
    val trackCount: Int
)
