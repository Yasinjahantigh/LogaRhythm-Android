package com.yasinjahantigh.logarhythm.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder
import com.yasinjahantigh.logarhythm.domain.library.DuplicateGroup
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Duplicates, "missing tags", and hidden tracks views; computed off the main thread from the library. */
@HiltViewModel
class LibraryToolsViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    preferencesManager: PreferencesManager,
    private val playerController: PlayerController
) : ViewModel() {

    private val separators = preferencesManager.artistSeparators.map { ArtistSplitter.parseSeparators(it) }

    val duplicates: StateFlow<List<DuplicateGroup>?> =
        combine(trackRepository.getAllTracks(), separators) { tracks, seps ->
            DuplicateFinder.find(tracks, seps)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val missingTags: StateFlow<List<Track>?> =
        trackRepository.getAllTracks()
            .map { DuplicateFinder.missingTags(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hiddenTracks: StateFlow<List<Track>?> =
        trackRepository.getHiddenTracks()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    fun play(track: Track) = playerController.play(listOf(track), 0)
    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            trackRepository.hideTrack(track.id)
        }
    }

    fun unhideTrack(trackId: String) {
        viewModelScope.launch {
            trackRepository.unhideTrack(trackId)
        }
    }

    fun unhideAllTracks() {
        viewModelScope.launch {
            trackRepository.unhideAllTracks()
        }
    }
}
