package com.yasinjahantigh.logarhythm.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.SmartMixRepository
import com.yasinjahantigh.logarhythm.domain.smart.SmartMix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    smartMixRepository: SmartMixRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoritePlaylistIds: StateFlow<Set<Long>> = preferencesManager.favoritePlaylistIds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val smartMixes: StateFlow<List<SmartMix>> = smartMixRepository.getSmartMixes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleFavoritePlaylist(playlistId: Long) {
        viewModelScope.launch {
            preferencesManager.toggleFavoritePlaylist(playlistId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, newName)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            if (id in favoritePlaylistIds.value) {
                preferencesManager.toggleFavoritePlaylist(id)
            }
            playlistRepository.deletePlaylist(id)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun createPlaylistWithTracks(name: String, trackIds: List<String>, onSaved: (Long) -> Unit = {}) {
        if (name.isBlank() || trackIds.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            if (playlistId > 0) {
                playlistRepository.addTracksToPlaylist(playlistId, trackIds)
                onSaved(playlistId)
            }
        }
    }
}
