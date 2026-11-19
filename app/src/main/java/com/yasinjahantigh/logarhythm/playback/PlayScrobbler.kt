package com.yasinjahantigh.logarhythm.playback

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
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

@Singleton
class PlayScrobbler @Inject constructor(
    private val statsRepository: StatsRepository,
    private val preferencesManager: PreferencesManager
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isPrivateSession = false

    init {
        ioScope.launch {
            preferencesManager.privateSession.collect { isPrivateSession = it }
        }
    }

    private var currentTrackId: String? = null
    private var currentDurationMs = 0L
    private var currentTrackListenedMs = 0L
    private var lastTickTimestamp = 0L
    private var lastKnownPositionMs = 0L

    private var recordedTimestamp = 0L
    private var lastFlushedListenedMs = 0L

    /**
     * Avoids resetting listen progress when a queue is rebuilt around the same playing track.
     * Explicitly replaying a track from the UI or advancing passes force=true to begin a new listening session.
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
        lastKnownPositionMs = 0L
    }

    /**
     * Called when the current track repeats/loops back to the start (e.g. repeat mode ONE,
     * single-track playlist repeat, or track position discontinuity).
     * Finalizes the completed play and begins a fresh session for the next repetition.
     */
    fun onTrackLooped(trackId: String? = null) {
        val targetId = trackId ?: currentTrackId ?: return
        if (recordedTimestamp != 0L) {
            finalizeCurrent()
        }
        currentTrackId = targetId
        currentTrackListenedMs = 0L
        recordedTimestamp = 0L
        lastFlushedListenedMs = 0L
        lastTickTimestamp = System.currentTimeMillis()
        lastKnownPositionMs = 0L
    }

    fun onTick(isPlaying: Boolean, track: Track?, durationMs: Long, positionMs: Long = -1L) {
        val now = System.currentTimeMillis()
        if (currentTrackId == null && track != null) currentTrackId = track.id
        if (isPlaying && track != null && track.id == currentTrackId) {
            val dur = if (track.durationMs > 0L) track.durationMs else durationMs
            currentDurationMs = dur

            // Fallback loop detection: if position wrapped around from near-end / past-threshold
            // back to the start (positionMs in 0..3000ms and lastKnownPositionMs >= playThresholdMs)
            if (positionMs in 0L..3_000L &&
                dur > 10_000L &&
                lastKnownPositionMs >= ListenPolicy.playThresholdMs(dur)
            ) {
                onTrackLooped(track.id)
            }

            val delta = if (positionMs > lastKnownPositionMs && lastKnownPositionMs >= 0L) {
                (positionMs - lastKnownPositionMs).coerceIn(0L, 5_000L)
            } else {
                if (lastTickTimestamp > 0L) (now - lastTickTimestamp).coerceIn(0L, 1000L) else 500L
            }
            currentTrackListenedMs += delta
            if (positionMs >= 0L) {
                lastKnownPositionMs = positionMs
            }
            maybeRecordPlay(track.id, now)
            maybeFlush(track.id)
        }
        lastTickTimestamp = now
    }

    private fun maybeRecordPlay(trackId: String, now: Long) {
        if (isPrivateSession) return
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
        if (isPrivateSession) return
        if (recordedTimestamp == 0L) return
        if (currentTrackListenedMs - lastFlushedListenedMs < FLUSH_INTERVAL_MS) return
        lastFlushedListenedMs = currentTrackListenedMs
        val listened = currentTrackListenedMs
        val timestamp = recordedTimestamp
        ioScope.launch { statsRepository.updatePlayEventListened(trackId, timestamp, listened) }
    }

    private fun finalizeCurrent() {
        if (isPrivateSession) return
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
