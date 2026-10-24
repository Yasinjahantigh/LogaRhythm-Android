package com.yasinjahantigh.logarhythm.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.core.utils.PermissionUtils
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Artist
import com.yasinjahantigh.logarhythm.domain.model.Folder
import com.yasinjahantigh.logarhythm.domain.model.Genre
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
    private val preferencesManager: PreferencesManager,
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
    private val trackFilterFlow = MutableStateFlow(TrackFilter.ALL)
    private val selectedTabFlow = MutableStateFlow(LibraryTab.SONGS)
    private var randomSeed = System.nanoTime()

    init {
        checkPermissionAndLoad()
        observeSortPreference()
        observeLibraryData()
    }

    /** Everything the library screen shows, sorted. */
    private data class SortedLibrary(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val genres: List<Genre>,
        val folders: List<Folder>,
        val visibleTracks: List<Track> = emptyList(),
        val playStats: Map<String, TrackPlayStat> = emptyMap(),
        val favorites: Set<String> = emptySet(),
        val downloadedUris: Set<String> = emptySet()
    )

    /** Each tab remembers its own field/direction, so switching tabs does not reset the list. */
    private fun observeSortPreference() {
        viewModelScope.launch {
            selectedTabFlow.flatMapLatest { tab ->
                preferencesManager.librarySortOrder(tab.name).map { (fieldName, directionName) ->
                    val field = runCatching { SortField.valueOf(fieldName) }
                        .getOrDefault(SortField.TITLE)
                        .takeIf { it.appliesTo(tab) } ?: SortField.TITLE
                    val direction = runCatching { SortDirection.valueOf(directionName) }
                        .getOrDefault(SortDirection.ASCENDING)
                    tab to SortOption(field, direction)
                }
            }.collect { (tab, sort) ->
                if (tab != selectedTabFlow.value) return@collect
                sortOptionFlow.value = sort
                _uiState.update { it.copy(sortOption = sort) }
            }
        }
    }

    /**
     * One pipeline for all five lists plus play counts and filter sets. Mapping and sorting
     * thousands of songs runs on a background dispatcher, so scrolling and launch never pay the
     * comparison cost on the main thread.
     */
    private fun observeLibraryData() {
        viewModelScope.launch {
            val libraryFlow = combine(
                trackRepository.getAllTracks(),
                trackRepository.getAlbums(),
                trackRepository.getArtists(),
                trackRepository.getGenres(),
                trackRepository.getFolders()
            ) { tracks, albums, artists, genres, folders ->
                SortedLibrary(tracks, albums, artists, genres, folders)
            }
            val extrasFlow = combine(
                trackRepository.getPlayStats(),
                trackRepository.getFavoriteTrackIds(),
                trackRepository.getDownloadedTrackUris()
            ) { stats, favorites, downloads -> Triple(stats, favorites, downloads) }

            combine(libraryFlow, extrasFlow) { library, extras ->
                library.copy(
                    playStats = extras.first,
                    favorites = extras.second,
                    downloadedUris = extras.third
                )
            }.flatMapLatest { library ->
                combine(sortOptionFlow, trackFilterFlow) { sort, filter ->
                    sortAndFilter(library, sort, filter)
                }
            }
                .flowOn(Dispatchers.Default)
                .collect { sorted ->
                    _uiState.update {
                        it.copy(
                            tracks = sorted.tracks,
                            visibleTracks = sorted.visibleTracks,
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

    private fun sortAndFilter(
        library: SortedLibrary,
        sort: SortOption,
        filter: TrackFilter
    ): SortedLibrary {
        val asc = sort.direction == SortDirection.ASCENDING
        val text = String.CASE_INSENSITIVE_ORDER
        val random = Random(randomSeed)

        fun <T> List<T>.orderedBy(comparator: Comparator<T>): List<T> =
            if (asc) sortedWith(comparator) else sortedWith(comparator.reversed())

        val tracks = when (sort.field) {
            SortField.TITLE -> library.tracks.orderedBy(compareBy(text) { it.title })
            SortField.ARTIST -> library.tracks.orderedBy(compareBy(text) { it.artist })
            SortField.ALBUM -> library.tracks.orderedBy(compareBy(text) { it.album })
            SortField.DATE_ADDED -> library.tracks.orderedBy(compareBy { it.dateAdded })
            SortField.DATE_MODIFIED -> library.tracks.orderedBy(compareBy { it.dateModified })
            SortField.DURATION -> library.tracks.orderedBy(compareBy { it.durationMs })
            SortField.YEAR -> library.tracks.orderedBy(compareBy { it.year })
            SortField.PLAY_COUNT -> library.tracks.orderedBy(
                compareBy<Track> { library.playStats[it.id]?.playCount ?: 0 }
                    .thenBy(text) { it.title }
            )
            SortField.LAST_PLAYED -> library.tracks.orderedBy(
                compareBy<Track> { library.playStats[it.id]?.lastPlayedAt ?: 0L }
                    .thenBy(text) { it.title }
            )
            SortField.RANDOM -> library.tracks.shuffled(random)
            SortField.TRACK_COUNT -> library.tracks.orderedBy(compareBy(text) { it.title })
        }

        val albums = when (sort.field) {
            SortField.ARTIST -> library.albums.orderedBy(
                compareBy<Album, String>(text) { it.artist }.thenBy(text) { it.name }
            )
            SortField.YEAR -> library.albums.orderedBy(
                compareBy<Album> { it.year }.thenBy(text) { it.name }
            )
            SortField.TRACK_COUNT -> library.albums.orderedBy(
                compareBy<Album> { it.trackCount }.thenBy(text) { it.name }
            )
            SortField.RANDOM -> library.albums.shuffled(random)
            else -> library.albums.orderedBy(compareBy(text) { it.name })
        }

        val artists = when (sort.field) {
            SortField.TRACK_COUNT -> library.artists.orderedBy(
                compareBy<Artist> { it.trackCount }.thenBy(text) { it.name }
            )
            SortField.RANDOM -> library.artists.shuffled(random)
            else -> library.artists.orderedBy(compareBy(text) { it.name })
        }

        val visibleTracks = filterTracks(tracks, filter, library)
        return library.copy(
            tracks = tracks,
            visibleTracks = visibleTracks,
            albums = albums,
            artists = artists,
            genres = library.genres.sortedWith(compareBy(text) { it.name }),
            folders = library.folders.sortedWith(compareBy(text) { it.name })
        )
    }

    private fun filterTracks(
        tracks: List<Track>,
        filter: TrackFilter,
        library: SortedLibrary
    ): List<Track> = when (filter) {
        TrackFilter.ALL -> tracks
        TrackFilter.FAVORITES -> tracks.filter { it.id in library.favorites }
        TrackFilter.DOWNLOADED -> tracks.filter { it.uri in library.downloadedUris }
        TrackFilter.RECENTLY_ADDED -> {
            val cutoffSeconds = System.currentTimeMillis() / 1000 - RECENTLY_ADDED_WINDOW_SECONDS
            tracks.filter { track ->
                val stamp = track.dateAdded.takeIf { it > 0 } ?: track.dateModified
                stamp >= cutoffSeconds
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        selectedTabFlow.value = tab
    }

    fun setTrackFilter(filter: TrackFilter) {
        trackFilterFlow.value = filter
        _uiState.update { it.copy(trackFilter = filter) }
    }

    fun setSortOption(sortOption: SortOption, reshuffle: Boolean = false) {
        if (sortOption.field == SortField.RANDOM &&
            (reshuffle || _uiState.value.sortOption.field != SortField.RANDOM)
        ) {
            randomSeed = System.nanoTime()
        }
        val tab = _uiState.value.selectedTab
        val normalized = if (sortOption.field.appliesTo(tab)) sortOption
        else sortOption.copy(field = SortField.TITLE)
        _uiState.update { it.copy(sortOption = normalized) }
        sortOptionFlow.value = normalized
        viewModelScope.launch {
            preferencesManager.setLibrarySortOrder(
                tab.name,
                normalized.field.name,
                normalized.direction.name
            )
        }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.visibleTracks
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.play(tracks, index)
    }

    /** Plays the currently filtered, sorted library in order, from the top. */
    fun playAll() {
        val tracks = _uiState.value.visibleTracks
        if (tracks.isEmpty()) return
        playerController.setShuffleEnabled(false)
        playerController.play(tracks, 0)
    }

    /** Shuffles the currently filtered library, starting from a random song. */
    fun shuffleAll() {
        val tracks = _uiState.value.visibleTracks
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

    companion object {
        /** "Recently added" means the last 30 days, in MediaStore's seconds-since-epoch stamps. */
        private const val RECENTLY_ADDED_WINDOW_SECONDS = 30L * 24 * 60 * 60
    }
}
