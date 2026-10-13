package com.example.musicplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.domain.ai.MusicAiEngine
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.domain.repository.StatsRepository
import com.example.musicplayer.domain.taste.TasteProfile
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    preferencesManager: PreferencesManager
) : ViewModel() {

    val isCompactMiniPlayer: StateFlow<Boolean> = preferencesManager.isCompactMiniPlayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val position: StateFlow<Long> = playerController.position
    val duration: StateFlow<Long> = playerController.duration
    val shuffle: StateFlow<Boolean> = playerController.shuffle
    val repeatMode: StateFlow<Int> = playerController.repeatMode
    val queue: StateFlow<List<Track>> = playerController.queue

    // Phase 7: Extras
    val playbackSpeed: StateFlow<Float> = playerController.playbackSpeed
    val sleepTimerRemainingMs: StateFlow<Long?> = playerController.sleepTimerRemainingMs
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

    init {
        viewModelScope.launch {
            currentTrack.collect {
                _isDismissed.value = false
            }
        }
    }

    fun dismissMiniPlayer() {
        _isDismissed.value = true
    }

    fun expand() { _isExpanded.value = true }
    fun collapse() { _isExpanded.value = false; _isQueueOpen.value = false }

    fun openQueue() { _isQueueOpen.value = true }
    fun closeQueue() { _isQueueOpen.value = false }

    fun togglePlayPause() {
        if (isPlaying.value) playerController.pause() else playerController.resume()
    }

    fun next() = playerController.next()
    fun previous() = playerController.previous()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun toggleShuffle() = playerController.toggleShuffle()
    fun cycleRepeat() = playerController.cycleRepeat()
    fun play(list: List<Track>, startIndex: Int = 0) = playerController.play(list, startIndex)

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)
    fun removeFromQueue(index: Int): Track? = playerController.removeFromQueue(index)
    fun insertIntoQueue(index: Int, track: Track) = playerController.insertIntoQueue(index, track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playerController.moveQueueItem(fromIndex, toIndex)
    fun playQueueItem(index: Int) = playerController.playQueueItem(index)
    fun clearQueue() = playerController.clearQueue()

    // Phase 7: Extras methods
    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int) = playerController.setSleepTimer(minutes)
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
            val recommendations = MusicAiEngine.recommendNextTracks(
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
}
