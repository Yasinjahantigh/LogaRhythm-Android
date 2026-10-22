package com.yasinjahantigh.logarhythm.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.core.utils.PermissionUtils
import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.Folder
import com.yasinjahantigh.logarhythm.domain.model.Genre
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
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
    @param:ApplicationContext private val context: Context
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

    private val sortOptionFlow = MutableStateFlow(_uiState.value.sortOption)

    init {
        checkPermissionAndLoad()
        observeLibraryData()
    }

    /** Everything the library screen shows, sorted. */
    private data class SortedLibrary(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val genres: List<Genre>,
        val folders: List<Folder>
    )

    /**
     * One pipeline for all five lists. Mapping and sorting thousands of songs used to run on the
     * main thread once per emitted list (five full sorts on every library change), which is what
     * made scrolling and launch feel heavy. Now it runs once, on a background dispatcher, and the
     * UI only receives the finished result. Comparators use case-insensitive ordering directly, so
     * no temporary lowercase strings are created per comparison.
     */
    private fun observeLibraryData() {
        viewModelScope.launch {
            val data = combine(
                trackRepository.getAllTracks(),
                trackRepository.getAlbums(),
                trackRepository.getArtists(),
                trackRepository.getGenres(),
                trackRepository.getFolders()
            ) { tracks, albums, artists, genres, folders ->
                SortedLibrary(tracks, albums, artists, genres, folders)
            }
            combine(data, sortOptionFlow) { library, sort -> sortLibrary(library, sort) }
                .flowOn(Dispatchers.Default)
                .collect { sorted ->
                    _uiState.update {
                        it.copy(
                            tracks = sorted.tracks,
                            albums = sorted.albums,
                            artists = sorted.artists,
                            genres = sorted.genres,
                            folders = sorted.folders,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun sortLibrary(library: SortedLibrary, sort: SortOption): SortedLibrary {
        val asc = sort.direction == SortDirection.ASCENDING
        val text = String.CASE_INSENSITIVE_ORDER

        fun <T> List<T>.orderedBy(comparator: Comparator<T>): List<T> =
            if (asc) sortedWith(comparator) else sortedWith(comparator.reversed())

        val tracks = when (sort.field) {
            SortField.TITLE -> library.tracks.orderedBy(compareBy(text) { it.title })
            SortField.ARTIST -> library.tracks.orderedBy(compareBy(text) { it.artist })
            SortField.ALBUM -> library.tracks.orderedBy(compareBy(text) { it.album })
            SortField.DATE_ADDED -> library.tracks.orderedBy(compareBy { it.dateAdded })
            SortField.DURATION -> library.tracks.orderedBy(compareBy { it.durationMs })
        }
        return SortedLibrary(
            tracks = tracks,
            albums = library.albums.orderedBy(compareBy(text) { it.name }),
            artists = library.artists.orderedBy(compareBy(text) { it.name }),
            genres = library.genres.orderedBy(compareBy(text) { it.name }),
            folders = library.folders.orderedBy(compareBy(text) { it.name })
        )
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { it.copy(sortOption = sortOption) }
        sortOptionFlow.value = sortOption
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.play(tracks, index)
    }

    /** Plays the whole (sorted) library in order, from the top. */
    fun playAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        playerController.setShuffleEnabled(false)
        playerController.play(tracks, 0)
    }

    /** Shuffles the whole library, starting from a random song. */
    fun shuffleAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        playerController.setShuffleEnabled(true)
        playerController.play(tracks, tracks.indices.random())
    }

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun playTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playerController.setShuffleEnabled(false)
        playerController.play(tracks, 0)
    }

    /** Reversed insertion keeps the visible selection order when each item lands right after the current song. */
    fun playNext(tracks: List<Track>) {
        tracks.asReversed().forEach(playerController::playNext)
    }

    fun addToQueue(tracks: List<Track>) {
        tracks.forEach(playerController::addToQueue)
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch { playlistRepository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<String>) {
        viewModelScope.launch {
            trackIds.forEach { playlistRepository.addTrackToPlaylist(playlistId, it) }
        }
    }

    fun addTracksToFavorites(tracks: List<Track>) {
        viewModelScope.launch {
            playlistRepository.addFavorites(tracks.map { it.id })
        }
    }

    fun hideTracks(tracks: List<Track>) {
        viewModelScope.launch {
            val currentId = currentTrack.value?.id
            if (tracks.any { it.id == currentId }) playerController.next()
            tracks.forEach { trackRepository.hideTrack(it.id) }
        }
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
            val updated = try {
                trackRepository.updateTrackMetadata(id, title, artist, album, genre)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (updated != null) playerController.updateTrackInPlayback(updated)
            onDone(updated)
        }
    }

    fun deleteTrack(track: Track, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = try {
                trackRepository.deleteTrack(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            onDone(deleted)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }

    fun checkPermissionAndLoad() {
        val granted = PermissionUtils.hasAudioPermission(context)
        _uiState.update { it.copy(hasPermission = granted) }
        // No auto-scan: the library renders from the Room snapshot, and LibrarySync refreshes
        // only when the MediaStore stamp changed or the user pulls to refresh.
        if (!granted) _uiState.update { it.copy(isLoading = false) }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = isGranted) }
        // LibrarySync (forced refresh from onboarding) owns scanning.
        if (!isGranted) _uiState.update { it.copy(isLoading = false) }
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

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            if (currentTrack.value?.id == track.id) {
                playerController.next()
            }
            trackRepository.hideTrack(track.id)
        }
    }
}
