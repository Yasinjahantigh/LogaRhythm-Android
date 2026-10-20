package com.yasinjahantigh.logarhythm.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sleep timer countdown and auto-pause behavior directly inside PlaybackService.
 * Supports both duration countdown (minutes) and track countdown (number of tracks).
 * Keeps running in background even if the app UI / Activity is closed.
 */
@Singleton
class SleepTimerManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null
    private var attachedPlayer: ExoPlayer? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _remainingTracks = MutableStateFlow<Int?>(null)
    val remainingTracks: StateFlow<Int?> = _remainingTracks.asStateFlow()

    private val _isEndOfTrack = MutableStateFlow(false)
    val isEndOfTrack: StateFlow<Boolean> = _isEndOfTrack.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val tracks = _remainingTracks.value
            if (tracks != null) {
                val newCount = tracks - 1
                if (newCount <= 0) {
                    attachedPlayer?.pause()
                    cancelTimer()
                } else {
                    _remainingTracks.value = newCount
                    _isEndOfTrack.value = (newCount == 1)
                }
            } else if (_isEndOfTrack.value) {
                // End of track reached; pause playback and clear timer
                attachedPlayer?.pause()
                cancelTimer()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if ((_isEndOfTrack.value || _remainingTracks.value != null) && playbackState == Player.STATE_ENDED) {
                attachedPlayer?.pause()
                cancelTimer()
            }
        }
    }

    fun attachPlayer(player: ExoPlayer) {
        detachPlayer()
        attachedPlayer = player
        player.addListener(playerListener)
    }

    fun detachPlayer() {
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = null
    }

    fun startTimer(minutes: Int) {
        cancelTimer()
        _isEndOfTrack.value = false
        _remainingTracks.value = null
        val initialRemaining = minutes * 60 * 1000L
        _remainingMs.value = initialRemaining
        val targetEndTime = System.currentTimeMillis() + initialRemaining

        countdownJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = (targetEndTime - now).coerceAtLeast(0L)
                _remainingMs.value = remaining

                if (remaining <= 0L) {
                    attachedPlayer?.pause()
                    cancelTimer()
                    break
                }
                delay(1000L)
            }
        }
    }

    fun startTimerTracks(count: Int) {
        cancelTimer()
        val safeCount = count.coerceAtLeast(1)
        _remainingTracks.value = safeCount
        _isEndOfTrack.value = (safeCount == 1)

        val player = attachedPlayer
        val currentTrackRemaining = if (player != null && player.duration > 0L) {
            (player.duration - player.currentPosition).coerceAtLeast(0L)
        } else {
            0L
        }
        _remainingMs.value = currentTrackRemaining

        countdownJob = scope.launch {
            while (isActive && _remainingTracks.value != null) {
                val p = attachedPlayer
                if (p != null && p.duration > 0L) {
                    _remainingMs.value = (p.duration - p.currentPosition).coerceAtLeast(0L)
                } else {
                    _remainingMs.value = 0L
                }
                delay(500L)
            }
        }
    }

    fun startEndOfTrack() {
        startTimerTracks(1)
    }

    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
        _remainingMs.value = null
        _remainingTracks.value = null
        _isEndOfTrack.value = false
    }
}
