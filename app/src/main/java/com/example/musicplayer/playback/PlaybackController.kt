package com.example.musicplayer.playback

import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface wrapping media playback control, extending PlayerController
 * to maintain compatibility with Phase 1 contracts.
 */
interface PlaybackController : PlayerController {
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun playTrack(track: Track)
    fun playQueue(tracks: List<Track>, startIndex: Int = 0)
    fun skipToNext()
    fun skipToPrevious()
    fun stop()
}
