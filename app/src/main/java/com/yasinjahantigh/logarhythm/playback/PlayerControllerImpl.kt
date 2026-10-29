package com.yasinjahantigh.logarhythm.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.yasinjahantigh.logarhythm.domain.model.Track
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
    @param:ApplicationContext private val context: Context,
    private val queuePersistenceManager: QueuePersistenceManager,
    private val playScrobbler: PlayScrobbler,
    private val sleepTimerManager: SleepTimerManager,
    private val equalizerManager: EqualizerManager
) : PlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaController: MediaController? = null
    private var reconnectAttempts = 0
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private val trackMap = mutableMapOf<String, Track>()
    private var originalQueue: List<Track> = emptyList()

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private val _currentTrack = MutableStateFlow<Track?>(null)
    override val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    override val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    override val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    private val _queueIndex = MutableStateFlow(-1)
    override val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    override val sleepTimerRemainingMs: StateFlow<Long?> = sleepTimerManager.remainingMs
    override val sleepTimerRemainingTracks: StateFlow<Int?> = sleepTimerManager.remainingTracks
    override val isSleepTimerEndOfTrack: StateFlow<Boolean> = sleepTimerManager.isEndOfTrack
    override val equalizerState: StateFlow<EqualizerState> = equalizerManager.equalizerState

    init {
        connect()
        startPositionTracker()
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    mediaController = null
                    scheduleReconnect()
                }
            })
            .buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                reconnectAttempts = 0
                setupController(controller)
            } catch (_: Exception) {
                scheduleReconnect()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** The service can be killed by the system, which leaves the controller holding a dead session. */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        scope.launch {
            delay(2_000L)
            connect()
        }
    }

    private fun setupController(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
                if (!playing) saveQueueState()
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                _queueIndex.value = controller.currentMediaItemIndex
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                _queueIndex.value = controller.currentMediaItemIndex
                onTrackChanged(item)
                saveQueueState()
            }
            override fun onPlaybackStateChanged(state: Int) {
                _isPlaying.value = controller.isPlaying
                if (controller.duration > 0L) _duration.value = controller.duration
            }
            override fun onRepeatModeChanged(mode: Int) { _repeatMode.value = mode }
            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                _playbackSpeed.value = parameters.speed
            }
        })
        controller.shuffleModeEnabled = false
        _queueIndex.value = controller.currentMediaItemIndex
        _repeatMode.value = controller.repeatMode
        _isPlaying.value = controller.isPlaying
        _playbackSpeed.value = controller.playbackParameters.speed
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
        pendingActions.forEach { it(controller) }
        pendingActions.clear()

        if (controller.mediaItemCount == 0) {
            restoreSavedQueue(controller)
        }
    }

    private fun restoreSavedQueue(controller: MediaController) {
        scope.launch {
            val restored = queuePersistenceManager.restoreQueue() ?: return@launch
            if (controller.mediaItemCount != 0 || _queue.value.isNotEmpty()) return@launch
            _queue.value = restored.tracks
            _shuffle.value = restored.shuffle
            originalQueue = restored.originalTracks
            restored.tracks.forEach { trackMap[it.id] = it }
            val track = restored.tracks.getOrNull(restored.currentIndex)
            _currentTrack.value = track
            _position.value = restored.positionMs
            if (track != null && track.durationMs > 0L) _duration.value = track.durationMs
            val mediaItems = restored.tracks.map { MediaItemMapper.toMediaItem(it) }
            controller.setMediaItems(mediaItems, restored.currentIndex, restored.positionMs)
            controller.prepare()
        }
    }

    private fun startPositionTracker() {
        scope.launch {
            while (isActive) {
                delay(if (_isPlaying.value) 500L else 1_000L)
                val controller = mediaController ?: continue
                val playing = controller.isPlaying
                _isPlaying.value = playing
                _position.value = controller.currentPosition.coerceAtLeast(0L)
                if (controller.duration > 0L) _duration.value = controller.duration
                playScrobbler.onTick(playing, _currentTrack.value, _duration.value)
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
        playScrobbler.onTrackChanged(track?.id)
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
    }

    private fun execute(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) action(controller) else pendingActions.add(action)
    }

    private fun saveQueueState() {
        val q = _queue.value
        val idx = mediaController?.currentMediaItemIndex ?: 0
        val pos = _position.value
        val shuffled = _shuffle.value
        val original = originalQueue
        scope.launch { queuePersistenceManager.saveQueue(q, idx, pos, shuffled, original) }
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
    }

    override fun play(list: List<Track>, startIndex: Int) {
        if (list.isEmpty()) return
        originalQueue = list
        trackMap.clear()
        list.forEach { trackMap[it.id] = it }
        val validIndex = startIndex.coerceIn(0, list.lastIndex)

        if (_shuffle.value) {
            val startTrack = list[validIndex]
            val otherTracks = list.filterIndexed { i, _ -> i != validIndex }.shuffled()
            val shuffledList = listOf(startTrack) + otherTracks
            _queue.value = shuffledList
            _currentTrack.value = startTrack
            _duration.value = startTrack.durationMs
            _position.value = 0L
            playScrobbler.onTrackChanged(startTrack.id, force = true)
            val mediaItems = shuffledList.map { MediaItemMapper.toMediaItem(it) }
            execute { controller ->
                controller.shuffleModeEnabled = false
                controller.setMediaItems(mediaItems, 0, 0L)
                controller.prepare()
                controller.play()
                saveQueueState()
            }
        } else {
            _queue.value = list
            _currentTrack.value = list[validIndex]
            _duration.value = list[validIndex].durationMs
            _position.value = 0L
            playScrobbler.onTrackChanged(list[validIndex].id, force = true)
            val mediaItems = list.map { MediaItemMapper.toMediaItem(it) }
            execute { controller ->
                controller.shuffleModeEnabled = false
                controller.setMediaItems(mediaItems, validIndex, 0L)
                controller.prepare()
                controller.play()
                saveQueueState()
            }
        }
    }

    override fun playRadio(seedTrack: Track, similarTracks: List<Track>) {
        val existingPosition = if (_currentTrack.value?.id == seedTrack.id) _position.value else 0L
        val radioTracks = (listOf(seedTrack) + similarTracks)
            .distinctBy { it.id }
        if (radioTracks.size < 2) return

        originalQueue = radioTracks
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

    override fun toggleShuffle() = execute { controller ->
        val newMode = !_shuffle.value
        _shuffle.value = newMode
        applyShuffleMode(newMode, controller)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        if (_shuffle.value == enabled) return
        _shuffle.value = enabled
        execute { applyShuffleMode(enabled, it) }
    }

    override fun reshuffleQueue() = execute { controller ->
        _shuffle.value = true
        applyShuffleMode(true, controller)
    }

    private fun applyShuffleMode(enabled: Boolean, controller: MediaController) {
        val visible = _queue.value
        if (visible.isEmpty()) {
            _shuffle.value = enabled
            controller.shuffleModeEnabled = false
            return
        }
        if (originalQueue.isEmpty()) originalQueue = visible
        val current = _currentTrack.value ?: return
        val index = controller.currentMediaItemIndex
            .takeIf { it in visible.indices }
            ?: visible.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        // Songs that already played stay exactly where they are; only the rest is shuffled or
        // restored, so the "Played" section never fills up with unheard tracks.
        val newQueue = shuffleUpcoming(visible, originalQueue, current, index, enabled)
        val upcoming = newQueue.drop(index + 1)
        val pos = _position.value

        _shuffle.value = enabled
        _queue.value = newQueue
        newQueue.forEach { trackMap[it.id] = it }
        _queueIndex.value = index
        controller.shuffleModeEnabled = false
        val canSplice = controller.mediaItemCount == visible.size &&
            controller.currentMediaItemIndex == index &&
            controller.currentMediaItem?.mediaId == current.id
        if (canSplice) {
            controller.removeMediaItems(index + 1, controller.mediaItemCount)
            controller.addMediaItems(index + 1, upcoming.map(MediaItemMapper::toMediaItem))
        } else {
            controller.setMediaItems(newQueue.map(MediaItemMapper::toMediaItem), index, pos)
        }
        saveQueueState()
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
        val nextIndex = (currentIndex + 1).coerceIn(0, currentQueue.size)
        originalQueue = insertOriginal(originalQueue, currentQueue, nextIndex, track)
        currentQueue.add(nextIndex, track)
        _queue.value = currentQueue
        trackMap[track.id] = track
        execute { it.addMediaItem(nextIndex, MediaItemMapper.toMediaItem(track)); saveQueueState() }
    }

    override fun addToQueue(track: Track) {
        if (_queue.value.isEmpty()) { play(listOf(track), 0); return }
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(track)
        originalQueue = originalQueue + track
        _queue.value = currentQueue
        trackMap[track.id] = track
        execute { it.addMediaItem(MediaItemMapper.toMediaItem(track)); saveQueueState() }
    }

    override fun removeFromQueue(index: Int): Track? {
        val currentQueue = _queue.value.toMutableList()
        if (index !in currentQueue.indices) return null
        val originalIndex = originalOccurrence(originalQueue, currentQueue, index)
        originalQueue = originalQueue.filterIndexed { i, _ -> i != originalIndex }
        val removed = currentQueue.removeAt(index)
        _queue.value = currentQueue
        execute { if (index < it.mediaItemCount) it.removeMediaItem(index); saveQueueState() }
        return removed
    }

    override fun insertIntoQueue(index: Int, track: Track) {
        val currentQueue = _queue.value.toMutableList()
        val safeIndex = index.coerceIn(0, currentQueue.size)
        originalQueue = insertOriginal(originalQueue, currentQueue, safeIndex, track)
        currentQueue.add(safeIndex, track)
        _queue.value = currentQueue
        trackMap[track.id] = track
        execute { it.addMediaItem(safeIndex, MediaItemMapper.toMediaItem(track)); saveQueueState() }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val originalIndex = originalOccurrence(originalQueue, currentQueue, fromIndex)
        originalQueue = originalQueue.filterIndexed { i, _ -> i != originalIndex }
        val item = currentQueue.removeAt(fromIndex)
        originalQueue = insertOriginal(originalQueue, currentQueue, toIndex, item)
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

    override fun currentQueueIndex(): Int = mediaController?.currentMediaItemIndex ?: -1

    override fun clearQueue() {
        originalQueue = emptyList()
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
    override fun setSleepTimerTracks(tracks: Int) = sleepTimerManager.startTimerTracks(tracks)
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
            // Rebuild the MediaItem so the notification, lock screen and Bluetooth metadata stop
            // showing the previous title/artist/cover. The position is restored manually because
            // replacing the current item otherwise restarts it.
            execute { controller ->
                val index = controller.currentMediaItemIndex
                if (index < 0 || index >= controller.mediaItemCount) return@execute
                if (controller.currentMediaItem?.mediaId != updatedTrack.id) return@execute
                val position = controller.currentPosition
                val playing = controller.isPlaying
                runCatching {
                    controller.replaceMediaItem(index, MediaItemMapper.toMediaItem(updatedTrack))
                    controller.seekTo(index, position)
                    if (playing) controller.play()
                }
            }
        }
        _queue.value = _queue.value.map { if (it.id == updatedTrack.id) updatedTrack else it }
    }
}
