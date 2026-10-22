package com.yasinjahantigh.logarhythm.feature.download

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.download.DownloadCookies
import com.yasinjahantigh.logarhythm.data.download.DownloadEvents
import com.yasinjahantigh.logarhythm.data.download.DownloadSavedEvent
import com.yasinjahantigh.logarhythm.data.download.YtDlpRuntime
import com.yasinjahantigh.logarhythm.data.download.YtDlpSearchService
import com.yasinjahantigh.logarhythm.data.download.YtdlpUpdateState
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val searchService: YtDlpSearchService,
    private val repository: DownloadRepository,
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

    fun search(term: String = _query.value) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        _query.value = trimmed
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            prefs.addDownloadRecentSearch(trimmed)
            _searchState.value = DownloadSearchUiState(searching = true, hasSearched = true)

            val outcome = try {
                searchService.search(trimmed)
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
