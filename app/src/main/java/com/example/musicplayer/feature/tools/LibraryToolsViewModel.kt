package com.example.musicplayer.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.domain.library.ArtistSplitter
import com.example.musicplayer.domain.library.DuplicateFinder
import com.example.musicplayer.domain.library.DuplicateGroup
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Duplicates and "missing tags" views; both are computed off the main thread from the library. */
@HiltViewModel
class LibraryToolsViewModel @Inject constructor(
    trackRepository: TrackRepository,
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

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    fun play(track: Track) = playerController.play(listOf(track), 0)
    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)
}
