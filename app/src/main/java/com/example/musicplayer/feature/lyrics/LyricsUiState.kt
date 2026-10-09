package com.example.musicplayer.feature.lyrics

import com.example.musicplayer.domain.model.Lyrics

data class LyricsUiState(
    val lyrics: Lyrics? = null,
    val isLoading: Boolean = false,
    val activeLineIndex: Int = -1,
    val isOnlineEnabled: Boolean = false,
    val message: String? = null
)
