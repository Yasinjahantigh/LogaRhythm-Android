package com.yasinjahantigh.logarhythm.feature.home

import com.yasinjahantigh.logarhythm.domain.model.Track

/** Pure transformations for Home's library-discovery sections. */
internal object HomeTrackCollections {
    fun recentlyAdded(tracks: List<Track>, limit: Int = 10): List<Track> {
        if (limit <= 0 || tracks.isEmpty()) return emptyList()
        return tracks
            .sortedWith(compareByDescending<Track> { it.dateAdded }.thenBy { it.title.lowercase() })
            .take(limit)
    }
}
