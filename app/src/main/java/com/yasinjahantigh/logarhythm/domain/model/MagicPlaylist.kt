package com.yasinjahantigh.logarhythm.domain.model

/** A generated playlist preview. Nothing is persisted until the user taps save. */
data class MagicPlaylist(
    val name: String,
    val description: String,
    val tracks: List<Track>
)

enum class AiFailure {
    NOT_CONFIGURED,
    INVALID_KEY,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    BAD_RESPONSE,
    NO_TRACKS
}

sealed interface MagicPlaylistResult {
    data class Success(val playlist: MagicPlaylist) : MagicPlaylistResult
    data class Failure(val reason: AiFailure) : MagicPlaylistResult
}
