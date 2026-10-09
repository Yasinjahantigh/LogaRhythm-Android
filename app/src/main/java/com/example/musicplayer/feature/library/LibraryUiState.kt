package com.example.musicplayer.feature.library

import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Folder
import com.example.musicplayer.domain.model.Genre
import com.example.musicplayer.domain.model.Track

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
