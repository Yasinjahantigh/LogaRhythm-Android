package com.example.musicplayer.feature.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.R
import com.example.musicplayer.data.backup.BackupManager
import com.example.musicplayer.data.backup.PlaylistFileService
import com.example.musicplayer.domain.library.ArtistSplitter
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.LyricsRepository
import com.example.musicplayer.domain.repository.TrackRepository
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val trackRepository: TrackRepository,
    private val lyricsRepository: LyricsRepository,
    private val backupManager: BackupManager,
    private val playlistFileService: PlaylistFileService
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
    val resumeOnHeadset: StateFlow<Boolean> = preferencesManager.resumeOnHeadset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val splitArtists: StateFlow<Boolean> = preferencesManager.splitArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val artistSeparators: StateFlow<List<String>> = preferencesManager.artistSeparators
        .map { ArtistSplitter.parseSeparators(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ArtistSplitter.DEFAULT_SEPARATORS)

    /** Songs whose lyrics are hidden, so the user can bring them back from one place. */
    val hiddenLyricsTracks: StateFlow<List<Track>> =
        combine(lyricsRepository.getHiddenTrackIds(), trackRepository.getAllTracks()) { ids, tracks ->
            val wanted = ids.toHashSet()
            tracks.filter { it.id in wanted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFadeSec(seconds: Int) { viewModelScope.launch { preferencesManager.setFadeBetweenSongsSec(seconds) } }
    fun setSkipSilence(enabled: Boolean) { viewModelScope.launch { preferencesManager.setSkipSilence(enabled) } }
    fun setReplayGainMode(mode: String) { viewModelScope.launch { preferencesManager.setReplayGainMode(mode) } }
    fun setResumeOnHeadset(enabled: Boolean) { viewModelScope.launch { preferencesManager.setResumeOnHeadset(enabled) } }
    fun setSplitArtists(enabled: Boolean) { viewModelScope.launch { preferencesManager.setSplitArtists(enabled) } }
    fun setArtistSeparators(raw: String?) {
        viewModelScope.launch {
            preferencesManager.setArtistSeparators(raw?.takeIf { it.isNotBlank() })
        }
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

    val colorSource: StateFlow<String> = preferencesManager.colorSource
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "fixed_color"
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

    val allFolders: StateFlow<List<String>> = trackRepository.getFolders()
        .map { folders -> folders.map { it.name }.filter { it.isNotBlank() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isLrclibEnabled: StateFlow<Boolean> = preferencesManager.isLrclibEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

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

    fun setColorSource(source: String) {
        viewModelScope.launch {
            preferencesManager.setColorSource(source)
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

    fun setLrclibEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setLrclibEnabled(enabled)
        }
    }
}
