package com.example.musicplayer.feature.lyrics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.domain.model.LyricLine
import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.LyricsRepository
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val lyricsRepository: LyricsRepository,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    private val positionMs: StateFlow<Long> = playerController.position

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isManualEditOpen = MutableStateFlow(false)
    val isManualEditOpen: StateFlow<Boolean> = _isManualEditOpen.asStateFlow()

    private val _lyricsCandidates = MutableStateFlow<List<com.example.musicplayer.domain.model.LyricsCandidate>>(emptyList())
    val lyricsCandidates: StateFlow<List<com.example.musicplayer.domain.model.LyricsCandidate>> = _lyricsCandidates.asStateFlow()

    private val _isSearchingCandidates = MutableStateFlow(false)
    val isSearchingCandidates: StateFlow<Boolean> = _isSearchingCandidates.asStateFlow()

    private val _showCandidatePicker = MutableStateFlow(false)
    val showCandidatePicker: StateFlow<Boolean> = _showCandidatePicker.asStateFlow()

    private var loadJob: Job? = null

    // Reactive flow of lyrics for the currently active track
    val currentLyrics: StateFlow<Lyrics?> = currentTrack.flatMapLatest { track ->
        if (track == null) flowOf(null) else lyricsRepository.getLyrics(track)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOnlineEnabled: StateFlow<Boolean> = preferencesManager.isLrclibEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Derived active line index based on live player position
    val activeLineIndex: StateFlow<Int> = combine(currentLyrics, positionMs) { lyrics, pos ->
        if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            findActiveLineIndex(lyrics.lines, pos)
        } else {
            -1
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    init {
        // Automatically attempt to discover/load lyrics when track changes
        viewModelScope.launch {
            currentTrack.collect { track ->
                if (track != null) {
                    loadLyricsForTrack(track)
                }
            }
        }
    }

    private fun loadLyricsForTrack(track: Track) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                lyricsRepository.loadLyrics(track)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun setManualEditOpen(open: Boolean) {
        _isManualEditOpen.value = open
    }

    fun saveManualLyrics(content: String) {
        val track = currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (content.isNotBlank()) {
                lyricsRepository.saveLyrics(track, content.trim(), "user_manual")
            }
            _isManualEditOpen.value = false
        }
    }

    fun deleteLyrics() {
        val track = currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            lyricsRepository.deleteLyrics(track)
        }
    }

    fun pasteAndSaveLyrics(clipboardText: String) {
        val track = currentTrack.value ?: return
        val trimmed = clipboardText.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            lyricsRepository.saveLyrics(track, trimmed, "user_clipboard")
        }
    }

    fun importLyricsFromUri(uri: Uri) {
        val track = currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (!content.isNullOrBlank()) {
                    lyricsRepository.saveLyrics(track, content.trim(), "imported_file")
                }
            } catch (_: Exception) {
            }
        }
    }

    fun exportLyricsToUri(uri: Uri) {
        val lyrics = currentLyrics.value ?: return
        val raw = if (lyrics.isSynced) {
            lyrics.lines.joinToString("\n") { line ->
                val min = line.timestampMs / 60_000L
                val sec = (line.timestampMs % 60_000L) / 1_000L
                val frac = (line.timestampMs % 1_000L) / 10L
                String.format("[%02d:%02d.%02d]%s", min, sec, frac, line.text)
            }
        } else {
            lyrics.plainText.orEmpty()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().apply {
                        write(raw)
                        flush()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun searchCandidatesOnline(
        customTitle: String? = null,
        customArtist: String? = null
    ) {
        val track = currentTrack.value ?: return
        val title = customTitle?.ifBlank { null } ?: track.title
        val artist = customArtist?.ifBlank { null } ?: track.artist
        viewModelScope.launch {
            _isSearchingCandidates.value = true
            _showCandidatePicker.value = true
            try {
                val results = lyricsRepository.searchLyricsCandidates(title, artist)
                _lyricsCandidates.value = results
            } catch (_: Exception) {
                _lyricsCandidates.value = emptyList()
            } finally {
                _isSearchingCandidates.value = false
            }
        }
    }

    fun selectCandidate(candidate: com.example.musicplayer.domain.model.LyricsCandidate) {
        val track = currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val content = if (candidate.lyrics.isSynced) {
                candidate.lyrics.lines.joinToString("\n") { line ->
                    val min = line.timestampMs / 60_000L
                    val sec = (line.timestampMs % 60_000L) / 1_000L
                    val frac = (line.timestampMs % 1_000L) / 10L
                    String.format("[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                }
            } else {
                candidate.lyrics.plainText.orEmpty()
            }
            lyricsRepository.saveLyrics(track, content, candidate.sourceName)
            _showCandidatePicker.value = false
        }
    }

    fun dismissCandidatePicker() {
        _showCandidatePicker.value = false
    }

    fun resetLyricsToDefault() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                lyricsRepository.deleteLyrics(track)
                // Reload clean state
                lyricsRepository.loadLyrics(track)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchOnlineLyrics() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                lyricsRepository.fetchOnlineLyrics(track)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchCustomOnlineLyrics(
        title: String,
        artist: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val found = lyricsRepository.fetchOnlineLyricsWithCustomQuery(track, title, artist)
                onComplete(found != null && !found.isEmpty)
            } catch (_: Exception) {
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun findActiveLineIndex(lines: List<LyricLine>, currentPosMs: Long): Int {
        var active = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= currentPosMs) {
                active = i
            } else {
                break
            }
        }
        return active
    }
}
