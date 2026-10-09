package com.example.musicplayer.feature.search

import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Track

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}
