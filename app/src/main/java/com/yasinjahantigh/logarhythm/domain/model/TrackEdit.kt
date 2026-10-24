package com.yasinjahantigh.logarhythm.domain.model

/** Every editable tag plus an optional new cover. A null artwork keeps the current file cover. */
data class TrackEdit(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val composer: String,
    val artwork: ByteArray? = null,
    val artworkMime: String? = null,
    val removeArtwork: Boolean = false
)

enum class TrackEditFailure {
    NOT_LOCAL,
    UNSUPPORTED,
    DENIED,
    NO_SPACE,
    FAILED
}

sealed interface TrackEditResult {
    data class Success(val track: Track) : TrackEditResult
    data class Failure(
        val failure: TrackEditFailure,
        val detail: String? = null
    ) : TrackEditResult
}
