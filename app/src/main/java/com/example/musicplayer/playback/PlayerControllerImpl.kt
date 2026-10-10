package com.example.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.widget.MusicWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queuePersistenceManager: QueuePersistenceManager,
    private val playScrobbler: PlayScrobbler,
    private val sleepTimerManager: SleepTimerManager,
    private val equalizerManager: EqualizerManager
) : PlayerController, PlaybackController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaController: MediaController? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private val trackMap = mutableMapOf<String, Track>()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    override val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()
    override val currentPositionMs: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()
    override val durationMs: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    override val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    override val sleepTimerRemainingMs: StateFlow<Long?> = sleepTimerManager.remainingMs
    override val isSleepTimerEndOfTrack: StateFlow<Boolean> = sleepTimerManager.isEndOfTrack
    override val equalizerState: StateFlow<EqualizerState> = equalizerManager.equalizerState

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                setupController(controller)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
        startPositionTracker()
    }

    private fun setupController(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                MusicWidgetUpdater.update(context, _currentTrack.value?.title, _currentTrack.value?.artist, _currentTrack.value?.artworkUri, playing)
                if (!playing) saveQueueState()
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                onTrackChanged(item)
                saveQueueState()
            }
            override fun onPlaybackStateChanged(state: Int) {
                _isPlaying.value = controller.isPlaying
                if (controller.duration > 0L) _duration.value = controller.duration
            }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { _shuffle.value = enabled }
            override fun onRepeatModeChanged(mode: Int) { _repeatMode.value = mode }
            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                _playbackSpeed.value = parameters.speed
            }
        })
        _shuffle.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        _isPlaying.value = controller.isPlaying
        _playbackSpeed.value = controller.playbackParameters.speed
        pendingActions.forEach { it(controller) }
        pendingActions.clear()

        if (controller.mediaItemCount == 0) {
            restoreSavedQueue(controller)
        }
    }

    private fun restoreSavedQueue(controller: MediaController) {
        scope.launch {
            val restored = queuePersistenceManager.restoreQueue() ?: return@launch
            _queue.value = restored.tracks
            restored.tracks.forEach { trackMap[it.id] = it }
            val track = restored.tracks.getOrNull(restored.currentIndex)
            _currentTrack.value = track
            _position.value = restored.positionMs
            if (track != null && track.durationMs > 0L) _duration.value = track.durationMs
            MusicWidgetUpdater.update(context, track?.title, track?.artist, track?.artworkUri, false)
            val mediaItems = restored.tracks.map { MediaItemMapper.toMediaItem(it) }
            controller.setMediaItems(mediaItems, restored.currentIndex, restored.positionMs)
            controller.prepare()
        }
    }

    private fun startPositionTracker() {
        scope.launch {
            while (isActive) {
                delay(500L)
                val controller = mediaController ?: continue
                val playing = controller.isPlaying
                _isPlaying.value = playing
                _position.value = controller.currentPosition.coerceAtLeast(0L)
                if (controller.duration > 0L) _duration.value = controller.duration
                playScrobbler.onTick(playing, _currentTrack.value, _duration.value, scope)
            }
        }
    }

    private fun onTrackChanged(mediaItem: MediaItem?) {
        val track = mediaItem?.mediaId?.let { trackMap[it] }
        val isDifferentTrack = _currentTrack.value?.id != track?.id
        _currentTrack.value = track
        if (track != null && track.durationMs > 0L) _duration.value = track.durationMs
        // setMediaItems can emit a transition for the same seed track during radio/queue restore.
        // Preserve its seek position; only a genuinely different track starts at zero.
        if (isDifferentTrack) _position.value = 0L
        MusicWidgetUpdater.update(context, track?.title, track?.artist, track?.artworkUri, _isPlaying.value)
        playScrobbler.onTrackChanged(track?.id)
    }

    private fun execute(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) action(controller) else pendingActions.add(action)
    }

    private fun saveQueueState() {
        val q = _queue.value
        val idx = mediaController?.currentMediaItemIndex ?: 0
        val pos = _position.value
        scope.launch { queuePersistenceManager.saveQueue(q, idx, pos) }
    }

    override fun play(list: List<Track>, startIndex: Int) {
        if (list.isEmpty()) return
        _queue.value = list
        trackMap.clear()
        list.forEach { trackMap[it.id] = it }
        val validIndex = startIndex.coerceIn(0, list.lastIndex)
        _currentTrack.value = list[validIndex]
        _duration.value = list[validIndex].durationMs
        _position.value = 0L
        playScrobbler.onTrackChanged(list[validIndex].id, force = true)
        val mediaItems = list.map { MediaItemMapper.toMediaItem(it) }
        execute { controller ->
            controller.setMediaItems(mediaItems, validIndex, 0L)
            controller.prepare()
            controller.play()
            saveQueueState()
        }
    }

    override fun playRadio(seedTrack: Track, similarTracks: List<Track>) {
        val existingPosition = if (_currentTrack.value?.id == seedTrack.id) _position.value else 0L
        val radioTracks = (listOf(seedTrack) + similarTracks)
            .distinctBy { it.id }
        if (radioTracks.size < 2) return

        _queue.value = radioTracks
        trackMap.clear()
        radioTracks.forEach { trackMap[it.id] = it }
        _currentTrack.value = seedTrack
        val safeResumePosition = if (seedTrack.durationMs > 0L) {
            existingPosition.coerceIn(0L, seedTrack.durationMs)
        } else {
            existingPosition.coerceAtLeast(0L)
        }
        _position.value = safeResumePosition
        _duration.value = seedTrack.durationMs.coerceAtLeast(0L)
        playScrobbler.onTrackChanged(seedTrack.id)

        val mediaItems = radioTracks.map { MediaItemMapper.toMediaItem(it) }
        _shuffle.value = false
        _repeatMode.value = Player.REPEAT_MODE_OFF
        execute { controller ->
            controller.shuffleModeEnabled = false
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.setMediaItems(mediaItems, 0, safeResumePosition)
            controller.prepare()
            controller.play()
            saveQueueState()
        }
    }

    override fun pause() = execute { it.pause(); saveQueueState() }
    override fun resume() = execute { it.play() }
    override fun next() = execute { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    override fun previous() = execute {
        if (it.currentPosition > 3000L) it.seekTo(0L)
        else if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
        else it.seekTo(0L)
    }
    override fun seekTo(positionMs: Long) = execute {
        it.seekTo(positionMs)
        _position.value = positionMs
        saveQueueState()
    }
    override fun toggleShuffle() = execute {
        val newMode = !it.shuffleModeEnabled
        it.shuffleModeEnabled = newMode
        _shuffle.value = newMode
    }

    override fun setShuffleEnabled(enabled: Boolean) = execute {
        it.shuffleModeEnabled = enabled
        _shuffle.value = enabled
    }

    override fun cycleRepeat() = execute {
        val next = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        it.repeatMode = next
        _repeatMode.value = next
    }

    override fun setRepeatMode(mode: Int) = execute {
        val safeMode = when (mode) {
            Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL -> mode
            else -> Player.REPEAT_MODE_OFF
        }
        it.repeatMode = safeMode
        _repeatMode.value = safeMode
    }

    override fun playNext(track: Track) {
        if (_queue.value.isEmpty()) { play(listOf(track), 0); return }
        val currentQueue = _queue.value.toMutableList()
        val currentIndex = mediaController?.currentMediaItemIndex ?: 0
        val nextIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(nextIndex, track)
        _queue.value = currentQueue
        trackMap[track.id] = track
        execute { it.addMediaItem(nextIndex, MediaItemMapper.toMediaItem(track)); saveQueueState() }
    }

    override fun addToQueue(track: Track) {
        if (_queue.value.isEmpty()) { play(listOf(track), 0); return }
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(track)
        _queue.value = currentQueue
        trackMap[track.id] = track
        execute { it.addMediaItem(MediaItemMapper.toMediaItem(track)); saveQueueState() }
    }

    override fun removeFromQueue(index: Int): Track? {
        val currentQueue = _queue.value.toMutableList()
        if (index !in currentQueue.indices) return null
        val removed = currentQueue.removeAt(index)
        _queue.value = currentQueue
        execute { if (index < it.mediaItemCount) it.removeMediaItem(index); saveQueueState() }
        return removed
    }

    override fun insertIntoQueue(index: Int, track: Track) {
        val currentQueue = _queue.value.toMutableList()
        val safeIndex = index.coerceIn(0, currentQueue.size)
        currentQueue.add(safeIndex, track)
        _queue.value = currentQueue
        trackMap[track.id] = track
        execute { it.addMediaItem(safeIndex, MediaItemMapper.toMediaItem(track)); saveQueueState() }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val item = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, item)
        _queue.value = currentQueue
        execute { it.moveMediaItem(fromIndex, toIndex); saveQueueState() }
    }

    override fun playQueueItem(index: Int) = execute {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    override fun clearQueue() {
        _queue.value = emptyList()
        _currentTrack.value = null
        _position.value = 0L
        execute { it.clearMediaItems(); saveQueueState() }
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        execute { it.setPlaybackSpeed(clamped) }
    }

    override fun setSleepTimer(minutes: Int) = sleepTimerManager.startTimer(minutes)
    override fun setSleepTimerEndOfTrack() = sleepTimerManager.startEndOfTrack()
    override fun cancelSleepTimer() = sleepTimerManager.cancelTimer()

    override fun setEqualizerEnabled(enabled: Boolean) = equalizerManager.setEqualizerEnabled(enabled)
    override fun setEqualizerPreset(presetIndex: Short) = equalizerManager.setPreset(presetIndex)
    override fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) =
        equalizerManager.setBandLevel(bandIndex, levelMb)
    override fun setBassBoostStrength(strength: Short) =
        equalizerManager.setBassBoostStrength(strength)
    override fun setVirtualizerStrength(strength: Short) =
        equalizerManager.setVirtualizerStrength(strength)
    override fun resetEqualizer() = equalizerManager.resetEqualizer()

    override fun updateTrackInPlayback(updatedTrack: Track) {
        trackMap[updatedTrack.id] = updatedTrack
        if (_currentTrack.value?.id == updatedTrack.id) {
            _currentTrack.value = updatedTrack
            MusicWidgetUpdater.update(context, updatedTrack.title, updatedTrack.artist, updatedTrack.artworkUri, _isPlaying.value)
        }
        _queue.value = _queue.value.map { if (it.id == updatedTrack.id) updatedTrack else it }
    }

    override fun playTrack(track: Track) = play(listOf(track), 0)
    override fun playQueue(tracks: List<Track>, startIndex: Int) = play(tracks, startIndex)
    override fun skipToNext() = next()
    override fun skipToPrevious() = previous()
    override fun stop() = pause()
}
