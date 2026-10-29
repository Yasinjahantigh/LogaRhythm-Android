package com.yasinjahantigh.logarhythm.feature.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.ai.AiProvider
import com.yasinjahantigh.logarhythm.data.ai.AiSettings
import com.yasinjahantigh.logarhythm.data.analytics.InstallReporter
import com.yasinjahantigh.logarhythm.data.backup.BackupManager
import com.yasinjahantigh.logarhythm.data.backup.PlaylistFileService
import com.yasinjahantigh.logarhythm.data.update.AppRelease
import com.yasinjahantigh.logarhythm.data.update.UpdateManager
import com.yasinjahantigh.logarhythm.data.update.UpdateState
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceTestResult
import com.yasinjahantigh.logarhythm.domain.model.AiFailure
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.AiPlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.LyricsRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the "Test connection" button inside the AI settings dialog. */
data class AiTestState(
    val testing: Boolean = false,
    val success: Boolean = false,
    val failure: AiFailure? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val trackRepository: TrackRepository,
    private val lyricsRepository: LyricsRepository,
    private val aiPlaylistRepository: AiPlaylistRepository,
    private val backupManager: BackupManager,
    private val playlistFileService: PlaylistFileService,
    private val updateManager: UpdateManager,
    private val installReporter: InstallReporter
) : ViewModel() {

    /** One-shot result text for a toast; the screen calls [consumeMessage] after showing it. */
    data class Message(@StringRes val res: Int, val args: List<Any> = emptyList())

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    val fadeSec: StateFlow<Int> = preferencesManager.fadeBetweenSongsSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val skipSilence: StateFlow<Boolean> = preferencesManager.skipSilence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val replayGainMode: StateFlow<String> = preferencesManager.replayGainMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")
    val replayGainPreampDb: StateFlow<Float> = preferencesManager.replayGainPreampDb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    val resumeOnHeadset: StateFlow<Boolean> = preferencesManager.resumeOnHeadset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val splitArtists: StateFlow<Boolean> = preferencesManager.splitArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val artistSeparators: StateFlow<List<String>> = preferencesManager.artistSeparators
        .map { ArtistSplitter.parseSeparators(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistSplitter.DEFAULT_SEPARATORS)
    val artistNeverSplit: StateFlow<Set<String>> = preferencesManager.artistNeverSplit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Songs whose lyrics are hidden, so the user can bring them back from one place. */
    val hiddenLyricsTracks: StateFlow<List<Track>> =
        combine(lyricsRepository.getHiddenTrackIds(), trackRepository.getAllTracks()) { ids, tracks ->
            val wanted = ids.toHashSet()
            tracks.filter { it.id in wanted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiSettings: StateFlow<AiSettings> = aiPlaylistRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettings())

    val installedVersion: String = updateManager.installedVersion
    val updateState: StateFlow<UpdateState> = updateManager.state
    val latestRelease: StateFlow<AppRelease?> = updateManager.lastRelease
    val autoCheckEnabled: StateFlow<Boolean> = updateManager.autoCheckEnabled
    val installStatsAvailable: Boolean = installReporter.isConfigured

    fun checkForUpdates() = updateManager.check(silent = false)
    fun setAutoCheckEnabled(enabled: Boolean) = updateManager.setAutoCheckEnabled(enabled)

    private val _aiTestState = MutableStateFlow(AiTestState())
    val aiTestState: StateFlow<AiTestState> = _aiTestState.asStateFlow()

    fun saveAiSettings(
        provider: AiProvider,
        baseUrl: String,
        model: String,
        apiKey: String?,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            aiPlaylistRepository.saveSettings(provider, baseUrl, model, apiKey)
            _aiTestState.value = AiTestState()
            onSaved()
        }
    }

    fun clearAiKey() {
        viewModelScope.launch { aiPlaylistRepository.clearKey() }
    }

    fun testAiConnection(provider: AiProvider, baseUrl: String, model: String, apiKey: String?) {
        if (_aiTestState.value.testing) return
        viewModelScope.launch {
            _aiTestState.value = AiTestState(testing = true)
            val failure = aiPlaylistRepository.testConnection(provider, baseUrl, model, apiKey)
            _aiTestState.value = AiTestState(success = failure == null, failure = failure)
        }
    }

    fun resetAiTestState() {
        _aiTestState.value = AiTestState()
    }

    fun setFadeSec(seconds: Int) { viewModelScope.launch { preferencesManager.setFadeBetweenSongsSec(seconds) } }
    fun setSkipSilence(enabled: Boolean) { viewModelScope.launch { preferencesManager.setSkipSilence(enabled) } }
    fun setReplayGainMode(mode: String) { viewModelScope.launch { preferencesManager.setReplayGainMode(mode) } }
    fun setReplayGainPreampDb(db: Float) { viewModelScope.launch { preferencesManager.setReplayGainPreampDb(db) } }
    fun setResumeOnHeadset(enabled: Boolean) { viewModelScope.launch { preferencesManager.setResumeOnHeadset(enabled) } }
    fun setSplitArtists(enabled: Boolean) { viewModelScope.launch { preferencesManager.setSplitArtists(enabled) } }
    fun setArtistSeparators(raw: String?) {
        viewModelScope.launch {
            preferencesManager.setArtistSeparators(raw?.takeIf { it.isNotBlank() })
        }
    }

    fun setArtistNeverSplit(names: Set<String>) {
        viewModelScope.launch { preferencesManager.setArtistNeverSplit(names) }
    }

    fun restoreLyrics(trackId: String) {
        viewModelScope.launch { lyricsRepository.resetLyricsToDefault(trackId) }
    }

    fun backupTo(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.export(uri)
            _message.value = result.fold(
                onSuccess = { Message(R.string.backup_saved, listOf(it.playlists, it.playEvents)) },
                onFailure = { Message(R.string.backup_failed) }
            )
        }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.restore(uri)
            _message.value = result.fold(
                onSuccess = { Message(R.string.restore_done, listOf(it.playlists, it.playEvents, it.skippedSongs)) },
                onFailure = { Message(R.string.restore_failed) }
            )
        }
    }

    fun exportPlaylists(treeUri: Uri) {
        viewModelScope.launch {
            val result = playlistFileService.exportAll(treeUri)
            _message.value = result.fold(
                onSuccess = { Message(R.string.m3u_exported, listOf(it)) },
                onFailure = { Message(R.string.m3u_failed) }
            )
        }
    }

    fun importPlaylists(uris: List<Uri>) {
        viewModelScope.launch {
            val result = playlistFileService.importFiles(uris)
            _message.value = result.fold(
                onSuccess = { Message(R.string.m3u_imported, listOf(it.playlists, it.matched, it.missing)) },
                onFailure = { Message(R.string.m3u_failed) }
            )
        }
    }

    val appLanguage: StateFlow<String> = preferencesManager.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val themeMode: StateFlow<String> = preferencesManager.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "system"
        )

    val themeStyle: StateFlow<String> = preferencesManager.themeStyle
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "default"
        )

    val cornerRoundness: StateFlow<String> = preferencesManager.cornerRoundness
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "standard"
        )

    val isCompactMiniPlayer: StateFlow<Boolean> = preferencesManager.isCompactMiniPlayer
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val minTrackLengthSec: StateFlow<Int> = preferencesManager.minTrackLengthSec
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 30
        )

    val excludedFolders: StateFlow<Set<String>> = preferencesManager.excludedFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val showSystemAudio: StateFlow<Boolean> = preferencesManager.showSystemAudio
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val allFolders: StateFlow<List<String>> = trackRepository.getAllDeviceFolderNames()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hiddenTracks: StateFlow<List<Track>> = trackRepository.getHiddenTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isLrclibEnabled: StateFlow<Boolean> = preferencesManager.isLrclibEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val isGeniusEnabled: StateFlow<Boolean> = preferencesManager.isGeniusEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val isNeteaseEnabled: StateFlow<Boolean> = preferencesManager.lyricsSourcePreferences
        .map { it.netease }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isKugouEnabled: StateFlow<Boolean> = preferencesManager.lyricsSourcePreferences
        .map { it.kugou }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isLyricsOvhEnabled: StateFlow<Boolean> = preferencesManager.lyricsSourcePreferences
        .map { it.lyricsOvh }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val isCustomLyricsEnabled: StateFlow<Boolean> = preferencesManager.lyricsSourcePreferences
        .map { it.custom }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val customLyricsUrl: StateFlow<String?> = preferencesManager.lyricsSourcePreferences
        .map { it.customUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sourceTestResults = MutableStateFlow<List<LyricsSourceTestResult>?>(null)
    val sourceTestResults: StateFlow<List<LyricsSourceTestResult>?> = _sourceTestResults.asStateFlow()
    private val _isTestingSources = MutableStateFlow(false)
    val isTestingSources: StateFlow<Boolean> = _isTestingSources.asStateFlow()

    fun setAppLanguage(
        language: String,
        onSaved: () -> Unit = {},
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                preferencesManager.setAppLanguage(language)
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun setCornerRoundness(roundness: String) {
        viewModelScope.launch {
            preferencesManager.setCornerRoundness(roundness)
        }
    }

    fun setCompactMiniPlayer(compact: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCompactMiniPlayer(compact)
        }
    }

    fun setMinTrackLengthSec(seconds: Int) {
        viewModelScope.launch {
            preferencesManager.setMinTrackLengthSec(seconds)
            trackRepository.refreshTracks()
        }
    }

    fun toggleExcludedFolder(folder: String) {
        viewModelScope.launch {
            preferencesManager.toggleExcludedFolder(folder)
            trackRepository.refreshTracks()
        }
    }

    fun setShowSystemAudio(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShowSystemAudio(show)
            trackRepository.refreshTracks()
        }
    }

    fun resetExcludedFoldersToDefault() {
        viewModelScope.launch {
            preferencesManager.resetExcludedFoldersToDefault()
            trackRepository.refreshTracks()
        }
    }

    fun clearAllExcludedFolders() {
        viewModelScope.launch {
            preferencesManager.clearAllExcludedFolders()
            trackRepository.refreshTracks()
        }
    }

    fun unhideTrack(trackId: String) {
        viewModelScope.launch {
            trackRepository.unhideTrack(trackId)
        }
    }

    fun unhideAllTracks() {
        viewModelScope.launch {
            trackRepository.unhideAllTracks()
        }
    }

    fun setLrclibEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setLrclibEnabled(enabled)
        }
    }

    fun setGeniusEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setGeniusEnabled(enabled)
        }
    }

    fun setNeteaseEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNeteaseEnabled(enabled) }
    }

    fun setKugouEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setKugouEnabled(enabled) }
    }

    fun setLyricsOvhEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setLyricsOvhEnabled(enabled) }
    }

    fun setCustomLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setCustomLyricsEnabled(enabled) }
    }

    fun setCustomLyricsUrl(url: String?) {
        viewModelScope.launch { preferencesManager.setCustomLyricsUrl(url) }
    }

    fun testLyricsSources() {
        if (_isTestingSources.value) return
        viewModelScope.launch {
            _isTestingSources.value = true
            _sourceTestResults.value = emptyList()
            try {
                _sourceTestResults.value = lyricsRepository.testLyricsSources()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _sourceTestResults.value = emptyList()
            } finally {
                _isTestingSources.value = false
            }
        }
    }

    fun dismissSourceTestResults() {
        _sourceTestResults.value = null
    }
}
