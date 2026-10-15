package com.example.musicplayer.feature.artist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val rawArtistName: String = savedStateHandle.get<String>("artistName").orEmpty()
    val artistName: String = Uri.decode(rawArtistName)

    val artist: StateFlow<Artist?> = trackRepository.getArtistByName(artistName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val tracks: StateFlow<List<Track>> = trackRepository.getTracksByArtist(artistName)
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
}
