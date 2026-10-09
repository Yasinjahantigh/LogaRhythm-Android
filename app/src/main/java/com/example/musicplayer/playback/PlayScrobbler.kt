package com.example.musicplayer.playback

import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks listening time per track and scrobbles a PlayEvent to StatsRepository
 * once listening exceeds 30 seconds or 50% of track duration.
 */
@Singleton
class PlayScrobbler @Inject constructor(
    private val statsRepository: StatsRepository
) {
    private var currentTrackRecorded = false
    private var currentTrackListenedMs = 0L
    private var lastTickTimestamp = 0L

    fun onTrackChanged() {
        currentTrackRecorded = false
        currentTrackListenedMs = 0L
        lastTickTimestamp = System.currentTimeMillis()
    }

    fun onTick(isPlaying: Boolean, track: Track?, durationMs: Long, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        if (isPlaying && track != null) {
            val delta = if (lastTickTimestamp > 0L) (now - lastTickTimestamp).coerceIn(0L, 1000L) else 500L
            currentTrackListenedMs += delta
            checkRecordPlayEvent(track, durationMs, scope)
        }
        lastTickTimestamp = now
    }

    private fun checkRecordPlayEvent(track: Track, durationMs: Long, scope: CoroutineScope) {
        if (currentTrackRecorded) return
        val dur = if (track.durationMs > 0L) track.durationMs else durationMs
        val threshold = if (dur > 0L) minOf(30_000L, dur / 2) else 30_000L
        if (currentTrackListenedMs >= threshold) {
            currentTrackRecorded = true
            val listened = currentTrackListenedMs
            scope.launch(Dispatchers.IO) {
                statsRepository.recordPlayEvent(
                    PlayEvent(trackId = track.id, timestamp = System.currentTimeMillis(), listenedMs = listened)
                )
            }
        }
    }
}
