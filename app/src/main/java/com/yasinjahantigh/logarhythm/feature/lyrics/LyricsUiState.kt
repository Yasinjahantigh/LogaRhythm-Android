package com.yasinjahantigh.logarhythm.feature.lyrics

import com.yasinjahantigh.logarhythm.domain.model.Lyrics

data class LyricsUiState(
    val lyrics: Lyrics? = null,
    val isLoading: Boolean = false,
    val activeLineIndex: Int = -1,
    val isOnlineEnabled: Boolean = false,
    val message: String? = null
)
