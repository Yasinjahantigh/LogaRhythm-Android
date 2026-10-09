package com.example.musicplayer.domain.model

/**
 * Immutable domain model representing an audio track.
 * The id format must always be prefixed with source type, e.g. "local:123" or "web:abc".
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String?,
    val sourceType: SourceType,
    val uri: String,
    val dateModified: Long = 0L,
    val folder: String = "",
    val genre: String = "",
    /** MediaStore's original added timestamp, in seconds since epoch. */
    val dateAdded: Long = 0L
)
