package com.yasinjahantigh.logarhythm.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.repository.ArtistPhotoRepository
import com.yasinjahantigh.logarhythm.data.room.SavedQueueDao
import com.yasinjahantigh.logarhythm.data.room.SavedQueueEntity
import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.AudioOutputDevice
import com.yasinjahantigh.logarhythm.playback.AudioOutputManager
import com.yasinjahantigh.logarhythm.playback.AudioOutputState
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
    private val statsRepository: StatsRepository,
    private val preferencesManager: PreferencesManager,
    private val savedQueueDao: SavedQueueDao,
    private val audioOutputManager: AudioOutputManager,
    private val artistPhotoRepository: ArtistPhotoRepository
) : ViewModel() {

    val audioOutputState: StateFlow<AudioOutputState> = audioOutputManager.state

    fun refreshAudioOutputs() = audioOutputManager.refresh()
    fun selectAudioOutput(device: AudioOutputDevice): Boolean {
        val switched = audioOutputManager.selectDevice(device)
        if (switched) {
            playerController.setPreferredAudioDevice(device.id)
        }
        return switched
    }
    fun setMediaVolume(fraction: Float) = audioOutputManager.setMediaVolume(fraction)
    fun openSystemAudioOutputSwitcher(context: android.content.Context): Boolean =
        audioOutputManager.openSystemOutputSwitcher(context)

    val isCompactMiniPlayer: StateFlow<Boolean> = preferencesManager.isCompactMiniPlayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val doubleTapSeekEnabled: StateFlow<Boolean> = preferencesManager.doubleTapSeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isVinylMode: StateFlow<Boolean> = preferencesManager.vinylMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.isVinylModeCached())

    fun setVinylMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setVinylMode(enabled)
        }
    }

    val savedQueues: StateFlow<List<SavedQueueEntity>> = savedQueueDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val position: StateFlow<Long> = playerController.position
    val duration: StateFlow<Long> = playerController.duration
    val shuffle: StateFlow<Boolean> = playerController.shuffle
    val repeatMode: StateFlow<Int> = playerController.repeatMode
    val queue: StateFlow<List<Track>> = playerController.queue
    val queueIndex: StateFlow<Int> = playerController.queueIndex

    val playbackSpeed: StateFlow<Float> = playerController.playbackSpeed
    val playbackPitch: StateFlow<Float> = playerController.playbackPitch
    val slowedReverb: StateFlow<Boolean> = playerController.slowedReverb
    val abLoopStartMs: StateFlow<Long?> = playerController.abLoopStartMs
    val abLoopEndMs: StateFlow<Long?> = playerController.abLoopEndMs
    val sleepTimerRemainingMs: StateFlow<Long?> = playerController.sleepTimerRemainingMs
    val sleepTimerRemainingTracks: StateFlow<Int?> = playerController.sleepTimerRemainingTracks
    val isSleepTimerEndOfTrack: StateFlow<Boolean> = playerController.isSleepTimerEndOfTrack

    val isFavorite: StateFlow<Boolean> = currentTrack.flatMapLatest { track ->
        if (track == null) flowOf(false) else playlistRepository.isTrackFavorite(track.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _isQueueOpen = MutableStateFlow(false)
    val isQueueOpen: StateFlow<Boolean> = _isQueueOpen.asStateFlow()

    private val _isDismissed = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = _isDismissed.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _showShareCardStudio = MutableStateFlow(false)
    val showShareCardStudio: StateFlow<Boolean> = _showShareCardStudio.asStateFlow()

    val currentArtistInfo: StateFlow<PlayerArtistInfo?> = combine(
        currentTrack,
        trackRepository.getAllTracks(),
        statsRepository.getAllPlayEvents(),
        artistPhotoRepository.photos
    ) { track, allTracks, events, photos ->
        val rawArtist = track?.artist?.trim().orEmpty()
        if (rawArtist.isBlank() || rawArtist.equals("<unknown>", true) || rawArtist.equals("unknown", true)) {
            return@combine null
        }
        val primaryArtist = ArtistSplitter.split(rawArtist).firstOrNull()?.trim().takeUnless { it.isNullOrBlank() }
            ?: rawArtist
        val artistKey = artistPhotoRepository.normalizeKey(primaryArtist)
        val artistTracks = allTracks.filter { candidate ->
            ArtistSplitter.split(candidate.artist).any {
                artistPhotoRepository.normalizeKey(it) == artistKey
            } || artistPhotoRepository.normalizeKey(candidate.artist) == artistKey
        }
        val artistTrackIds = artistTracks.mapTo(HashSet()) { it.id }
        val playCountByTrack = HashMap<String, Int>()
        var totalPlays = 0
        var totalListenMs = 0L
        for (ev in events) {
            if (ev.trackId in artistTrackIds) {
                if (!ev.skipped) {
                    totalPlays++
                    playCountByTrack[ev.trackId] = (playCountByTrack[ev.trackId] ?: 0) + 1
                }
                totalListenMs += ev.listenedMs
            }
        }
        val rankedTop = artistTracks.sortedWith(
            compareByDescending<Track> { playCountByTrack[it.id] ?: 0 }
                .thenBy { it.title.lowercase() }
        ).take(5)
        val photoUri = photos[artistKey]
            ?: artistTracks.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) }
            ?: track?.artworkUri
        PlayerArtistInfo(
            artistName = primaryArtist,
            photoUri = photoUri,
            playCount = totalPlays,
            totalListenTimeMs = totalListenMs,
            libraryTrackCount = artistTracks.size,
            topTracks = rankedTop
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            currentTrack.collect { track ->
                _isDismissed.value = false
                val primaryArtist = track?.artist?.let { ArtistSplitter.split(it).firstOrNull()?.trim() }
                if (!primaryArtist.isNullOrBlank() &&
                    !primaryArtist.equals("<unknown>", true) &&
                    !primaryArtist.equals("unknown", true)
                ) {
                    launch {
                        runCatching { artistPhotoRepository.ensureFetched(primaryArtist, force = false) }
                    }
                }
            }
        }
        viewModelScope.launch {
            isPlaying.collect { playing ->
                if (playing) _isDismissed.value = false
            }
        }
    }

    fun dismissMiniPlayer() {
        _isDismissed.value = true
    }

    fun expand() { _isExpanded.value = true }
    fun collapse() {
        _isExpanded.value = false
        _isQueueOpen.value = false
        _showLyrics.value = false
        _showShareCardStudio.value = false
    }

    fun setShowLyrics(show: Boolean) { _showLyrics.value = show }
    fun setShowShareCardStudio(show: Boolean) { _showShareCardStudio.value = show }

    fun openQueue() {
        _isQueueOpen.value = true
        _showLyrics.value = false
        _showShareCardStudio.value = false
    }
    fun closeQueue() { _isQueueOpen.value = false }

    fun togglePlayPause() {
        if (isPlaying.value) playerController.pause() else playerController.resume()
    }

    fun next() = playerController.next()
    fun previous() = playerController.previous()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun reshuffleQueue() = playerController.reshuffleQueue()
    fun cycleRepeat() = playerController.cycleRepeat()
    fun play(list: List<Track>, startIndex: Int = 0) = playerController.play(list, startIndex)

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)
    fun removeFromQueue(index: Int): Track? = playerController.removeFromQueue(index)
    fun insertIntoQueue(index: Int, track: Track) = playerController.insertIntoQueue(index, track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playerController.moveQueueItem(fromIndex, toIndex)
    fun playQueueItem(index: Int) = playerController.playQueueItem(index)

    fun commitCoverPeek(expectedIndex: Int, targetIndex: Int) {
        if (playerController.currentQueueIndex() == expectedIndex && kotlin.math.abs(targetIndex - expectedIndex) == 1) {
            playerController.playQueueItem(targetIndex)
        }
    }
    fun clearQueue() = playerController.clearQueue()

    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = playerController.setPlaybackPitch(pitch)
    fun setSlowedReverb(enabled: Boolean) = playerController.setSlowedReverb(enabled)
    fun resetPlaybackSpeedAndPitch() = playerController.resetPlaybackSpeedAndPitch()
    fun toggleAbLoopMarker() = playerController.toggleAbLoopMarker()
    fun clearAbLoop() = playerController.clearAbLoop()

    fun saveCurrentQueueAsSlot(name: String, onSaved: () -> Unit = {}) {
        val trimmed = name.trim()
        val list = queue.value
        if (trimmed.isBlank() || list.isEmpty()) return
        val idsJson = org.json.JSONArray(list.map { it.id }).toString()
        val currentIdx = playerController.currentQueueIndex().coerceIn(0, list.lastIndex)
        val posMs = position.value.coerceAtLeast(0L)
        val isShuffled = shuffle.value
        viewModelScope.launch {
            savedQueueDao.upsert(
                SavedQueueEntity(
                    queueId = java.util.UUID.randomUUID().toString(),
                    name = trimmed,
                    trackIdsJson = idsJson,
                    currentIndex = currentIdx,
                    positionMs = posMs,
                    shuffle = isShuffled,
                    updatedAt = System.currentTimeMillis()
                )
            )
            onSaved()
        }
    }

    fun loadSavedQueueSlot(slot: SavedQueueEntity, onLoaded: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val allById = trackRepository.getAllTracks().first().associateBy { it.id }
            val arr = runCatching { org.json.JSONArray(slot.trackIdsJson) }.getOrNull()
            val tracks = if (arr == null) emptyList() else {
                (0 until arr.length()).mapNotNull { i -> allById[arr.optString(i)] }
            }
            if (tracks.isEmpty()) {
                onLoaded(false)
                return@launch
            }
            val startIdx = slot.currentIndex.coerceIn(0, tracks.lastIndex)
            playerController.play(tracks, startIdx)
            if (slot.positionMs > 0L) playerController.seekTo(slot.positionMs)
            onLoaded(true)
        }
    }

    fun deleteSavedQueueSlot(queueId: String) {
        viewModelScope.launch { savedQueueDao.delete(queueId) }
    }
    fun setSleepTimer(minutes: Int) = playerController.setSleepTimer(minutes)
    fun setSleepTimerTracks(tracks: Int) = playerController.setSleepTimerTracks(tracks)
    fun setSleepTimerEndOfTrack() = playerController.setSleepTimerEndOfTrack()
    fun cancelSleepTimer() = playerController.cancelSleepTimer()

    /** Starts a relevance-first radio queue without losing the current song's playback position. */
    fun startTrackRadio(onResult: (recommendedCount: Int) -> Unit = {}) {
        val seed = currentTrack.value ?: return
        viewModelScope.launch {
            val library = trackRepository.getAllTracks().first()
            // Radio learns from real listening: songs heard together rise, skipped songs sink.
            val favoriteIds = playlistRepository.getPlaylistTracks(1L).first().map { it.id }.toSet()
            val profile = TasteProfile.build(statsRepository.getAllPlayEvents().first(), favoriteIds)
            val recommendations = TrackCuration.recommendNextTracks(
                currentTrack = seed,
                allTracks = library,
                limit = 12,
                profile = profile
            )
            if (recommendations.isNotEmpty()) playerController.playRadio(seed, recommendations)
            onResult(recommendations.size)
        }
    }

    fun toggleFavorite() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            playlistRepository.toggleFavorite(track.id)
        }
    }

    fun updateTrackMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        onDone: (Track?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val updated = try {
                trackRepository.updateTrackMetadata(id, title, artist, album, genre)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (updated != null) playerController.updateTrackInPlayback(updated)
            onDone(updated)
        }
    }

    fun hideCurrentTrack(onHidden: (Track) -> Unit = {}) {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            next()
            trackRepository.hideTrack(track.id)
            onHidden(track)
        }
    }

    fun deleteCurrentTrack(track: Track, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = try {
                trackRepository.deleteTrack(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (deleted) {
                playerController.removeTrackFromPlayback(track.id)
                if (playerController.queue.value.isEmpty()) {
                    collapse()
                }
            }
            onDone(deleted)
        }
    }
}

data class PlayerArtistInfo(
    val artistName: String,
    val photoUri: String?,
    val playCount: Int,
    val totalListenTimeMs: Long,
    val libraryTrackCount: Int,
    val topTracks: List<Track>
)
