package com.yasinjahantigh.logarhythm.feature.search

import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.Track

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}
