package com.yasinjahantigh.logarhythm.feature.lyrics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.model.LyricLine
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsMismatch
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.LyricsRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import kotlinx.coroutines.CancellationException
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
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    private val positionMs: StateFlow<Long> = playerController.position

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isManualEditOpen = MutableStateFlow(false)
    val isManualEditOpen: StateFlow<Boolean> = _isManualEditOpen.asStateFlow()

    private val _lyricsCandidates = MutableStateFlow<List<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate>>(emptyList())
    val lyricsCandidates: StateFlow<List<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate>> = _lyricsCandidates.asStateFlow()

    private val _isSearchingCandidates = MutableStateFlow(false)
    val isSearchingCandidates: StateFlow<Boolean> = _isSearchingCandidates.asStateFlow()

    private val _showCandidatePicker = MutableStateFlow(false)
    val showCandidatePicker: StateFlow<Boolean> = _showCandidatePicker.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private var loadJob: Job? = null

    // Reactive flow of lyrics for the currently active track
    val currentLyrics: StateFlow<Lyrics?> = currentTrack.flatMapLatest { track ->
        if (track == null) flowOf(null) else lyricsRepository.getLyrics(track)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOnlineEnabled: StateFlow<Boolean> = preferencesManager.isLrclibEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Live playback position; only the active karaoke line collects it. */
    val livePositionMs: StateFlow<Long> = positionMs

    /** The user's timing correction for the current song (positive = lyrics appear earlier). */
    val offsetMs: StateFlow<Long> = currentTrack.flatMapLatest { track ->
        if (track == null) flowOf(0L) else lyricsRepository.getOffset(track.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** True when the song's lyrics were hidden by the user; the UI then offers "Restore". */
    val isHidden: StateFlow<Boolean> = combine(currentTrack, lyricsRepository.getHiddenTrackIds()) { track, hidden ->
        track != null && track.id in hidden
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Synced lyrics whose timestamps cannot fit this song's length: probably the wrong song. */
    val lyricsMismatch: StateFlow<Boolean> = combine(currentLyrics, currentTrack) { lyrics, track ->
        lyrics != null && lyrics.isSynced && track != null &&
            LyricsMismatch.isLikely(lyrics.lines, track.durationMs)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Derived active line index based on live player position
    val activeLineIndex: StateFlow<Int> = combine(currentLyrics, positionMs, offsetMs) { lyrics, pos, offset ->
        if (lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            findActiveLineIndex(lyrics.lines, pos + offset)
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

    fun nudgeOffset(deltaMs: Long) {
        val track = currentTrack.value ?: return
        val next = (offsetMs.value + deltaMs).coerceIn(-10_000L, 10_000L)
        viewModelScope.launch(Dispatchers.IO) { lyricsRepository.setOffset(track.id, next) }
    }

    fun resetOffset() {
        val track = currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) { lyricsRepository.setOffset(track.id, 0L) }
    }

    /** Un-hides lyrics and looks them up again, so wrong lyrics never trap the user. */
    fun restoreLyrics() {
        val track = currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            lyricsRepository.resetLyricsToDefault(track.id)
            loadLyricsForTrack(track)
        }
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun setManualEditOpen(open: Boolean) {
        _isManualEditOpen.value = open
    }

    fun saveManualLyrics(content: String, targetTrack: Track? = currentTrack.value) {
        val track = targetTrack ?: return
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            _saveMessage.value = "failed"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val embedded: Boolean? = try {
                lyricsRepository.saveLyrics(track, trimmed, "user_manual")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // A thrown cache write is not a successful cache-only save.
            } finally {
                _isLoading.value = false
            }
            _saveMessage.value = when (embedded) {
                true -> "embedded"
                false -> "cached_only"
                null -> "failed"
            }
            if (embedded != null) _isManualEditOpen.value = false
        }
    }

    fun deleteLyrics(targetTrack: Track? = currentTrack.value, removeFromFile: Boolean = true) {
        val track = targetTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileClean = lyricsRepository.deleteLyrics(track, removeFromFile)
                // "deleted": gone from LogaRhythm and from the audio file.
                // "hidden": hidden in LogaRhythm, but a copy may still sit inside the file.
                _saveMessage.value = if (fileClean) "deleted" else "hidden"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveMessage.value = "delete_failed"
            }
        }
    }

    fun pasteAndSaveLyrics(clipboardText: String, targetTrack: Track? = currentTrack.value) {
        val track = targetTrack ?: return
        val trimmed = clipboardText.trim()
        if (trimmed.isBlank()) {
            _saveMessage.value = "failed"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val embedded: Boolean? = try {
                lyricsRepository.saveLyrics(track, trimmed, "user_clipboard")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                _isLoading.value = false
            }
            _saveMessage.value = when (embedded) {
                true -> "embedded"
                false -> "cached_only"
                null -> "failed"
            }
        }
    }

    fun importLyricsFromUri(uri: Uri, targetTrack: Track? = currentTrack.value) {
        val track = targetTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (!content.isNullOrBlank()) {
                    val embedded = lyricsRepository.saveLyrics(track, content.trim(), "imported_file")
                    _saveMessage.value = if (embedded) "embedded" else "cached_only"
                } else {
                    _saveMessage.value = "failed"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveMessage.value = "failed"
            } finally {
                _isLoading.value = false
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
                String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _lyricsCandidates.value = emptyList()
            } finally {
                _isSearchingCandidates.value = false
            }
        }
    }

    fun selectCandidate(
        candidate: com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate,
        targetTrack: Track? = currentTrack.value
    ) {
        val track = targetTrack ?: return
        val content = if (candidate.lyrics.isSynced) {
            candidate.lyrics.lines.joinToString("\n") { line ->
                val min = line.timestampMs / 60_000L
                val sec = (line.timestampMs % 60_000L) / 1_000L
                val frac = (line.timestampMs % 1_000L) / 10L
                String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
            }
        } else {
            candidate.lyrics.plainText.orEmpty()
        }.trim()
        if (content.isBlank()) {
            _saveMessage.value = "failed"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val embedded: Boolean? = try {
                lyricsRepository.saveLyrics(track, content, candidate.sourceName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                _isLoading.value = false
            }
            _saveMessage.value = when (embedded) {
                true -> "embedded"
                false -> "cached_only"
                null -> "failed"
            }
            if (embedded != null) _showCandidatePicker.value = false
        }
    }

    fun reportSaveFailure() {
        _saveMessage.value = "failed"
    }

    fun consumeSaveMessage() {
        _saveMessage.value = null
    }

    fun dismissCandidatePicker() {
        _showCandidatePicker.value = false
    }

    fun resetLyricsToDefault(targetTrack: Track? = currentTrack.value) {
        val track = targetTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Reset is not deletion: remove the Room override/tombstone so local embedded
                // or companion lyrics can be discovered again.
                lyricsRepository.resetLyricsToDefault(track.id)
                lyricsRepository.loadLyrics(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveMessage.value = "reset_failed"
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Empty/error UI state is preferable to crashing the lyrics screen.
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
