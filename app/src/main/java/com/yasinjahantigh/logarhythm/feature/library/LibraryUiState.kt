package com.yasinjahantigh.logarhythm.feature.library

import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.Folder
import com.yasinjahantigh.logarhythm.domain.model.Genre
import com.yasinjahantigh.logarhythm.domain.model.Track

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val sortOption: SortOption = SortOption(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasPermission: Boolean = false
)
