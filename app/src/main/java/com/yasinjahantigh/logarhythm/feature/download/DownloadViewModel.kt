package com.yasinjahantigh.logarhythm.feature.download

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.download.DownloadCatalogCache
import com.yasinjahantigh.logarhythm.data.download.DownloadCookies
import com.yasinjahantigh.logarhythm.data.download.DownloadEvents
import com.yasinjahantigh.logarhythm.data.download.DownloadSavedEvent
import com.yasinjahantigh.logarhythm.data.download.DownloadSearchService
import com.yasinjahantigh.logarhythm.data.download.ResolvedShareAction
import com.yasinjahantigh.logarhythm.data.download.ShareLinkResolver
import com.yasinjahantigh.logarhythm.data.download.StreamPreviewPlayer
import com.yasinjahantigh.logarhythm.data.download.StreamPreviewState
import com.yasinjahantigh.logarhythm.data.download.YtDlpRuntime
import com.yasinjahantigh.logarhythm.data.download.YtdlpUpdateState
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanClient
import com.yasinjahantigh.logarhythm.data.download.saavn.SaavnClient
import com.yasinjahantigh.logarhythm.data.radar.ArtistFollowRepository
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarService
import com.yasinjahantigh.logarhythm.domain.download.ArtistMerger
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val searchService: DownloadSearchService,
    private val repository: DownloadRepository,
    private val itunesClient: com.yasinjahantigh.logarhythm.data.download.metadata.ItunesClient,
    private val musicManClient: MusicManClient,
    private val deezerClient: DeezerClient,
    private val radioJavanClient: RadioJavanClient,
    private val saavnClient: SaavnClient,
    private val catalogCache: DownloadCatalogCache,
    private val notifications: com.yasinjahantigh.logarhythm.data.download.DownloadNotifications,
    private val prefs: PreferencesManager,
    private val runtime: YtDlpRuntime,
    private val cookies: DownloadCookies,
    private val playerController: PlayerController,
    private val events: DownloadEvents,
    private val artistFollowRepository: ArtistFollowRepository,
    private val streamPreviewPlayer: StreamPreviewPlayer,
    private val shareLinkResolver: ShareLinkResolver,
    private val sourceRouter: com.yasinjahantigh.logarhythm.data.download.DownloadSourceRouter,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchState = MutableStateFlow(DownloadSearchUiState())
    val searchState: StateFlow<DownloadSearchUiState> = _searchState.asStateFlow()

    private val _preparing = MutableStateFlow(true)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _sourceFilter = MutableStateFlow<DownloadSource?>(null)
    val sourceFilter: StateFlow<DownloadSource?> = _sourceFilter.asStateFlow()

    private val _categoryFilter = MutableStateFlow(DownloadSearchCategory.ALL)
    val categoryFilter: StateFlow<DownloadSearchCategory> = _categoryFilter.asStateFlow()

    private val _collectionDetail = MutableStateFlow<DownloadCollectionUiState?>(null)
    val collectionDetail: StateFlow<DownloadCollectionUiState?> = _collectionDetail.asStateFlow()

    val savedCollections: StateFlow<List<DownloadCatalogCache.SavedCollectionSummary>> =
        catalogCache.observeSavedCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<DownloadItem>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches: StateFlow<List<String>> = prefs.downloadRecentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val updateState: StateFlow<YtdlpUpdateState> = runtime.updateState
    val ytdlpVersion: StateFlow<String?> = runtime.version
    val savedEvents: SharedFlow<DownloadSavedEvent> = events.saved
    val cookiesImported: StateFlow<Boolean> = cookies.imported
    val previewState: StateFlow<StreamPreviewState> = streamPreviewPlayer.state
    val pendingShareText: StateFlow<String?> = shareLinkResolver.pendingShareText
    val routingStatus: StateFlow<com.yasinjahantigh.logarhythm.data.download.SourceRoutingStatus> =
        sourceRouter.status

    val downloadQualityPref: StateFlow<String> = prefs.downloadQualityPref
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "best")

    private val _freshResults = MutableStateFlow<List<DownloadSearchResult>>(emptyList())
    val freshResults: StateFlow<List<DownloadSearchResult>> = _freshResults.asStateFlow()

    private val _popularResults = MutableStateFlow<List<DownloadSearchResult>>(emptyList())
    val popularResults: StateFlow<List<DownloadSearchResult>> = _popularResults.asStateFlow()

    val followedArtistKeys: StateFlow<Set<String>> = artistFollowRepository.observeFollowed()
        .map { list -> list.map { it.artistKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun isArtistFollowed(artistName: String, keys: Set<String>): Boolean =
        ReleaseRadarService.normalizeArtistKey(artistName) in keys

    fun toggleFollowCollectionArtist(header: DownloadSearchResult) {
        viewModelScope.launch {
            artistFollowRepository.toggleFollow(
                displayName = header.title,
                musicManArtistId = if (header.source == DownloadSource.MUSICMAN) header.artistId ?: header.id else null,
                artworkUrl = header.thumbnailUrl
            )
        }
    }

    private val _cookieImport = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val cookieImport: SharedFlow<Boolean> = _cookieImport

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _sourceFilter.value = prefs.downloadSourceFilter.first()
        }
        viewModelScope.launch {
            try {
                runtime.ensureReady()
                runtime.currentVersion()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Failures are reported when a search or download actually needs the engine.
            } finally {
                _preparing.value = false
            }
        }
        viewModelScope.launch {
            sourceRouter.ensureFreshRanking(force = false)
        }
        viewModelScope.launch {
            runCatching { musicManClient.fetchFresh(16) }.getOrNull()?.let { _freshResults.value = it }
            runCatching { musicManClient.fetchPopular(16) }.getOrNull()?.let { _popularResults.value = it }
        }
        val initialQuery = savedStateHandle.get<String>("query")?.trim().orEmpty()
        if (initialQuery.isNotBlank()) {
            _query.value = initialQuery
            search(initialQuery)
        }
    }

    override fun onCleared() {
        streamPreviewPlayer.stop()
        super.onCleared()
    }

    fun setDownloadQuality(prefValue: String) {
        viewModelScope.launch { prefs.setDownloadQualityPref(prefValue) }
    }

    fun togglePreview(result: DownloadSearchResult) {
        streamPreviewPlayer.toggle(result)
    }

    fun isPreviewKey(result: DownloadSearchResult): String =
        streamPreviewPlayer.keyFor(result)

    fun consumeSharedLink(onDirectEnqueued: (DownloadSearchResult) -> Unit) {
        val text = shareLinkResolver.consumePending() ?: return
        viewModelScope.launch {
            when (val resolved = shareLinkResolver.resolve(text)) {
                is ResolvedShareAction.EnqueueDirect -> {
                    repository.enqueue(resolved.result)
                    onDirectEnqueued(resolved.result)
                }
                is ResolvedShareAction.SearchQuery -> {
                    _sourceFilter.value = resolved.sourceFilter
                    _query.value = resolved.query
                    search(resolved.query)
                }
                null -> Unit
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun setSourceFilter(source: DownloadSource?) {
        if (_sourceFilter.value == source) return
        _sourceFilter.value = source
        viewModelScope.launch { prefs.setDownloadSourceFilter(source) }
        if (_searchState.value.hasSearched && _query.value.isNotBlank()) search(_query.value)
    }

    fun setCategoryFilter(category: DownloadSearchCategory) {
        if (_categoryFilter.value == category) return
        _categoryFilter.value = category
        if (_searchState.value.hasSearched && _query.value.isNotBlank()) search(_query.value)
    }

    fun search(term: String = _query.value) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        val category = _categoryFilter.value
        val source = _sourceFilter.value
        _query.value = trimmed
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            prefs.addDownloadRecentSearch(trimmed)
            val cached = runCatching { catalogCache.getSearch(trimmed, category, source) }.getOrNull().orEmpty()
            _searchState.value = DownloadSearchUiState(
                searching = true,
                results = cached,
                hasSearched = true,
                category = category
            )

            try {
                searchService.searchFlow(trimmed, source, category).collect { outcome ->
                    val merged = if (outcome.results.isNotEmpty()) outcome.results else cached
                    _searchState.value = DownloadSearchUiState(
                        searching = !outcome.isFinal,
                        results = merged,
                        error = if (outcome.isFinal && merged.isEmpty()) outcome.failure else null,
                        hasSearched = true,
                        category = category
                    )
                    if (outcome.isFinal && outcome.results.isNotEmpty()) {
                        catalogCache.saveSearch(trimmed, category, source, outcome.results)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _searchState.value = DownloadSearchUiState(
                    searching = false,
                    results = cached,
                    error = if (cached.isEmpty()) DownloadErrorKind.UNKNOWN else null,
                    hasSearched = true,
                    category = category
                )
            }
        }
    }

    private var collectionJob: Job? = null

    fun openCollection(item: DownloadSearchResult, forceRefresh: Boolean = false) {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            val cached = runCatching { catalogCache.getCollection(item) }.getOrNull()
            // Older caches for artists were capped at 50 tracks; auto-expand them while showing cached items immediately.
            val cachedLooksTruncated = cached != null &&
                item.kind == DownloadResultKind.ARTIST &&
                cached.tracks.size <= 50

            if (cached != null && !forceRefresh && !cachedLooksTruncated) {
                _collectionDetail.value = DownloadCollectionUiState(
                    header = cached.header,
                    albums = cached.albums,
                    tracks = cached.tracks,
                    loading = false,
                    loadingMore = false,
                    fromCache = true
                )
                return@launch
            }

            _collectionDetail.value = DownloadCollectionUiState(
                header = cached?.header ?: item,
                albums = cached?.albums.orEmpty(),
                tracks = cached?.tracks.orEmpty(),
                loading = cached == null,
                loadingMore = cached != null,
                fromCache = cached != null
            )

            try {
                if (item.kind == DownloadResultKind.ARTIST) {
                    val sourcesToQuery = (item.matchedSources + item.sourceArtistIds.keys + item.source).toSet()
                    val mutex = Mutex()
                    val combinedAlbums = LinkedHashMap<String, DownloadSearchResult>()
                    val combinedTracks = LinkedHashMap<String, DownloadSearchResult>()

                    cached?.albums?.forEach { a -> combinedAlbums[ArtistMerger.normalizeRawString(a.title)] = a }
                    cached?.tracks?.forEach { t -> combinedTracks[ArtistMerger.normalizeRawString(t.title)] = t }

                    fun mergeAlbums(newAlbums: List<DownloadSearchResult>) {
                        for (a in newAlbums) {
                            val key = ArtistMerger.normalizeRawString(a.title)
                            if (key.isNotBlank()) {
                                combinedAlbums.putIfAbsent(key, a)
                            }
                        }
                    }

                    fun mergeTracks(newTracks: List<DownloadSearchResult>) {
                        for (t in newTracks) {
                            val key = ArtistMerger.normalizeRawString(t.title)
                            if (key.isNotBlank()) {
                                val existing = combinedTracks[key]
                                if (existing == null) {
                                    combinedTracks[key] = t
                                } else if (!existing.hasReadyAudio && t.hasReadyAudio) {
                                    combinedTracks[key] = t
                                }
                            }
                        }
                    }

                    suspend fun emitState(loadingMore: Boolean = true) {
                        val albumList = combinedAlbums.values.toList()
                        val trackList = combinedTracks.values.toList()
                        _collectionDetail.value = DownloadCollectionUiState(
                            header = item,
                            albums = albumList,
                            tracks = trackList,
                            loading = false,
                            loadingMore = loadingMore,
                            fromCache = false
                        )
                    }

                    coroutineScope {
                        if (sourcesToQuery.contains(DownloadSource.MUSICMAN)) {
                            launch(Dispatchers.IO) {
                                try {
                                    val mmId = item.sourceArtistIds[DownloadSource.MUSICMAN]
                                        ?: (if (item.source == DownloadSource.MUSICMAN) (item.artistId ?: item.id) else null)
                                    val mmArtistId = mmId ?: run {
                                        val searchRes = musicManClient.search(item.title, category = DownloadSearchCategory.ARTIST, limit = 3)
                                        searchRes.firstOrNull { ArtistMerger.areArtistsMatching(it.title, item.title) }?.id
                                    }
                                    if (mmArtistId != null) {
                                        musicManClient.fetchFullArtistCatalog(
                                            artistId = mmArtistId,
                                            onProgress = { partial ->
                                                mutex.withLock {
                                                    mergeAlbums(partial.albums)
                                                    mergeTracks(partial.tracks)
                                                }
                                                emitState()
                                            }
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (sourcesToQuery.contains(DownloadSource.DEEZER)) {
                            launch(Dispatchers.IO) {
                                try {
                                    val dzId = item.sourceArtistIds[DownloadSource.DEEZER]
                                        ?: (if (item.source == DownloadSource.DEEZER) (item.artistId ?: item.id) else null)
                                    val dzArtistId = dzId ?: run {
                                        val dzSearch = deezerClient.searchArtists(item.title, limit = 3)
                                        dzSearch.firstOrNull { ArtistMerger.areArtistsMatching(it.title, item.title) }?.id
                                    }
                                    if (dzArtistId != null) {
                                        deezerClient.fetchArtistCatalog(
                                            artistId = dzArtistId,
                                            onProgress = { partialAlbums, partialTracks ->
                                                mutex.withLock {
                                                    mergeAlbums(partialAlbums)
                                                    mergeTracks(partialTracks)
                                                }
                                                emitState()
                                            }
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (sourcesToQuery.contains(DownloadSource.RADIOJAVAN) || sourcesToQuery.isEmpty()) {
                            launch(Dispatchers.IO) {
                                try {
                                    val rjId = item.sourceArtistIds[DownloadSource.RADIOJAVAN]
                                        ?: (if (item.source == DownloadSource.RADIOJAVAN) (item.artistId ?: item.id) else null)
                                        ?: item.title
                                    val rjTracks = radioJavanClient.lookupArtistTracks(rjId)
                                    if (rjTracks.isNotEmpty()) {
                                        mutex.withLock {
                                            mergeTracks(rjTracks)
                                        }
                                        emitState()
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        if (sourcesToQuery.contains(DownloadSource.SAAVN)) {
                            launch(Dispatchers.IO) {
                                try {
                                    val saavnTracks = saavnClient.search(item.title, limit = 30)
                                    if (saavnTracks.isNotEmpty()) {
                                        mutex.withLock {
                                            mergeTracks(saavnTracks)
                                        }
                                        emitState()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    val finalAlbums = combinedAlbums.values.toList()
                    val finalTracks = combinedTracks.values.toList()
                    if (finalAlbums.isNotEmpty() || finalTracks.isNotEmpty()) {
                        catalogCache.saveCollection(item, finalAlbums, finalTracks)
                    }
                    _collectionDetail.value = DownloadCollectionUiState(
                        header = item,
                        albums = finalAlbums,
                        tracks = finalTracks,
                        loading = false,
                        loadingMore = false,
                        fromCache = false
                    )
                } else {
                    val detail = when (item.source) {
                        DownloadSource.MUSICMAN -> musicManClient.lookupAlbum(item.collectionId ?: item.id)
                        DownloadSource.DEEZER -> {
                            val (header, tracks) = deezerClient.lookupAlbumTracks(item.collectionId ?: item.id)
                            MusicManClient.CollectionDetail(header ?: item, emptyList(), tracks)
                        }
                        else -> MusicManClient.CollectionDetail(item, emptyList(), emptyList())
                    }
                    val finalHeader = detail.header ?: item
                    if (detail.tracks.isNotEmpty() || detail.albums.isNotEmpty()) {
                        catalogCache.saveCollection(finalHeader, detail.albums, detail.tracks)
                    }
                    _collectionDetail.value = DownloadCollectionUiState(
                        header = finalHeader,
                        albums = detail.albums,
                        tracks = detail.tracks,
                        loading = false,
                        loadingMore = false,
                        fromCache = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (cached != null) {
                    _collectionDetail.value = DownloadCollectionUiState(
                        header = cached.header,
                        albums = cached.albums,
                        tracks = cached.tracks,
                        loading = false,
                        loadingMore = false,
                        fromCache = true
                    )
                } else {
                    _collectionDetail.value = _collectionDetail.value?.copy(
                        loading = false,
                        loadingMore = false,
                        error = "Could not load tracks."
                    )
                }
            }
        }
    }

    fun openSavedCollection(cacheKey: String) {
        viewModelScope.launch {
            val cached = runCatching { catalogCache.getCollection(cacheKey) }.getOrNull() ?: return@launch
            _collectionDetail.value = DownloadCollectionUiState(
                header = cached.header,
                albums = cached.albums,
                tracks = cached.tracks,
                loading = false,
                loadingMore = false,
                fromCache = true
            )
        }
    }

    fun closeCollection() {
        collectionJob?.cancel()
        _collectionDetail.value = null
    }

    fun downloadAllFromCollection(
        collectionTitle: String,
        tracks: List<DownloadSearchResult>
    ) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val queued = repository.enqueueBatch(tracks)
            if (queued > 0) notifications.notifyAlbumQueued(collectionTitle, queued)
            _albumSummary.value = AlbumDownloadSummary(collectionTitle, queued, 0, found = true)
        }
    }

    fun selectRecent(term: String) = search(term)

    fun removeRecent(term: String) {
        viewModelScope.launch { prefs.removeDownloadRecentSearch(term) }
    }

    fun clearRecent() {
        viewModelScope.launch { prefs.clearDownloadRecentSearches() }
    }

    fun enqueue(result: DownloadSearchResult) {
        viewModelScope.launch { repository.enqueue(result) }
    }

    data class AlbumDownloadSummary(val album: String, val queued: Int, val failed: Int, val found: Boolean)
    data class AlbumTrackItem(val title: String, val artist: String)
    data class AlbumPreview(val name: String, val artist: String, val tracks: List<AlbumTrackItem>)

    private val _albumSummary = MutableStateFlow<AlbumDownloadSummary?>(null)
    val albumSummary: StateFlow<AlbumDownloadSummary?> = _albumSummary.asStateFlow()
    private val _albumPreview = MutableStateFlow<AlbumPreview?>(null)
    val albumPreview: StateFlow<AlbumPreview?> = _albumPreview.asStateFlow()
    private val _albumBusy = MutableStateFlow(false)
    val albumBusy: StateFlow<Boolean> = _albumBusy.asStateFlow()

    /** Resolves the album first; nothing is queued until the user confirms the list. */
    fun previewAlbum(result: DownloadSearchResult) {
        if (_albumBusy.value) return
        viewModelScope.launch {
            _albumBusy.value = true
            _albumPreview.value = null
            val album = runCatching { itunesClient.albumForTrack(result.title, result.artist) }.getOrNull()
            if (album == null) {
                _albumSummary.value = AlbumDownloadSummary("", 0, 0, found = false)
            } else {
                _albumPreview.value = AlbumPreview(
                    name = album.name,
                    artist = album.artist,
                    tracks = album.tracks.map { AlbumTrackItem(it.title, it.artist) }
                )
            }
            _albumBusy.value = false
        }
    }

    fun dismissAlbumPreview() {
        if (_albumBusy.value) return
        _albumPreview.value = null
    }

    fun confirmAlbumDownload(indices: Set<Int>) {
        val preview = _albumPreview.value ?: return
        if (_albumBusy.value || indices.isEmpty()) return
        viewModelScope.launch {
            _albumBusy.value = true
            var queued = 0
            var failed = 0
            for (index in indices.sorted()) {
                val track = preview.tracks.getOrNull(index) ?: continue
                val outcome = runCatching {
                    searchService.search("${track.title} ${track.artist}".trim(), _sourceFilter.value)
                }.getOrNull()
                val best = outcome?.results?.firstOrNull()
                if (best == null) {
                    failed++
                } else {
                    val tagged = best.copy(
                        album = preview.name,
                        albumArtist = preview.artist,
                        trackNumber = index + 1
                    )
                    runCatching { repository.enqueue(tagged) }.onSuccess { queued++ }.onFailure { failed++ }
                }
            }
            if (queued > 0) notifications.notifyAlbumQueued(preview.name, queued)
            _albumSummary.value = AlbumDownloadSummary(preview.name, queued, failed, found = true)
            _albumPreview.value = null
            _albumBusy.value = false
        }
    }

    fun clearAlbumSummary() {
        _albumSummary.value = null
    }

    fun cancel(item: DownloadItem) {
        viewModelScope.launch { repository.cancel(item.id) }
    }

    fun retry(item: DownloadItem) {
        viewModelScope.launch { repository.retry(item.id) }
    }

    fun retryAllFailed() {
        viewModelScope.launch { repository.retryAllFailed() }
    }

    fun remove(item: DownloadItem) {
        viewModelScope.launch { repository.remove(item.id) }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }

    fun updateYtdlp() {
        viewModelScope.launch {
            runCatching { runtime.updateAndVerify() }
            runCatching { runtime.currentVersion() }
        }
    }

    fun importCookies(uri: Uri) {
        viewModelScope.launch { _cookieImport.emit(cookies.import(uri)) }
    }

    fun clearCookies() {
        cookies.clear()
    }

    fun play(item: DownloadItem) {
        val uri = item.contentUri ?: return
        val track = Track(
            id = item.mediaStoreId?.let { "local:$it" } ?: "download:${item.id}",
            title = item.title,
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            artworkUri = uri,
            sourceType = SourceType.LOCAL,
            uri = uri,
            folder = "LogaRhythm"
        )
        playerController.play(listOf(track), 0)
    }
}
