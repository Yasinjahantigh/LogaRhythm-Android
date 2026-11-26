package com.yasinjahantigh.logarhythm.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.yasinjahantigh.logarhythm.data.analytics.AppLog
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val queuePersistenceManager: QueuePersistenceManager,
    private val playScrobbler: PlayScrobbler,
    private val sleepTimerManager: SleepTimerManager,
    private val equalizerManager: EqualizerManager,
    private val preferencesManager: PreferencesManager,
    private val trackRepositoryLazy: Lazy<TrackRepository>,
    private val playlistRepositoryLazy: Lazy<PlaylistRepository>,
    private val statsRepositoryLazy: Lazy<StatsRepository>
) : PlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaController: MediaController? = null
    private var isConnecting = false
    private var reconnectAttempts = 0
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private val trackMap = mutableMapOf<String, Track>()
    private var originalQueue: List<Track> = emptyList()
    private var endlessAppendJob: Job? = null
    private var saveQueueJob: Job? = null
    private var playPrepJob: Job? = null
    private var hydrateJob: Job? = null

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val ENDLESS_BATCH_SIZE = 15
        const val ENDLESS_TRIGGER_REMAINING = 2
        const val QUEUE_SAVE_DEBOUNCE_MS = 400L
        const val ASYNC_MEDIA_MAP_THRESHOLD = 100
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

    private val _playbackPitch = MutableStateFlow(1.0f)
    override val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _slowedReverb = MutableStateFlow(false)
    override val slowedReverb: StateFlow<Boolean> = _slowedReverb.asStateFlow()

    private val _abLoopStartMs = MutableStateFlow<Long?>(null)
    override val abLoopStartMs: StateFlow<Long?> = _abLoopStartMs.asStateFlow()

    private val _abLoopEndMs = MutableStateFlow<Long?>(null)
    override val abLoopEndMs: StateFlow<Long?> = _abLoopEndMs.asStateFlow()

    override val sleepTimerRemainingMs: StateFlow<Long?> = sleepTimerManager.remainingMs
    override val sleepTimerRemainingTracks: StateFlow<Int?> = sleepTimerManager.remainingTracks
    override val isSleepTimerEndOfTrack: StateFlow<Boolean> = sleepTimerManager.isEndOfTrack
    override val equalizerState: StateFlow<EqualizerState> = equalizerManager.equalizerState

    init {
        connect()
        startPositionTracker()
    }

    private fun connect() {
        if (isConnecting) return
        isConnecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    AppLog.w("PlayerCtrl", "MediaController disconnected")
                    mediaController = null
                    scheduleReconnect()
                }
            })
            .buildAsync()
        future.addListener({
            isConnecting = false
            try {
                val controller = future.get()
                mediaController = controller
                reconnectAttempts = 0
                AppLog.i("PlayerCtrl", "MediaController connected (items=${controller.mediaItemCount})")
                setupController(controller)
            } catch (e: Exception) {
                AppLog.w("PlayerCtrl", "MediaController connect failed (attempt=$reconnectAttempts)", e)
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
                if (!playing) saveQueueState(immediate = true)
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                _queueIndex.value = controller.currentMediaItemIndex
                if (controller.mediaItemCount > 0 && (_queue.value.isEmpty() || _currentTrack.value == null)) {
                    hydrateFromController(controller)
                }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val index = controller.currentMediaItemIndex
                val previousIndex = _queueIndex.value
                _queueIndex.value = index
                val isRepeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && index == previousIndex) ||
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && index == previousIndex && _position.value == 0L)
                onTrackChanged(item, isRepeat = isRepeat)
                saveQueueState()
                maybePrefetchEndlessQueue(controller, playNextWhenReady = false)
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    val track = _currentTrack.value
                    if (track != null) {
                        playScrobbler.onTrackLooped(track.id)
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                _isPlaying.value = controller.isPlaying
                if (controller.duration > 0L) _duration.value = controller.duration
                if (state == Player.STATE_ENDED &&
                    controller.repeatMode == Player.REPEAT_MODE_OFF &&
                    !hasActiveSleepTimer()
                ) {
                    maybePrefetchEndlessQueue(controller, playNextWhenReady = true)
                }
            }
            override fun onRepeatModeChanged(mode: Int) {
                _repeatMode.value = mode
                com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
            }
            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                if (playbackParamsJob?.isActive != true) {
                    val s = parameters.speed
                    val p = parameters.pitch
                    if (s.isFinite() && s in 0.5f..2.0f) _playbackSpeed.value = s
                    if (p.isFinite() && p in 0.5f..2.0f) _playbackPitch.value = p
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLog.e(
                    "PlayerCtrl",
                    "ExoPlayer error code=${error.errorCodeName} (${error.errorCode}) track=${_currentTrack.value?.id}",
                    error
                )
                // Recover gracefully if an AudioSink/Sonic reconfiguration error occurs after speed/pitch tweaks.
                if (!recoveringFromSpeedError && (_playbackSpeed.value != 1.0f || _playbackPitch.value != 1.0f)) {
                    recoveringFromSpeedError = true
                    val resumePos = controller.currentPosition.coerceAtLeast(0L)
                    val shouldPlay = controller.playWhenReady
                    controller.setPlaybackParameters(PlaybackParameters(_playbackSpeed.value, _playbackPitch.value))
                    controller.prepare()
                    if (resumePos > 0L) controller.seekTo(resumePos)
                    if (shouldPlay) controller.play()
                    scope.launch {
                        delay(1_500L)
                        recoveringFromSpeedError = false
                    }
                }
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
        } else {
            hydrateFromController(controller)
        }
        scope.launch {
            val savedPitch = preferencesManager.playbackPitch.first().coerceIn(0.5f, 2.0f)
            val savedSlowed = preferencesManager.slowedReverbEnabled.first()
            _playbackPitch.value = savedPitch
            _slowedReverb.value = savedSlowed
            if (savedSlowed) {
                _playbackSpeed.value = 0.85f
                controller.playbackParameters = PlaybackParameters(0.85f, savedPitch)
                equalizerManager.setReverbEnabled(true)
            } else if (savedPitch != 1.0f) {
                controller.playbackParameters = PlaybackParameters(_playbackSpeed.value, savedPitch)
            }
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
            val mediaItems = withContext(Dispatchers.Default) {
                restored.tracks.map { MediaItemMapper.toMediaItem(it) }
            }
            if (controller.mediaItemCount != 0) return@launch
            controller.setMediaItems(mediaItems, restored.currentIndex, restored.positionMs)
            controller.prepare()
        }
    }

    /**
     * When PlaybackService was started in the background (e.g. from the widget after process death)
     * and already populated ExoPlayer, hydrate _queue/_currentTrack/trackMap from the live
     * MediaController immediately and asynchronously enrich with full Room/MediaStore track records.
     */
    private fun hydrateFromController(controller: MediaController) {
        val count = controller.mediaItemCount
        if (count <= 0) return

        val hydrated = ArrayList<Track>(count)
        for (i in 0 until count) {
            val item = controller.getMediaItemAt(i)
            val resolved = trackMap[item.mediaId] ?: MediaItemMapper.fallbackTrack(item)
            if (resolved != null) {
                trackMap[resolved.id] = resolved
                hydrated.add(resolved)
            }
        }
        if (hydrated.isEmpty()) return

        _queue.value = hydrated
        if (originalQueue.isEmpty()) {
            originalQueue = hydrated
        }
        val curIdx = controller.currentMediaItemIndex.takeIf { it in hydrated.indices } ?: 0
        _queueIndex.value = curIdx
        val current = hydrated.getOrNull(curIdx)
            ?: controller.currentMediaItem?.let { MediaItemMapper.fallbackTrack(it) }
        _currentTrack.value = current
        _position.value = controller.currentPosition.coerceAtLeast(0L)
        val ctrlDuration = controller.duration.takeIf { it > 0L } ?: current?.durationMs ?: 0L
        if (ctrlDuration > 0L) _duration.value = ctrlDuration
        _isPlaying.value = controller.isPlaying

        hydrateJob?.cancel()
        hydrateJob = scope.launch {
            val allById = withContext(Dispatchers.IO) {
                runCatching { trackRepositoryLazy.get().getAllTracks().first() }
                    .getOrNull()?.associateBy { it.id }.orEmpty()
            }
            if (allById.isEmpty()) return@launch
            val enrichedQueue = _queue.value.map { t -> allById[t.id] ?: t }
            enrichedQueue.forEach { trackMap[it.id] = it }
            _queue.value = enrichedQueue
            if (originalQueue.isNotEmpty()) {
                originalQueue = originalQueue.map { t -> allById[t.id] ?: t }
            }
            val activeId = _currentTrack.value?.id ?: controller.currentMediaItem?.mediaId
            if (activeId != null) {
                allById[activeId]?.let { fullTrack ->
                    _currentTrack.value = fullTrack
                    if (_duration.value <= 0L && fullTrack.durationMs > 0L) {
                        _duration.value = fullTrack.durationMs
                    }
                }
            }
        }
    }

    override fun reconnectIfNeeded() {
        val controller = mediaController
        if (controller == null) {
            reconnectAttempts = 0
            if (!isConnecting) connect()
        } else {
            val activeMediaId = controller.currentMediaItem?.mediaId
            if (controller.mediaItemCount > 0 && (
                    _queue.value.isEmpty() ||
                        _currentTrack.value == null ||
                        _queue.value.size != controller.mediaItemCount ||
                        (activeMediaId != null && _currentTrack.value?.id != activeMediaId)
                    )
            ) {
                hydrateFromController(controller)
            }
            _isPlaying.value = controller.isPlaying
            _position.value = controller.currentPosition.coerceAtLeast(0L)
            if (controller.duration > 0L) _duration.value = controller.duration
        }
    }

    override fun setPreferredAudioDevice(deviceId: Int?) {
        execute { controller ->
            val args = Bundle().apply {
                putInt(PlaybackService.EXTRA_AUDIO_DEVICE_ID, deviceId ?: -1)
            }
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_SET_PREFERRED_AUDIO_DEVICE, Bundle.EMPTY),
                args
            )
        }
    }

    private fun startPositionTracker() {
        scope.launch {
            while (isActive) {
                if (!_isPlaying.value) {
                    // Suspend completely while paused/idle instead of polling MediaController IPC every second.
                    _isPlaying.first { it }
                }
                delay(500L)
                val controller = mediaController ?: continue
                val playing = controller.isPlaying
                _isPlaying.value = playing
                val pos = controller.currentPosition.coerceAtLeast(0L)
                _position.value = pos
                if (controller.duration > 0L) _duration.value = controller.duration
                val loopStart = _abLoopStartMs.value
                val loopEnd = _abLoopEndMs.value
                if (playing && loopStart != null && loopEnd != null && loopEnd > loopStart && pos >= loopEnd) {
                    controller.seekTo(loopStart)
                    _position.value = loopStart
                }
                playScrobbler.onTick(playing, _currentTrack.value, _duration.value, pos)
            }
        }
    }

    private fun onTrackChanged(mediaItem: MediaItem?, isRepeat: Boolean = false) {
        val trackId = mediaItem?.mediaId
        val track = trackId?.let { trackMap[it] } ?: mediaItem?.let { MediaItemMapper.fallbackTrack(it) }
        if (track != null && trackId != null && !trackMap.containsKey(trackId)) {
            trackMap[trackId] = track
            scope.launch {
                val full = withContext(Dispatchers.IO) {
                    runCatching { trackRepositoryLazy.get().getTrackById(trackId).first() }.getOrNull()
                }
                if (full != null) {
                    trackMap[full.id] = full
                    if (_currentTrack.value?.id == full.id) {
                        _currentTrack.value = full
                        if (_duration.value <= 0L && full.durationMs > 0L) _duration.value = full.durationMs
                    }
                }
            }
        }
        val isDifferentTrack = _currentTrack.value?.id != track?.id
        _currentTrack.value = track
        if (track != null && track.durationMs > 0L) _duration.value = track.durationMs
        // setMediaItems can emit a transition for the same seed track during radio/queue restore.
        // Preserve its seek position; only a genuinely different track or explicit repeat starts at zero.
        if (isDifferentTrack || isRepeat) {
            _position.value = 0L
            clearAbLoop()
        }
        playScrobbler.onTrackChanged(track?.id, force = isRepeat || isDifferentTrack)
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
    }

    private fun execute(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) action(controller) else pendingActions.add(action)
    }

    private fun saveQueueState(immediate: Boolean = false) {
        saveQueueJob?.cancel()
        saveQueueJob = scope.launch {
            if (!immediate) delay(QUEUE_SAVE_DEBOUNCE_MS)
            val q = _queue.value
            val idx = mediaController?.currentMediaItemIndex ?: 0
            val pos = _position.value
            val shuffled = _shuffle.value
            val original = originalQueue
            queuePersistenceManager.saveQueue(q, idx, pos, shuffled, original)
        }
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
    }

    override fun play(list: List<Track>, startIndex: Int) {
        if (list.isEmpty()) return
        playPrepJob?.cancel()
        val validIndex = startIndex.coerceIn(0, list.lastIndex)
        val startTrack = list[validIndex]
        val shouldShuffle = _shuffle.value

        originalQueue = list
        _currentTrack.value = startTrack
        _duration.value = startTrack.durationMs
        _position.value = 0L
        playScrobbler.onTrackChanged(startTrack.id, force = true)

        if (list.size <= ASYNC_MEDIA_MAP_THRESHOLD) {
            trackMap.clear()
            list.forEach { trackMap[it.id] = it }
            val targetQueue = if (shouldShuffle) {
                val otherTracks = list.filterIndexed { i, _ -> i != validIndex }.shuffled()
                ArrayList<Track>(list.size).apply {
                    add(startTrack)
                    addAll(otherTracks)
                }
            } else {
                list
            }
            val targetIndex = if (shouldShuffle) 0 else validIndex
            _queue.value = targetQueue
            val mediaItems = targetQueue.map { MediaItemMapper.toMediaItem(it) }
            execute { controller ->
                controller.shuffleModeEnabled = false
                controller.setMediaItems(mediaItems, targetIndex, 0L)
                controller.prepare()
                controller.play()
                saveQueueState()
            }
        } else {
            // Large library playback: shuffle & build MediaItems off the Main thread to avoid tap jank.
            trackMap[startTrack.id] = startTrack
            playPrepJob = scope.launch {
                val (targetQueue, mediaItems, newMap) = withContext(Dispatchers.Default) {
                    val map = HashMap<String, Track>((list.size * 4) / 3 + 1)
                    for (t in list) map[t.id] = t
                    val queue = if (shouldShuffle) {
                        val otherTracks = ArrayList<Track>(list.size - 1)
                        for (i in list.indices) {
                            if (i != validIndex) otherTracks.add(list[i])
                        }
                        otherTracks.shuffle()
                        ArrayList<Track>(list.size).apply {
                            add(startTrack)
                            addAll(otherTracks)
                        }
                    } else {
                        list
                    }
                    val items = queue.map { MediaItemMapper.toMediaItem(it) }
                    Triple(queue, items, map)
                }
                val targetIndex = if (shouldShuffle) 0 else validIndex
                trackMap.clear()
                trackMap.putAll(newMap)
                _queue.value = targetQueue
                execute { controller ->
                    controller.shuffleModeEnabled = false
                    controller.setMediaItems(mediaItems, targetIndex, 0L)
                    controller.prepare()
                    controller.play()
                    saveQueueState()
                }
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

    override fun pause() = execute { it.pause(); saveQueueState(immediate = true) }
    override fun resume() = execute { it.play() }
    override fun next() = execute { controller ->
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.repeatMode == Player.REPEAT_MODE_OFF && _queue.value.isNotEmpty()) {
            maybePrefetchEndlessQueue(controller, playNextWhenReady = true)
        }
    }

    private fun hasActiveSleepTimer(): Boolean =
        sleepTimerRemainingMs.value != null ||
            sleepTimerRemainingTracks.value != null ||
            isSleepTimerEndOfTrack.value

    /**
     * Endless listening: when the queue approaches its final tracks (or ends), build a similar
     * next-up mix from the listener's taste profile and append it seamlessly so music never stops.
     */
    private fun maybePrefetchEndlessQueue(controller: MediaController, playNextWhenReady: Boolean) {
        if (controller.repeatMode != Player.REPEAT_MODE_OFF) return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return
        val currentIndex = controller.currentMediaItemIndex.takeIf { it in currentQueue.indices }
            ?: currentQueue.lastIndex
        val remaining = currentQueue.lastIndex - currentIndex
        if (!playNextWhenReady && remaining > ENDLESS_TRIGGER_REMAINING) return
        if (endlessAppendJob?.isActive == true) return

        val seedTrack = currentQueue.getOrNull(currentIndex) ?: currentQueue.last()
        val recentTailIds = currentQueue.takeLast(24).map { it.id }.toSet()
        val allQueuedIds = currentQueue.map { it.id }.toSet()

        endlessAppendJob = scope.launch {
            val nextBatch = withContext(Dispatchers.Default) {
                try {
                    val library = trackRepositoryLazy.get().getAllTracks().first()
                    if (library.isEmpty()) return@withContext emptyList<Track>()
                    val events = runCatching { statsRepositoryLazy.get().getAllPlayEvents().first() }.getOrDefault(emptyList())
                    val favIds = runCatching {
                        trackRepositoryLazy.get().getFavoriteTrackIds().first()
                    }.getOrDefault(emptySet())
                    val profile = TasteProfile.build(events, favIds, library = library)

                    // 1) Prefer similar tracks not yet in the current queue.
                    val freshSimilar = TrackCuration.recommendNextTracks(
                        currentTrack = seedTrack,
                        allTracks = library,
                        limit = ENDLESS_BATCH_SIZE,
                        excludedTrackIds = allQueuedIds,
                        profile = profile
                    )
                    if (freshSimilar.isNotEmpty()) return@withContext freshSimilar

                    // 2) If most of the library was queued, recommend similar tracks avoiding the recent tail.
                    val cycledSimilar = TrackCuration.recommendNextTracks(
                        currentTrack = seedTrack,
                        allTracks = library,
                        limit = ENDLESS_BATCH_SIZE,
                        excludedTrackIds = recentTailIds,
                        profile = profile
                    )
                    if (cycledSimilar.isNotEmpty()) return@withContext cycledSimilar

                    // 3) Small library fallback: shuffle tracks other than the currently playing one.
                    val fallbackPool = library.filter { it.id != seedTrack.id }.ifEmpty { library }
                    fallbackPool.shuffled().take(ENDLESS_BATCH_SIZE)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            }

            if (nextBatch.isEmpty()) return@launch
            val liveController = mediaController ?: return@launch
            val insertAt = _queue.value.size
            val updatedQueue = _queue.value + nextBatch
            originalQueue = originalQueue + nextBatch
            nextBatch.forEach { trackMap[it.id] = it }
            _queue.value = updatedQueue
            liveController.addMediaItems(nextBatch.map(MediaItemMapper::toMediaItem))
            if (playNextWhenReady && insertAt < liveController.mediaItemCount) {
                liveController.seekToDefaultPosition(insertAt)
                liveController.play()
            }
            saveQueueState()
        }
    }
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
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
    }

    override fun setRepeatMode(mode: Int) = execute {
        val safeMode = when (mode) {
            Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL -> mode
            else -> Player.REPEAT_MODE_OFF
        }
        it.repeatMode = safeMode
        _repeatMode.value = safeMode
        com.yasinjahantigh.logarhythm.widget.MusicWidgetUpdater.requestUpdate(context)
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
        playPrepJob?.cancel()
        originalQueue = emptyList()
        _queue.value = emptyList()
        _currentTrack.value = null
        _position.value = 0L
        execute { it.clearMediaItems(); saveQueueState(immediate = true) }
    }

    private var playbackParamsJob: kotlinx.coroutines.Job? = null
    private var recoveringFromSpeedError: Boolean = false

    private fun normalizeRate(value: Float): Float {
        if (!value.isFinite()) return 1.0f
        return ((value.coerceIn(0.5f, 2.0f) * 20f).toInt() / 20f).coerceIn(0.5f, 2.0f)
    }

    private fun applyPlaybackParametersDebounced(
        speed: Float,
        pitch: Float,
        immediate: Boolean = false,
        persistPitch: Boolean = false,
        persistSlowed: Boolean? = null
    ) {
        val safeSpeed = normalizeRate(speed)
        val safePitch = normalizeRate(pitch)
        _playbackSpeed.value = safeSpeed
        _playbackPitch.value = safePitch

        playbackParamsJob?.cancel()
        playbackParamsJob = scope.launch {
            if (!immediate) {
                delay(110L)
            }
            if (persistPitch) {
                preferencesManager.setPlaybackPitch(safePitch)
            }
            if (persistSlowed != null) {
                preferencesManager.setSlowedReverbEnabled(persistSlowed)
            }
            execute { controller ->
                val cur = controller.playbackParameters
                val changed = kotlin.math.abs(cur.speed - safeSpeed) > 0.005f ||
                    kotlin.math.abs(cur.pitch - safePitch) > 0.005f
                if (changed) {
                    val wasPlaying = controller.playWhenReady
                    runCatching {
                        controller.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
                    }
                    // If ExoPlayer hit an error or became IDLE during rapid sonic reconfiguration, recover cleanly.
                    if (controller.playerError != null || (wasPlaying && controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0)) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        controller.prepare()
                        if (pos > 0L) controller.seekTo(pos)
                        if (wasPlaying) controller.play()
                    }
                }
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clamped = normalizeRate(speed)
        if (_slowedReverb.value && kotlin.math.abs(clamped - 0.85f) > 0.01f) {
            _slowedReverb.value = false
            equalizerManager.setReverbEnabled(false)
            applyPlaybackParametersDebounced(clamped, _playbackPitch.value, persistSlowed = false)
        } else {
            applyPlaybackParametersDebounced(clamped, _playbackPitch.value)
        }
    }

    override fun setPlaybackPitch(pitch: Float) {
        val clamped = normalizeRate(pitch)
        if (_slowedReverb.value && kotlin.math.abs(clamped - 0.88f) > 0.02f) {
            _slowedReverb.value = false
            equalizerManager.setReverbEnabled(false)
            applyPlaybackParametersDebounced(_playbackSpeed.value, clamped, persistPitch = true, persistSlowed = false)
        } else {
            applyPlaybackParametersDebounced(_playbackSpeed.value, clamped, persistPitch = true)
        }
    }

    override fun setSlowedReverb(enabled: Boolean) {
        _slowedReverb.value = enabled
        val newSpeed = if (enabled) 0.85f else 1.0f
        val newPitch = if (enabled) 0.88f else 1.0f
        equalizerManager.setReverbEnabled(enabled)
        applyPlaybackParametersDebounced(
            speed = newSpeed,
            pitch = newPitch,
            immediate = true,
            persistPitch = true,
            persistSlowed = enabled
        )
    }

    override fun resetPlaybackSpeedAndPitch() {
        _slowedReverb.value = false
        equalizerManager.setReverbEnabled(false)
        applyPlaybackParametersDebounced(
            speed = 1.0f,
            pitch = 1.0f,
            immediate = true,
            persistPitch = true,
            persistSlowed = false
        )
    }

    override fun toggleAbLoopMarker() {
        val pos = _position.value.coerceAtLeast(0L)
        val a = _abLoopStartMs.value
        val b = _abLoopEndMs.value
        when {
            a == null -> {
                _abLoopStartMs.value = pos
                _abLoopEndMs.value = null
            }
            b == null -> {
                if (pos > a + 500L) {
                    _abLoopEndMs.value = pos
                    seekTo(a)
                } else {
                    clearAbLoop()
                }
            }
            else -> clearAbLoop()
        }
    }

    override fun clearAbLoop() {
        _abLoopStartMs.value = null
        _abLoopEndMs.value = null
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
    override fun setStereoBalance(balance: Float) =
        equalizerManager.setStereoBalance(balance)
    override fun setMonoAudio(enabled: Boolean) =
        equalizerManager.setMonoAudio(enabled)
    override fun setDeviceAutoEqEnabled(enabled: Boolean) =
        equalizerManager.setDeviceAutoEqEnabled(enabled)
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

    override fun removeTrackFromPlayback(trackId: String) {
        trackMap.remove(trackId)
        originalQueue = originalQueue.filterNot { it.id == trackId }
        val currentQueue = _queue.value
        if (currentQueue.none { it.id == trackId }) return

        val wasCurrent = _currentTrack.value?.id == trackId
        val oldIndex = currentQueueIndex().takeIf { it in currentQueue.indices }
            ?: _queueIndex.value.takeIf { it in currentQueue.indices }
            ?: currentQueue.indexOfFirst { it.id == trackId }

        val updatedQueue = currentQueue.filterNot { it.id == trackId }
        if (updatedQueue.isEmpty()) {
            clearQueue()
            return
        }

        val removedBefore = currentQueue.take(oldIndex.coerceAtLeast(0)).count { it.id == trackId }
        val targetIndex = (oldIndex - removedBefore).coerceIn(0, updatedQueue.lastIndex)
        _queue.value = updatedQueue
        _queueIndex.value = targetIndex

        if (wasCurrent) {
            val nextTrack = updatedQueue[targetIndex]
            _currentTrack.value = nextTrack
            _position.value = 0L
            _duration.value = nextTrack.durationMs.coerceAtLeast(0L)
            clearAbLoop()
            playScrobbler.onTrackChanged(nextTrack.id, force = true)
        }

        execute { controller ->
            val removeIndices = mutableListOf<Int>()
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId == trackId) {
                    removeIndices.add(i)
                }
            }
            val wasPlaying = controller.isPlaying || controller.playWhenReady
            for (idx in removeIndices.asReversed()) {
                if (idx < controller.mediaItemCount) {
                    controller.removeMediaItem(idx)
                }
            }
            if (wasCurrent && controller.mediaItemCount > 0) {
                val safeIdx = targetIndex.coerceIn(0, controller.mediaItemCount - 1)
                if (controller.currentMediaItemIndex != safeIdx) {
                    controller.seekToDefaultPosition(safeIdx)
                }
                if (wasPlaying) {
                    controller.play()
                }
            }
            saveQueueState(immediate = true)
        }
    }
}
