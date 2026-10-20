package com.yasinjahantigh.logarhythm.feature.folder

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val rawFolderName: String = savedStateHandle.get<String>("folderName").orEmpty()
    val folderName: String = Uri.decode(rawFolderName)

    val tracks: StateFlow<List<Track>> = trackRepository.getTracksByFolder(folderName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun playAll() {
        val list = tracks.value
        if (list.isNotEmpty()) {
            playerController.play(list, 0)
        }
    }

    fun shuffleAll() {
        val list = tracks.value
        if (list.isNotEmpty()) {
            playerController.play(list.shuffled(), 0)
        }
    }

    fun playTrack(track: Track) {
        val list = tracks.value
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.play(list, index)
    }

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            if (currentTrack.value?.id == track.id) {
                playerController.next()
            }
            trackRepository.hideTrack(track.id)
        }
    }

    fun excludeFolder() {
        viewModelScope.launch {
            trackRepository.excludeFolder(folderName)
        }
    }
}
