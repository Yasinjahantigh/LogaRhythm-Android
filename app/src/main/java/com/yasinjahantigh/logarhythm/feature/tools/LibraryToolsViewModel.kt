package com.yasinjahantigh.logarhythm.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.core.utils.AudioFingerprint
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder
import com.yasinjahantigh.logarhythm.domain.library.DuplicateGroup
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class TagFixSuggestion(
    val track: Track,
    val tags: TagSuggestionCleaner.Tags,
    val artworkUrl: String? = null,
    val matchedOnline: Boolean = false
)

/**
 * Step-by-step Batch Tag & Cover Fixer, missing tags inspector, duplicates, hidden tracks,
 * and optional AI metadata cleaner.
 * Heavy scan/apply work is delegated to [BatchTagFixerManager] so it keeps running in the
 * background even when the user navigates away from this screen.
 */
@HiltViewModel
class LibraryToolsViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    preferencesManager: PreferencesManager,
    private val playerController: PlayerController,
    private val batchTagFixerManager: BatchTagFixerManager,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val separators = preferencesManager.artistSeparators.map { ArtistSplitter.parseSeparators(it) }

    val trackFileSizes: StateFlow<Map<String, Long>> =
        trackRepository.getAllTracks()
            .map { tracks -> resolveFileSizes(tracks) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val audioFingerprints: StateFlow<Map<String, String>> =
        combine(trackRepository.getAllTracks(), trackFileSizes) { tracks, sizes ->
            resolveCandidateAudioFingerprints(tracks, sizes)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val duplicates: StateFlow<List<DuplicateGroup>?> =
        combine(
            trackRepository.getAllTracks(),
            separators,
            trackFileSizes,
            audioFingerprints
        ) { tracks, seps, sizes, fingerprints ->
            DuplicateFinder.find(tracks, seps, sizes, fingerprints)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val missingTags: StateFlow<List<Track>?> =
        trackRepository.getAllTracks()
            .map { DuplicateFinder.missingTags(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hiddenTracks: StateFlow<List<Track>?> =
        trackRepository.getHiddenTracks()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun resolveFileSizes(tracks: List<Track>): Map<String, Long> {
        val result = HashMap<String, Long>(tracks.size)
        for (track in tracks) {
            var size = 0L
            if (track.filePath.isNotBlank()) {
                val f = java.io.File(track.filePath)
                if (f.isFile) size = f.length()
            }
            if (size <= 0L && track.uri.startsWith("content://")) {
                size = runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(track.uri), "r")?.use { afd ->
                        afd.length.takeIf { it > 0L } ?: 0L
                    } ?: 0L
                }.getOrDefault(0L)
            }
            if (size > 0L) result[track.id] = size
        }
        return result
    }

    private fun resolveCandidateAudioFingerprints(
        tracks: List<Track>,
        fileSizes: Map<String, Long>
    ): Map<String, String> {
        val candidateIds = AudioFingerprint.findCandidateIds(tracks, fileSizes)
        if (candidateIds.isEmpty()) return emptyMap()
        val result = HashMap<String, String>(candidateIds.size)
        for (track in tracks) {
            if (track.id !in candidateIds) continue
            val fp = AudioFingerprint.computeForTrack(appContext, track)
            if (!fp.isNullOrBlank()) {
                result[track.id] = fp
            }
        }
        return result
    }

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    internal val suggestions: StateFlow<List<TagFixSuggestion>?> = batchTagFixerManager.suggestions
    val findingFixes: StateFlow<Boolean> = batchTagFixerManager.findingFixes
    val scanProcessed: StateFlow<Int> = batchTagFixerManager.scanProcessed
    val scanTotal: StateFlow<Int> = batchTagFixerManager.scanTotal
    val currentScanningTrack: StateFlow<String> = batchTagFixerManager.currentScanningTrack
    val onlineMatchesCount: StateFlow<Int> = batchTagFixerManager.onlineMatchesCount
    val suggestVpn: StateFlow<Boolean> = batchTagFixerManager.suggestVpn
    val reSearchingTrackId: StateFlow<String?> = batchTagFixerManager.reSearchingTrackId
    val applyingProgress: StateFlow<Pair<Int, Int>?> = batchTagFixerManager.applyingProgress
    internal val aiSuggestions: StateFlow<List<TagFixSuggestion>?> = batchTagFixerManager.aiSuggestions
    val findingAiFixes: StateFlow<Boolean> = batchTagFixerManager.findingAiFixes
    val savingFix: StateFlow<String?> = batchTagFixerManager.savingFix
    val fixFailed: StateFlow<Boolean> = batchTagFixerManager.fixFailed
    val scannedTrackCount: StateFlow<Int> = batchTagFixerManager.scannedTrackCount

    val isAiConfigured: Boolean get() = batchTagFixerManager.isAiConfigured

    private val _pendingScanPlan = MutableStateFlow<ScanPlan?>(null)
    val pendingScanPlan: StateFlow<ScanPlan?> = _pendingScanPlan.asStateFlow()

    fun cancelScan() = batchTagFixerManager.cancelScan()

    /**
     * Checks whether any tracks have already been scanned or suggestions already exist.
     * On first-ever scan it starts immediately; otherwise it asks the user to confirm
     * whether to scan only remaining unscanned tracks, rescan all from scratch, or cancel.
     */
    fun requestScan(includeCleanTracks: Boolean = false) {
        if (findingFixes.value || savingFix.value != null || applyingProgress.value != null) return
        viewModelScope.launch {
            val plan = batchTagFixerManager.inspectScanPlan(includeCleanTracks)
            if (plan.alreadyScannedCount == 0 && plan.existingSuggestionsCount == 0) {
                batchTagFixerManager.startScan(
                    includeCleanTracks = includeCleanTracks,
                    skipAlreadyScanned = true,
                    clearExisting = true
                )
            } else {
                _pendingScanPlan.value = plan
            }
        }
    }

    fun confirmScanRemaining() {
        val plan = _pendingScanPlan.value ?: return
        _pendingScanPlan.value = null
        batchTagFixerManager.startScan(
            includeCleanTracks = plan.includeCleanTracks,
            skipAlreadyScanned = true,
            clearExisting = false
        )
    }

    fun confirmRescanAll() {
        val plan = _pendingScanPlan.value ?: return
        _pendingScanPlan.value = null
        batchTagFixerManager.startScan(
            includeCleanTracks = plan.includeCleanTracks,
            skipAlreadyScanned = false,
            clearExisting = true
        )
    }

    fun dismissScanConfirm() {
        _pendingScanPlan.value = null
    }

    fun suggestFixes(includeCleanTracks: Boolean = false) {
        requestScan(includeCleanTracks)
    }

    internal fun updateSuggestion(trackId: String, updatedTags: TagSuggestionCleaner.Tags, isAi: Boolean = false) {
        batchTagFixerManager.updateSuggestion(trackId, updatedTags, isAi)
    }

    fun dismissSuggestion(trackId: String, isAi: Boolean = false) {
        batchTagFixerManager.dismissSuggestion(trackId, isAi)
    }

    fun reSearchTrackOnline(
        trackId: String,
        queryTitle: String,
        queryArtist: String,
        isAi: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    ) {
        batchTagFixerManager.reSearchTrackOnline(trackId, queryTitle, queryArtist, isAi, onResult)
    }

    fun suggestAiFixes() = batchTagFixerManager.suggestAiFixes()

    fun dismissSuggestions() = batchTagFixerManager.dismissSuggestions()

    fun applySuggestion(trackId: String, isAi: Boolean = false) {
        batchTagFixerManager.applySuggestion(trackId, isAi)
    }

    fun applyAllSuggestions(isAi: Boolean = false, onDone: (Int) -> Unit = {}) {
        batchTagFixerManager.applyAllSuggestions(isAi, onDone)
    }

    fun play(track: Track) = playerController.play(listOf(track), 0)
    fun playNext(track: Track) = playerController.playNext(track)
    fun addToQueue(track: Track) = playerController.addToQueue(track)

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            trackRepository.hideTrack(track.id)
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

    fun deleteTrack(track: Track, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = runCatching { trackRepository.deleteTrack(track) }.getOrDefault(false)
            onResult(deleted)
        }
    }

    fun deleteTracks(tracks: List<Track>, onResult: (Int) -> Unit = {}) {
        if (tracks.isEmpty()) {
            onResult(0)
            return
        }
        viewModelScope.launch {
            var deletedCount = 0
            for (track in tracks) {
                if (runCatching { trackRepository.deleteTrack(track) }.getOrDefault(false)) {
                    deletedCount++
                }
            }
            onResult(deletedCount)
        }
    }
}
