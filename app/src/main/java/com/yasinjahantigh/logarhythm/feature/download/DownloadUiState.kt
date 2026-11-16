package com.yasinjahantigh.logarhythm.feature.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult

data class DownloadSearchUiState(
    val searching: Boolean = false,
    val results: List<DownloadSearchResult> = emptyList(),
    val error: DownloadErrorKind? = null,
    val hasSearched: Boolean = false,
    val category: DownloadSearchCategory = DownloadSearchCategory.ALL
)

data class DownloadCollectionUiState(
    val header: DownloadSearchResult,
    val albums: List<DownloadSearchResult> = emptyList(),
    val tracks: List<DownloadSearchResult> = emptyList(),
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null
)
