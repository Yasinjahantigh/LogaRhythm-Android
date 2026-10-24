package com.yasinjahantigh.logarhythm.domain.model

/**
 * Everything the artist page shows. [topTracks] is filled by the repository from play history;
 * [appearsOn] holds collaborations where this artist is not the first credited name.
 */
data class ArtistProfile(
    val artist: Artist,
    val tracks: List<Track>,
    val albums: List<Album>,
    val appearsOn: List<Track>,
    val topTracks: List<Track> = emptyList()
)
