package com.yasinjahantigh.logarhythm.feature.download

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.download.DownloadCookies
import com.yasinjahantigh.logarhythm.data.download.DownloadEvents
import com.yasinjahantigh.logarhythm.data.download.DownloadSavedEvent
import com.yasinjahantigh.logarhythm.data.download.DownloadSearchService
import com.yasinjahantigh.logarhythm.data.download.YtDlpRuntime
import com.yasinjahantigh.logarhythm.data.download.YtdlpUpdateState
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val searchService: DownloadSearchService,
    private val repository: DownloadRepository,
    private val itunesClient: com.yasinjahantigh.logarhythm.data.download.metadata.ItunesClient,
    private val notifications: com.yasinjahantigh.logarhythm.data.download.DownloadNotifications,
    private val prefs: PreferencesManager,
    private val runtime: YtDlpRuntime,
    private val cookies: DownloadCookies,
    private val playerController: PlayerController,
    private val events: DownloadEvents
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchState = MutableStateFlow(DownloadSearchUiState())
    val searchState: StateFlow<DownloadSearchUiState> = _searchState.asStateFlow()

    private val _preparing = MutableStateFlow(true)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _sourceFilter = MutableStateFlow<DownloadSource?>(null)
    val sourceFilter: StateFlow<DownloadSource?> = _sourceFilter.asStateFlow()

    val history: StateFlow<List<DownloadItem>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches: StateFlow<List<String>> = prefs.downloadRecentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val updateState: StateFlow<YtdlpUpdateState> = runtime.updateState
    val ytdlpVersion: StateFlow<String?> = runtime.version
    val savedEvents: SharedFlow<DownloadSavedEvent> = events.saved
    val cookiesImported: StateFlow<Boolean> = cookies.imported

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

    fun search(term: String = _query.value) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        _query.value = trimmed
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            prefs.addDownloadRecentSearch(trimmed)
            _searchState.value = DownloadSearchUiState(searching = true, hasSearched = true)

            val outcome = try {
                searchService.search(trimmed, _sourceFilter.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            _searchState.value = if (outcome == null) {
                DownloadSearchUiState(
                    searching = false,
                    error = DownloadErrorKind.UNKNOWN,
                    hasSearched = true
                )
            } else {
                DownloadSearchUiState(
                    searching = false,
                    results = outcome.results,
                    error = if (outcome.results.isEmpty()) outcome.failure else null,
                    hasSearched = true
                )
            }
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
                    runCatching { repository.enqueue(best) }.onSuccess { queued++ }.onFailure { failed++ }
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
