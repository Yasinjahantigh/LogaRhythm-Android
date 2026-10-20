package com.yasinjahantigh.logarhythm.playback

import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.stats.ListenPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw playback into honest listening history:
 *  - a play is logged as soon as the play threshold is crossed, so it shows up immediately;
 *  - that event's listened time is then corrected to the real total (it used to stay at ~30s,
 *    which badly understated listening time);
 *  - leaving a track before the threshold is logged as a skip, which the taste engine uses to
 *    stop recommending music the listener keeps passing over.
 */
@Singleton
class PlayScrobbler @Inject constructor(
    private val statsRepository: StatsRepository
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentTrackId: String? = null
    private var currentDurationMs = 0L
    private var currentTrackListenedMs = 0L
    private var lastTickTimestamp = 0L

    private var recordedTimestamp = 0L
    private var lastFlushedListenedMs = 0L

    /**
     * Avoids resetting listen progress when a queue is rebuilt around the same playing track.
     * Explicitly replaying a track from the UI passes force=true to begin a new listening session.
     */
    fun onTrackChanged(trackId: String? = null, force: Boolean = false) {
        if (!force && trackId == currentTrackId) return
        finalizeCurrent()
        currentTrackId = trackId
        currentDurationMs = 0L
        currentTrackListenedMs = 0L
        recordedTimestamp = 0L
        lastFlushedListenedMs = 0L
        lastTickTimestamp = System.currentTimeMillis()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTick(isPlaying: Boolean, track: Track?, durationMs: Long, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        if (currentTrackId == null && track != null) currentTrackId = track.id
        if (isPlaying && track != null && track.id == currentTrackId) {
            val delta = if (lastTickTimestamp > 0L) (now - lastTickTimestamp).coerceIn(0L, 1000L) else 500L
            currentTrackListenedMs += delta
            currentDurationMs = if (track.durationMs > 0L) track.durationMs else durationMs
            maybeRecordPlay(track.id, now)
            maybeFlush(track.id)
        }
        lastTickTimestamp = now
    }

    private fun maybeRecordPlay(trackId: String, now: Long) {
        if (recordedTimestamp != 0L) return
        if (currentTrackListenedMs < ListenPolicy.playThresholdMs(currentDurationMs)) return
        recordedTimestamp = now
        lastFlushedListenedMs = currentTrackListenedMs
        val listened = currentTrackListenedMs
        val timestamp = now
        ioScope.launch {
            statsRepository.recordPlayEvent(
                PlayEvent(trackId = trackId, timestamp = timestamp, listenedMs = listened)
            )
        }
    }

    /** Keeps the stored listened time close to reality even if the process is killed mid-track. */
    private fun maybeFlush(trackId: String) {
        if (recordedTimestamp == 0L) return
        if (currentTrackListenedMs - lastFlushedListenedMs < FLUSH_INTERVAL_MS) return
        lastFlushedListenedMs = currentTrackListenedMs
        val listened = currentTrackListenedMs
        val timestamp = recordedTimestamp
        ioScope.launch { statsRepository.updatePlayEventListened(trackId, timestamp, listened) }
    }

    private fun finalizeCurrent() {
        val trackId = currentTrackId ?: return
        val listened = currentTrackListenedMs
        if (recordedTimestamp != 0L) {
            if (listened > lastFlushedListenedMs) {
                val timestamp = recordedTimestamp
                ioScope.launch { statsRepository.updatePlayEventListened(trackId, timestamp, listened) }
            }
            return
        }
        if (ListenPolicy.classify(listened, currentDurationMs) == ListenPolicy.Outcome.SKIP) {
            val timestamp = System.currentTimeMillis()
            ioScope.launch {
                statsRepository.recordPlayEvent(
                    PlayEvent(trackId = trackId, timestamp = timestamp, listenedMs = listened, skipped = true)
                )
            }
        }
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 20_000L
    }
}
