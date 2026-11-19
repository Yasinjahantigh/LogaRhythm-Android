package com.yasinjahantigh.logarhythm.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing an audio artist.
 */
@Immutable
data class Artist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)
