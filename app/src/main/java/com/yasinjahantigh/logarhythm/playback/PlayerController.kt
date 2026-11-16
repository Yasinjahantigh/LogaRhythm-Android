package com.yasinjahantigh.logarhythm.playback

import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/** Playback operations exposed to the UI. */
interface PlayerController {
    val currentTrack: StateFlow<Track?>
    val isPlaying: StateFlow<Boolean>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val shuffle: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>
    val queue: StateFlow<List<Track>>
    val queueIndex: StateFlow<Int>

    val playbackSpeed: StateFlow<Float>
    val playbackPitch: StateFlow<Float>
    val slowedReverb: StateFlow<Boolean>
    val abLoopStartMs: StateFlow<Long?>
    val abLoopEndMs: StateFlow<Long?>
    val sleepTimerRemainingMs: StateFlow<Long?>
    val sleepTimerRemainingTracks: StateFlow<Int?>
    val isSleepTimerEndOfTrack: StateFlow<Boolean>
    val equalizerState: StateFlow<EqualizerState>

    fun play(list: List<Track>, startIndex: Int = 0)
    /** Replaces the queue with a track radio while preserving the seed track position when possible. */
    fun playRadio(seedTrack: Track, similarTracks: List<Track>)
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun setShuffleEnabled(enabled: Boolean)
    fun reshuffleQueue()
    fun cycleRepeat()
    fun setRepeatMode(mode: Int)

    fun playNext(track: Track)
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int): Track?
    fun insertIntoQueue(index: Int, track: Track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun playQueueItem(index: Int)
    fun currentQueueIndex(): Int
    fun clearQueue()

    fun setPlaybackSpeed(speed: Float)
    fun setPlaybackPitch(pitch: Float)
    fun setSlowedReverb(enabled: Boolean)
    fun toggleAbLoopMarker()
    fun clearAbLoop()
    fun setSleepTimer(minutes: Int)
    fun setSleepTimerTracks(tracks: Int)
    fun setSleepTimerEndOfTrack()
    fun cancelSleepTimer()
    fun setEqualizerEnabled(enabled: Boolean)
    fun setEqualizerPreset(presetIndex: Short)
    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short)
    fun setBassBoostStrength(strength: Short)
    fun setVirtualizerStrength(strength: Short)
    fun setStereoBalance(balance: Float)
    fun setMonoAudio(enabled: Boolean)
    fun setDeviceAutoEqEnabled(enabled: Boolean)
    fun resetEqualizer()
    fun updateTrackInPlayback(updatedTrack: Track)
}
