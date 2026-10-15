package com.example.musicplayer.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.playback.PlayerController
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
    private val trackRepository: com.example.musicplayer.domain.repository.TrackRepository,
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
            val shuffled = list.shuffled()
            playerController.play(shuffled, 0)
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

    fun addTrackToPlaylist(targetPlaylistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(targetPlaylistId, trackId)
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
