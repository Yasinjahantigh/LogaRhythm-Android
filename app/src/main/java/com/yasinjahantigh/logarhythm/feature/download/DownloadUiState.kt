package com.yasinjahantigh.logarhythm.feature.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult

data class DownloadSearchUiState(
    val searching: Boolean = false,
    val results: List<DownloadSearchResult> = emptyList(),
    val error: DownloadErrorKind? = null,
    val hasSearched: Boolean = false
)
