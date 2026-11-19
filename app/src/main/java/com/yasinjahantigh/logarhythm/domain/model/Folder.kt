package com.yasinjahantigh.logarhythm.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing an audio folder.
 */
@Immutable
data class Folder(
    val id: String,
    val name: String,
    val trackCount: Int
)
