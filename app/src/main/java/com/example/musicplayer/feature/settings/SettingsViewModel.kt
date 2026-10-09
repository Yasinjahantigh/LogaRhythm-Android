package com.example.musicplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val trackRepository: TrackRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = preferencesManager.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "system"
        )

    val colorSource: StateFlow<String> = preferencesManager.colorSource
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "fixed_color"
        )

    val cornerRoundness: StateFlow<String> = preferencesManager.cornerRoundness
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "standard"
        )

    val isCompactMiniPlayer: StateFlow<Boolean> = preferencesManager.isCompactMiniPlayer
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val minTrackLengthSec: StateFlow<Int> = preferencesManager.minTrackLengthSec
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 30
        )

    val excludedFolders: StateFlow<Set<String>> = preferencesManager.excludedFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val allFolders: StateFlow<List<String>> = trackRepository.getFolders()
        .map { folders -> folders.map { it.name }.filter { it.isNotBlank() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isLrclibEnabled: StateFlow<Boolean> = preferencesManager.isLrclibEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun setColorSource(source: String) {
        viewModelScope.launch {
            preferencesManager.setColorSource(source)
        }
    }

    fun setCornerRoundness(roundness: String) {
        viewModelScope.launch {
            preferencesManager.setCornerRoundness(roundness)
        }
    }

    fun setCompactMiniPlayer(compact: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCompactMiniPlayer(compact)
        }
    }

    fun setMinTrackLengthSec(seconds: Int) {
        viewModelScope.launch {
            preferencesManager.setMinTrackLengthSec(seconds)
            trackRepository.refreshTracks()
        }
    }

    fun toggleExcludedFolder(folder: String) {
        viewModelScope.launch {
            preferencesManager.toggleExcludedFolder(folder)
            trackRepository.refreshTracks()
        }
    }

    fun setLrclibEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setLrclibEnabled(enabled)
        }
    }
}
