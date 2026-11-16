package com.yasinjahantigh.logarhythm.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.domain.model.AlbumStat
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.CustomDateRange
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.TimeRange
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatsTab {
    HISTORY,
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

    private val _customDateRange = MutableStateFlow<CustomDateRange?>(null)
    val customDateRange: StateFlow<CustomDateRange?> = _customDateRange.asStateFlow()

    private val rangeBounds = combine(_timeRange, _customDateRange) { range, custom ->
        range.toSinceTimestamp(customRange = custom) to range.toUntilTimestamp(customRange = custom)
    }

    private val _selectedTab = MutableStateFlow(StatsTab.HISTORY)
    val selectedTab: StateFlow<StatsTab> = _selectedTab.asStateFlow()

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    val recentlyPlayed: StateFlow<List<Track>> = statsRepository.getRecentlyPlayedTracks(limit = 100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalListenTimeMs: StateFlow<Long> = rangeBounds
        .flatMapLatest { (since, until) ->
            statsRepository.getTotalListenTimeMs(since, until)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val completedPlayCount: StateFlow<Int> = rangeBounds
        .flatMapLatest { (since, until) ->
            statsRepository.getCompletedPlayCount(since, until)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val peakHour: StateFlow<Int?> = rangeBounds
        .flatMapLatest { (since, until) ->
            statsRepository.getPeakHour(since, until)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val topTracks: StateFlow<List<TrackStat>> = rangeBounds
        .flatMapLatest { (since, until) ->
            statsRepository.getTopTracks(since, limit = 50, untilTimestamp = until)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val topArtists: StateFlow<List<ArtistStat>> = rangeBounds
        .flatMapLatest { (since, until) ->
            statsRepository.getTopArtists(since, limit = 50, untilTimestamp = until)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val topAlbums: StateFlow<List<AlbumStat>> = rangeBounds
        .flatMapLatest { (since, until) ->
            statsRepository.getTopAlbums(since, limit = 50, untilTimestamp = until)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setCustomDateRange(startMs: Long, endMs: Long) {
        val normalizedStart = minOf(startMs, endMs)
        // Include full end calendar day (UTC picker timestamps point to midnight 00:00).
        val normalizedEnd = maxOf(startMs, endMs) + 86_399_999L
        _customDateRange.value = CustomDateRange(normalizedStart, normalizedEnd)
        _timeRange.value = TimeRange.CUSTOM
    }

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
