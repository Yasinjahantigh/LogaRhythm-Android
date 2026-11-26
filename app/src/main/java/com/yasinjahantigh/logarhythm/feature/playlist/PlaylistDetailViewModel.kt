package com.yasinjahantigh.logarhythm.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: com.yasinjahantigh.logarhythm.domain.repository.TrackRepository,
    private val playerController: PlayerController,
    private val preferencesManager: PreferencesManager
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

    val allTracks: StateFlow<List<Track>> = trackRepository.getAllTracks()
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

    val isFavorite: StateFlow<Boolean> = preferencesManager.favoritePlaylistIds
        .map { playlistId in it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleFavorite() {
        viewModelScope.launch {
            preferencesManager.toggleFavoritePlaylist(playlistId)
        }
    }

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
            playlistRepository.addTracksToPlaylist(targetPlaylistId, trackIds)
        }
    }

    fun addTracksToCurrentPlaylist(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.addTracksToPlaylist(playlistId, trackIds)
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

    fun deleteTrack(track: Track, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (playerController.currentTrack.value?.id == track.id) {
                playerController.next()
            }
            val ok = try {
                trackRepository.deleteTrack(track)
            } catch (_: Exception) {
                false
            }
            onDone(ok)
        }
    }

    fun deleteTracks(tracks: List<Track>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val currentId = playerController.currentTrack.value?.id
            if (tracks.any { it.id == currentId }) {
                playerController.next()
            }
            val count = try {
                trackRepository.deleteTracks(tracks)
            } catch (_: Exception) {
                0
            }
            onDone(count)
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
            if (isFavorite.value) {
                preferencesManager.toggleFavoritePlaylist(playlistId)
            }
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
