package com.example.musicplayer.playback

import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller interface wrapping media playback operations.
 * UI components interact with audio playback strictly through this abstraction.
 */
interface PlayerController {
    val currentTrack: StateFlow<Track?>
    val isPlaying: StateFlow<Boolean>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val shuffle: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>
    val queue: StateFlow<List<Track>>

    // Phase 7: Extras
    val playbackSpeed: StateFlow<Float>
    val sleepTimerRemainingMs: StateFlow<Long?>
    val isSleepTimerEndOfTrack: StateFlow<Boolean>
    val equalizerState: StateFlow<EqualizerState>

    fun play(list: List<Track>, startIndex: Int = 0)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()

    fun playNext(track: Track)
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int): Track?
    fun insertIntoQueue(index: Int, track: Track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun playQueueItem(index: Int)
    fun clearQueue()

    // Phase 7: Extras
    fun setPlaybackSpeed(speed: Float)
    fun setSleepTimer(minutes: Int)
    fun setSleepTimerEndOfTrack()
    fun cancelSleepTimer()
    fun setEqualizerEnabled(enabled: Boolean)
    fun setEqualizerPreset(presetIndex: Short)
    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short)
    fun setBassBoostStrength(strength: Short)
    fun setVirtualizerStrength(strength: Short)
    fun resetEqualizer()
}
