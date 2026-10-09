package com.example.musicplayer.domain.model

/**
 * Domain model representing an audio folder.
 */
data class Folder(
    val id: String,
    val name: String,
    val trackCount: Int
)
