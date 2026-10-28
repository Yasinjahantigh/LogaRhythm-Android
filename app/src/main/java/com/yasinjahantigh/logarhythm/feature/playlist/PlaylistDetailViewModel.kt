package com.yasinjahantigh.logarhythm.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: com.yasinjahantigh.logarhythm.domain.repository.TrackRepository,
    private val playerController: PlayerController
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 1L

    val playlist: StateFlow<Playlist?> = playlistRepository.getPlaylistById(playlistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val tracks: StateFlow<List<Track>> = playlistRepository.getPlaylistTracks(playlistId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun playAll() {
        val list = tracks.value
        if (list.isNotEmpty()) {
            playerController.play(list, 0)
        }
    }

    fun shuffleAll() {
        val list = tracks.value
        if (list.isNotEmpty()) {
            playerController.setShuffleEnabled(true)
            playerController.play(list, list.indices.random())
        }
    }

    fun playTrackAt(index: Int) {
        val list = tracks.value
        if (list.isNotEmpty() && index in list.indices) {
            playerController.play(list, index)
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun reorderTracks(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playlistRepository.reorderPlaylistTracks(playlistId, fromIndex, toIndex)
        }
    }

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun playTracks(selected: List<Track>) {
        if (selected.isEmpty()) return
        playerController.setShuffleEnabled(false)
        playerController.play(selected, 0)
    }

    fun playNext(selected: List<Track>) {
        selected.asReversed().forEach(playerController::playNext)
    }

    fun addToQueue(selected: List<Track>) {
        selected.forEach(playerController::addToQueue)
    }

    fun addTrackToPlaylist(targetPlaylistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(targetPlaylistId, trackId)
        }
    }

    fun addTracksToPlaylist(targetPlaylistId: Long, trackIds: List<String>) {
        viewModelScope.launch {
            trackIds.forEach { playlistRepository.addTrackToPlaylist(targetPlaylistId, it) }
        }
    }

    fun addTracksToFavorites(selected: List<Track>) {
        viewModelScope.launch {
            playlistRepository.addFavorites(selected.map { it.id })
        }
    }

    fun removeTracks(trackIds: List<String>) {
        viewModelScope.launch {
            trackIds.forEach { playlistRepository.removeTrackFromPlaylist(playlistId, it) }
        }
    }

    fun hideTracks(selected: List<Track>) {
        viewModelScope.launch {
            if (selected.any { it.id == playerController.currentTrack.value?.id }) {
                playerController.next()
            }
            selected.forEach { trackRepository.hideTrack(it.id) }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            if (playerController.currentTrack.value?.id == track.id) {
                playerController.next()
            }
            trackRepository.hideTrack(track.id)
        }
    }
}
