package com.yasinjahantigh.logarhythm.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing a musical genre.
 */
@Immutable
data class Genre(
    val id: String,
    val name: String,
    val trackCount: Int
)
