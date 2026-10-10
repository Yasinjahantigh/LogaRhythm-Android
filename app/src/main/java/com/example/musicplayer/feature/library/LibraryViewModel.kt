package com.example.musicplayer.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.core.utils.PermissionUtils
import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Folder
import com.example.musicplayer.domain.model.Genre
import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            hasPermission = PermissionUtils.hasAudioPermission(context)
        )
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private val scanErrorChannel = Channel<Unit>(Channel.BUFFERED)
    val scanErrors: Flow<Unit> = scanErrorChannel.receiveAsFlow()
    private val scanSuccessChannel = Channel<Int>(Channel.BUFFERED)
    val scanSuccess: Flow<Int> = scanSuccessChannel.receiveAsFlow()

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var rawTracks: List<Track> = emptyList()
    private var rawAlbums: List<Album> = emptyList()
    private var rawArtists: List<Artist> = emptyList()
    private var rawGenres: List<Genre> = emptyList()
    private var rawFolders: List<Folder> = emptyList()

    init {
        checkPermissionAndLoad()
        observeLibraryData()
    }

    private fun observeLibraryData() {
        viewModelScope.launch {
            trackRepository.getAllTracks().collect { trackList ->
                rawTracks = trackList
                applySortAndUpdate()
            }
        }
        viewModelScope.launch {
            trackRepository.getAlbums().collect { albumList ->
                rawAlbums = albumList
                applySortAndUpdate()
            }
        }
        viewModelScope.launch {
            trackRepository.getArtists().collect { artistList ->
                rawArtists = artistList
                applySortAndUpdate()
            }
        }
        viewModelScope.launch {
            trackRepository.getGenres().collect { genreList ->
                rawGenres = genreList
                applySortAndUpdate()
            }
        }
        viewModelScope.launch {
            trackRepository.getFolders().collect { folderList ->
                rawFolders = folderList
                applySortAndUpdate()
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(sortOption = sortOption) }
        applySortAndUpdate()
    }

    private fun applySortAndUpdate() {
        val sort = _uiState.value.sortOption
        val isAsc = sort.direction == SortDirection.ASCENDING

        val sortedTracks = when (sort.field) {
            SortField.TITLE -> if (isAsc) rawTracks.sortedBy { it.title.lowercase() } else rawTracks.sortedByDescending { it.title.lowercase() }
            SortField.ARTIST -> if (isAsc) rawTracks.sortedBy { it.artist.lowercase() } else rawTracks.sortedByDescending { it.artist.lowercase() }
            SortField.ALBUM -> if (isAsc) rawTracks.sortedBy { it.album.lowercase() } else rawTracks.sortedByDescending { it.album.lowercase() }
            SortField.DATE_ADDED -> if (isAsc) rawTracks.sortedBy { it.dateAdded } else rawTracks.sortedByDescending { it.dateAdded }
            SortField.DURATION -> if (isAsc) rawTracks.sortedBy { it.durationMs } else rawTracks.sortedByDescending { it.durationMs }
        }

        val sortedAlbums = if (isAsc) rawAlbums.sortedBy { it.name.lowercase() } else rawAlbums.sortedByDescending { it.name.lowercase() }
        val sortedArtists = if (isAsc) rawArtists.sortedBy { it.name.lowercase() } else rawArtists.sortedByDescending { it.name.lowercase() }
        val sortedGenres = if (isAsc) rawGenres.sortedBy { it.name.lowercase() } else rawGenres.sortedByDescending { it.name.lowercase() }
        val sortedFolders = if (isAsc) rawFolders.sortedBy { it.name.lowercase() } else rawFolders.sortedByDescending { it.name.lowercase() }

        _uiState.update {
            it.copy(
                tracks = sortedTracks,
                albums = sortedAlbums,
                artists = sortedArtists,
                genres = sortedGenres,
                folders = sortedFolders,
                isLoading = false
            )
        }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.play(tracks, index)
    }

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch { playlistRepository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun updateTrackMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        onDone: (Track?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val updated = trackRepository.updateTrackMetadata(id, title, artist, album, genre)
            if (updated != null) {
                playerController.updateTrackInPlayback(updated)
            }
            onDone(updated)
        }
    }

    fun deleteTrack(track: Track, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            trackRepository.deleteTrack(track)
            onDone()
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }

    fun checkPermissionAndLoad() {
        val granted = PermissionUtils.hasAudioPermission(context)
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) refresh(isPullToRefresh = false)
        else _uiState.update { it.copy(isLoading = false) }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = isGranted) }
        if (isGranted) refresh(isPullToRefresh = false)
    }

    fun refresh(isPullToRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                trackRepository.refreshTracks()
                val count = _uiState.value.tracks.size
                scanSuccessChannel.trySend(count)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _uiState.update { it.copy(hasPermission = false) }
                scanErrorChannel.trySend(Unit)
            } catch (_: Exception) {
                // Keep the last good database snapshot and tell the user the refresh failed.
                scanErrorChannel.trySend(Unit)
            } finally {
                _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
            }
        }
    }
}
