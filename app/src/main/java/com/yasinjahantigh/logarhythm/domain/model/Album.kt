package com.yasinjahantigh.logarhythm.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing an audio album.
 */
@Immutable
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val trackCount: Int,
    val artworkUri: String? = null,
    val year: Int = 0
)
