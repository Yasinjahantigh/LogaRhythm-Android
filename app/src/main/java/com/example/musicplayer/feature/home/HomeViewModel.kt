package com.example.musicplayer.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.domain.ai.MusicAiEngine
import com.example.musicplayer.domain.mix.DailyMixGenerator
import com.example.musicplayer.domain.model.DailyMix
import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.domain.repository.StatsRepository
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    statsRepository: StatsRepository,
    trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val dailyMixGenerator = DailyMixGenerator()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyPlayed: StateFlow<List<Track>> = statsRepository.getRecentlyPlayedTracks(limit = 20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val mostPlayed: StateFlow<List<Track>> = statsRepository.getMostPlayedTracks(limit = 20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalListenTimeMs: StateFlow<Long> = statsRepository.getTotalListenTimeMs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    private val favoritesTrackIds = playlistRepository.getPlaylistTracks(1L)
        .map { tracks -> tracks.map { it.id }.toSet() }

    val dailyMixes: StateFlow<List<DailyMix>> = combine(
        trackRepository.getAllTracks(),
        statsRepository.getAllPlayEvents(),
        favoritesTrackIds
    ) { tracks, events, favIds ->
        dailyMixGenerator.generateMixes(
            allTracks = tracks,
            playEvents = events,
            favoriteTrackIds = favIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val allTracksFlow = trackRepository.getAllTracks()

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        playerController.play(tracks, startIndex)
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

    fun playAiMoodPlaylist(mood: MusicAiEngine.AiMood, onEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            allTracksFlow.collect { tracks ->
                val moodTracks = MusicAiEngine.generateMoodPlaylist(mood, tracks)
                if (moodTracks.isNotEmpty()) {
                    playerController.play(moodTracks, 0)
                } else {
                    onEmpty()
                }
                return@collect
            }
        }
    }
}
