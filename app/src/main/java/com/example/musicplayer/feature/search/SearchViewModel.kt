package com.example.musicplayer.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val recentSearches: StateFlow<List<String>> = preferencesManager.recentSearches
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    val searchResults: StateFlow<SearchResults> = _searchQuery
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                flowOf(SearchResults())
            } else {
                flow {
                    // Invalidate results immediately; debounce only the database work, not the UI state.
                    emit(SearchResults(isLoading = true))
                    delay(250L)
                    emitAll(
                        combine(
                            trackRepository.searchTracks(trimmed),
                            trackRepository.searchAlbums(trimmed),
                            trackRepository.searchArtists(trimmed)
                        ) { songs, albums, artists ->
                            SearchResults(
                                songs = songs,
                                albums = albums,
                                artists = artists,
                                isLoading = false
                            )
                        }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchResults()
        )

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearQuery() {
        _searchQuery.value = ""
    }

    fun saveRecentQuery(query: String = _searchQuery.value) {
        viewModelScope.launch {
            preferencesManager.addRecentSearch(query)
        }
    }

    fun onSelectRecentSearch(term: String) {
        _searchQuery.value = term
        saveRecentQuery(term)
    }

    fun onRemoveRecentSearch(term: String) {
        viewModelScope.launch {
            preferencesManager.removeRecentSearch(term)
        }
    }

    fun onClearRecentSearches() {
        viewModelScope.launch {
            preferencesManager.clearRecentSearches()
        }
    }

    fun playTrack(track: Track, allSongs: List<Track>) {
        saveRecentQuery()
        val index = allSongs.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.play(allSongs, index)
    }
}
