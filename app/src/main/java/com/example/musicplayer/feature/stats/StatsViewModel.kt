package com.example.musicplayer.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.domain.model.AlbumStat
import com.example.musicplayer.domain.model.ArtistStat
import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.TimeRange
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackStat
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.domain.repository.StatsRepository
import com.example.musicplayer.playback.PlayerController
import com.example.musicplayer.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatsTab {
    TRACKS,
    ARTISTS,
    ALBUMS
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val timeRange: StateFlow<TimeRange> = _timeRange.asStateFlow()

    private val _selectedTab = MutableStateFlow(StatsTab.TRACKS)
    val selectedTab: StateFlow<StatsTab> = _selectedTab.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalListenTimeMs: StateFlow<Long> = _timeRange
        .flatMapLatest { range ->
            statsRepository.getTotalListenTimeMs(range.toSinceTimestamp())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val topTracks: StateFlow<List<TrackStat>> = _timeRange
        .flatMapLatest { range ->
            statsRepository.getTopTracks(range.toSinceTimestamp(), limit = 50)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val topArtists: StateFlow<List<ArtistStat>> = _timeRange
        .flatMapLatest { range ->
            statsRepository.getTopArtists(range.toSinceTimestamp(), limit = 50)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val topAlbums: StateFlow<List<AlbumStat>> = _timeRange
        .flatMapLatest { range ->
            statsRepository.getTopAlbums(range.toSinceTimestamp(), limit = 50)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
    }

    fun setSelectedTab(tab: StatsTab) {
        _selectedTab.value = tab
    }

    fun playTrackFromTop(tracks: List<Track>, index: Int) {
        playerController.play(tracks, index)
    }

    fun playNext(track: Track) {
        playerController.playNext(track)
    }

    fun addToQueue(track: Track) {
        playerController.addToQueue(track)
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            trackRepository.hideTrack(track.id)
        }
    }
}
