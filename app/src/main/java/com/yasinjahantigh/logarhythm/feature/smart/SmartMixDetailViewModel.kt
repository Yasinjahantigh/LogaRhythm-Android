package com.yasinjahantigh.logarhythm.feature.smart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.SmartMixRepository
import com.yasinjahantigh.logarhythm.domain.smart.SmartMix
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartMixDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    smartMixRepository: SmartMixRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController
) : ViewModel() {

    val type: SmartMixType =
        SmartMixType.fromRaw(savedStateHandle.get<String>("mixType")) ?: SmartMixType.RECENTLY_ADDED

    val mix: StateFlow<SmartMix?> = smartMixRepository.getSmartMix(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tracks: StateFlow<List<Track>> = smartMixRepository.getSmartMixTracks(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playAll() {
        val list = tracks.value
        if (list.isNotEmpty()) playerController.play(list, 0)
    }

    fun shuffleAll() {
        val list = tracks.value
        if (list.isNotEmpty()) playerController.play(list.shuffled(), 0)
    }

    fun playTrackAt(index: Int) {
        val list = tracks.value
        if (index in list.indices) playerController.play(list, index)
    }

    fun saveAsPlaylist(name: String, onSaved: (Long) -> Unit) {
        val ids = tracks.value.map { it.id }
        if (ids.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            if (playlistId > 0) {
                playlistRepository.addTracksToPlaylist(playlistId, ids)
                onSaved(playlistId)
            }
        }
    }
}
