package com.yasinjahantigh.logarhythm.feature.artist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.download.DownloadSearchService
import com.yasinjahantigh.logarhythm.data.download.StreamPreviewPlayer
import com.yasinjahantigh.logarhythm.data.download.StreamPreviewState
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.radar.ArtistFollowRepository
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarService
import com.yasinjahantigh.logarhythm.data.repository.ArtistPhotoCandidate
import com.yasinjahantigh.logarhythm.data.repository.ArtistPhotoRepository
import com.yasinjahantigh.logarhythm.domain.model.ArtistProfile
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.feature.download.DownloadCollectionUiState
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController,
    private val artistFollowRepository: ArtistFollowRepository,
    private val artistPhotoRepository: ArtistPhotoRepository,
    private val downloadSearchService: DownloadSearchService,
    private val musicManClient: MusicManClient,
    private val deezerClient: DeezerClient,
    private val downloadRepository: DownloadRepository,
    private val streamPreviewPlayer: StreamPreviewPlayer,
    private val sourceRouter: com.yasinjahantigh.logarhythm.data.download.DownloadSourceRouter
) : ViewModel() {

    private val rawArtistName: String = savedStateHandle.get<String>("artistName").orEmpty()
    val artistName: String = Uri.decode(rawArtistName)
    private val artistKey: String = artistPhotoRepository.normalizeKey(artistName)

    val artistPhotoUri: StateFlow<String?> = artistPhotoRepository.photos
        .map { it[artistKey] }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = artistPhotoRepository.photoFor(artistName)
        )

    private val _photoCandidates = MutableStateFlow<List<ArtistPhotoCandidate>>(emptyList())
    val photoCandidates: StateFlow<List<ArtistPhotoCandidate>> = _photoCandidates.asStateFlow()

    private val _searchingPhotos = MutableStateFlow(false)
    val searchingPhotos: StateFlow<Boolean> = _searchingPhotos.asStateFlow()

    private var photoSearchJob: Job? = null

    init {
        if (artistName.isNotBlank()) {
            viewModelScope.launch {
                artistPhotoRepository.ensureFetched(artistName, force = false)
            }
        }
    }

    fun searchArtistPhotos(query: String) {
        val term = query.trim().ifBlank { artistName }
        if (term.isBlank()) return
        photoSearchJob?.cancel()
        photoSearchJob = viewModelScope.launch {
            _searchingPhotos.value = true
            _photoCandidates.value = runCatching {
                artistPhotoRepository.searchPhotoCandidates(term)
            }.getOrDefault(emptyList())
            _searchingPhotos.value = false
        }
    }

    fun setArtistPhotoUrl(url: String) {
        viewModelScope.launch {
            artistPhotoRepository.setCustomPhotoUrl(artistName, url)
        }
    }

    fun setArtistPhotoBytes(bytes: ByteArray) {
        viewModelScope.launch {
            artistPhotoRepository.setCustomPhotoBytes(artistName, bytes)
        }
    }

    fun resetArtistPhoto() {
        viewModelScope.launch {
            artistPhotoRepository.resetPhoto(artistName)
        }
    }

    val isFollowed: StateFlow<Boolean> = artistFollowRepository.observeIsFollowed(artistName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleFollow() {
        val art = artistPhotoUri.value ?: profile.value?.tracks?.firstOrNull()?.artworkUri
        viewModelScope.launch {
            artistFollowRepository.toggleFollow(displayName = artistName, artworkUrl = art)
        }
    }

    val profile: StateFlow<ArtistProfile?> = trackRepository.getArtistProfile(artistName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val tracks: StateFlow<List<Track>> = profile
        .map { it?.tracks.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    // --- Full Online Discography & Downloads (paginated, no 50-track ceiling) ---
    private val _onlineTracks = MutableStateFlow<List<DownloadSearchResult>>(emptyList())
    val onlineTracks: StateFlow<List<DownloadSearchResult>> = _onlineTracks.asStateFlow()

    private val _onlineAlbums = MutableStateFlow<List<DownloadSearchResult>>(emptyList())
    val onlineAlbums: StateFlow<List<DownloadSearchResult>> = _onlineAlbums.asStateFlow()

    private val _onlineLoading = MutableStateFlow(false)
    val onlineLoading: StateFlow<Boolean> = _onlineLoading.asStateFlow()

    private val _onlineLoadingMore = MutableStateFlow(false)
    val onlineLoadingMore: StateFlow<Boolean> = _onlineLoadingMore.asStateFlow()

    private val _onlineLoadedOnce = MutableStateFlow(false)
    val onlineLoadedOnce: StateFlow<Boolean> = _onlineLoadedOnce.asStateFlow()

    private val _onlineSuggestVpn = MutableStateFlow(false)
    val onlineSuggestVpn: StateFlow<Boolean> = _onlineSuggestVpn.asStateFlow()

    private val _openedOnlineAlbum = MutableStateFlow<DownloadCollectionUiState?>(null)
    val openedOnlineAlbum: StateFlow<DownloadCollectionUiState?> = _openedOnlineAlbum.asStateFlow()

    val previewState: StateFlow<StreamPreviewState> = streamPreviewPlayer.state

    val downloadHistory: StateFlow<List<DownloadItem>> = downloadRepository.observeHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var onlineJob: Job? = null
    private var albumSheetJob: Job? = null

    fun loadOnlineCatalog(force: Boolean = false) {
        if (artistName.isBlank()) return
        if (!force && (_onlineLoadedOnce.value || _onlineLoading.value)) return
        onlineJob?.cancel()
        onlineJob = viewModelScope.launch {
            _onlineLoading.value = true
            _onlineLoadingMore.value = false
            _onlineSuggestVpn.value = false
            if (force) {
                _onlineTracks.value = emptyList()
                _onlineAlbums.value = emptyList()
            }

            val mergeMutex = Mutex()
            val tracksByKey = LinkedHashMap<String, DownloadSearchResult>()
            val albumsByKey = LinkedHashMap<String, DownloadSearchResult>()

            fun titleKey(raw: String): String =
                ReleaseRadarService.normalizeArtistKey(raw)
                    .replace(Regex("""\s*[\(\[].*?[\)\]]\s*"""), " ")
                    .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                    .trim()

            suspend fun mergeAndPublish(
                newTracks: List<DownloadSearchResult>,
                newAlbums: List<DownloadSearchResult>,
                preferExisting: Boolean = true
            ) {
                mergeMutex.withLock {
                    for (alb in newAlbums) {
                        val key = titleKey(alb.title).ifBlank { "${alb.source.raw}:${alb.id}" }
                        if (!preferExisting || !albumsByKey.containsKey(key)) {
                            albumsByKey[key] = alb
                        }
                    }
                    for (tr in newTracks) {
                        val key = titleKey(tr.title).ifBlank { "${tr.source.raw}:${tr.id}" }
                        val existing = tracksByKey[key]
                        if (existing == null) {
                            tracksByKey[key] = tr
                        } else if (sourceRouter.isPreferredOver(tr.source, existing.source)) {
                            // VPN OFF -> prefer MusicMan; VPN ON -> prefer fastest measured source
                            tracksByKey[key] = tr
                        }
                    }
                    val sortedTracks = tracksByKey.values.sortedByDescending {
                        ReleaseRadarService.isArtistMatch(it.artist, artistName)
                    }
                    val sortedAlbums = albumsByKey.values.sortedByDescending {
                        ReleaseRadarService.isArtistMatch(it.artist, artistName) || it.artist.isBlank()
                    }
                    _onlineTracks.value = sortedTracks
                    _onlineAlbums.value = sortedAlbums
                    if (sortedTracks.isNotEmpty() || sortedAlbums.isNotEmpty()) {
                        _onlineLoading.value = false
                        _onlineLoadingMore.value = true
                    }
                }
            }

            try {
                coroutineScope {
                    // 1. Resolve MusicMan artist ID & fetch full paginated discography (all pages!)
                    val musicManJob = launch(Dispatchers.IO) {
                        val candidates = withTimeoutOrNull(10_000L) {
                            runCatching {
                                musicManClient.search(artistName, DownloadSearchCategory.ARTIST, limit = 10)
                            }.getOrDefault(emptyList())
                        }.orEmpty()
                        val bestArtist = candidates.firstOrNull {
                            ReleaseRadarService.isArtistMatch(it.title, artistName)
                        } ?: candidates.firstOrNull()
                        val artistId = bestArtist?.artistId ?: bestArtist?.id
                        if (!artistId.isNullOrBlank()) {
                            runCatching {
                                musicManClient.fetchFullArtistCatalog(
                                    artistId = artistId,
                                    pageSize = 50,
                                    maxPages = 20,
                                    onProgress = { partial ->
                                        mergeAndPublish(partial.tracks, partial.albums, preferExisting = false)
                                    }
                                )
                            }
                        }
                    }

                    // 2. Resolve Deezer artist ID & fetch full paginated albums + expanded album tracks
                    val deezerJob = launch(Dispatchers.IO) {
                        val candidates = withTimeoutOrNull(10_000L) {
                            runCatching { deezerClient.searchArtists(artistName, limit = 10) }
                                .getOrDefault(emptyList())
                        }.orEmpty()
                        val bestArtist = candidates.firstOrNull {
                            ReleaseRadarService.isArtistMatch(it.title, artistName)
                        } ?: candidates.firstOrNull()
                        val artistId = bestArtist?.artistId ?: bestArtist?.id
                        if (!artistId.isNullOrBlank()) {
                            runCatching {
                                deezerClient.fetchArtistCatalog(
                                    artistId = artistId,
                                    onProgress = { albums, tracks ->
                                        mergeAndPublish(tracks, albums, preferExisting = true)
                                    }
                                )
                            }
                        }
                    }

                    // 3. Also run multi-source search (captures RadioJavan / Persian exclusives & extras)
                    val searchJob = launch(Dispatchers.IO) {
                        runCatching {
                            downloadSearchService.searchFlow(
                                query = artistName,
                                source = null,
                                category = DownloadSearchCategory.ALL
                            ).collect { outcome ->
                                val matchedTracks = outcome.results.filter {
                                    it.kind == DownloadResultKind.TRACK &&
                                        ReleaseRadarService.isArtistMatch(it.artist, artistName)
                                }
                                val matchedAlbums = outcome.results.filter {
                                    it.kind == DownloadResultKind.ALBUM &&
                                        ReleaseRadarService.isArtistMatch(it.artist, artistName)
                                }
                                if (matchedTracks.isNotEmpty() || matchedAlbums.isNotEmpty()) {
                                    mergeAndPublish(matchedTracks, matchedAlbums, preferExisting = true)
                                }
                            }
                        }
                    }

                    musicManJob.join()
                    deezerJob.join()
                    searchJob.join()
                }

                _onlineLoadedOnce.value = true
                _onlineLoading.value = false
                _onlineLoadingMore.value = false
                _onlineSuggestVpn.value = _onlineTracks.value.isEmpty() && _onlineAlbums.value.isEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _onlineLoadedOnce.value = true
                _onlineLoading.value = false
                _onlineLoadingMore.value = false
                _onlineSuggestVpn.value = _onlineTracks.value.isEmpty() && _onlineAlbums.value.isEmpty()
            }
        }
    }

    fun openOnlineAlbum(album: DownloadSearchResult, forceRefresh: Boolean = false) {
        albumSheetJob?.cancel()
        albumSheetJob = viewModelScope.launch {
            _openedOnlineAlbum.value = DownloadCollectionUiState(
                header = album,
                loading = true
            )
            try {
                val detail = when (album.source) {
                    DownloadSource.MUSICMAN -> musicManClient.lookupAlbum(album.collectionId ?: album.id)
                    DownloadSource.DEEZER -> {
                        val (header, tracks) = deezerClient.lookupAlbumTracks(album.collectionId ?: album.id)
                        MusicManClient.CollectionDetail(header ?: album, emptyList(), tracks)
                    }
                    else -> MusicManClient.CollectionDetail(album, emptyList(), emptyList())
                }
                _openedOnlineAlbum.value = DownloadCollectionUiState(
                    header = detail.header ?: album,
                    albums = detail.albums,
                    tracks = detail.tracks,
                    loading = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _openedOnlineAlbum.value = DownloadCollectionUiState(
                    header = album,
                    loading = false,
                    error = "Could not load album tracks."
                )
            }
        }
    }

    fun closeOnlineAlbum() {
        albumSheetJob?.cancel()
        _openedOnlineAlbum.value = null
    }

    fun previewKeyFor(result: DownloadSearchResult): String = streamPreviewPlayer.keyFor(result)

    fun togglePreview(result: DownloadSearchResult) {
        streamPreviewPlayer.toggle(result)
    }

    fun downloadOnlineTrack(result: DownloadSearchResult, onQueued: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                downloadRepository.enqueue(
                    result.copy(
                        albumArtist = result.albumArtist.ifBlank { artistName }
                    )
                )
            }.onSuccess {
                onQueued()
            }
        }
    }

    fun downloadOnlineBatch(results: List<DownloadSearchResult>, onQueued: (Int) -> Unit = {}) {
        if (results.isEmpty()) return
        viewModelScope.launch {
            val normalized = results.map { it.copy(albumArtist = it.albumArtist.ifBlank { artistName }) }
            val count = runCatching { downloadRepository.enqueueBatch(normalized) }.getOrDefault(0)
            onQueued(count)
        }
    }

    override fun onCleared() {
        streamPreviewPlayer.stop()
        super.onCleared()
    }

    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun playAll() {
        val list = tracks.value
        if (list.isNotEmpty()) {
            playerController.play(list, 0)
        }
    }

    fun shuffleAll() {
        val list = tracks.value
        if (list.isNotEmpty()) {
            playerController.play(list.shuffled(), 0)
        }
    }

    fun playTrack(track: Track) {
        val list = tracks.value
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playerController.play(list, index)
    }

    fun playTopTracks() {
        val top = profile.value?.topTracks.orEmpty()
        if (top.isNotEmpty()) playerController.play(top, 0)
    }

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            if (currentTrack.value?.id == track.id) {
                playerController.next()
            }
            trackRepository.hideTrack(track.id)
        }
    }

    fun deleteTrack(track: Track, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (currentTrack.value?.id == track.id) {
                playerController.next()
            }
            val deleted = try {
                trackRepository.deleteTrack(track)
            } catch (_: Exception) {
                false
            }
            onDone(deleted)
        }
    }

    fun deleteTracks(tracks: List<Track>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val currentId = currentTrack.value?.id
            if (tracks.any { it.id == currentId }) {
                playerController.next()
            }
            val count = try {
                trackRepository.deleteTracks(tracks)
            } catch (_: Exception) {
                0
            }
            onDone(count)
        }
    }
}
