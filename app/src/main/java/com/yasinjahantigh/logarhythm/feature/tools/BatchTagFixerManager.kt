package com.yasinjahantigh.logarhythm.feature.tools

import com.yasinjahantigh.logarhythm.data.ai.AiKeyStore
import com.yasinjahantigh.logarhythm.data.ai.AiPlaylistClient
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.download.ArtworkLoader
import com.yasinjahantigh.logarhythm.data.download.metadata.MetadataResolver
import com.yasinjahantigh.logarhythm.data.tagger.AudioFileTagWriter
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditResult
import com.yasinjahantigh.logarhythm.domain.repository.AiPlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class ScanPlan(
    val includeCleanTracks: Boolean,
    val totalCandidates: Int,
    val alreadyScannedCount: Int,
    val remainingUnscannedCount: Int,
    val existingSuggestionsCount: Int
)

/**
 * Application-scoped manager for Batch Tag & Cover Fixer.
 * Keeps scanning, re-searching, and saving tags running seamlessly in the background when the user
 * navigates away from LibraryToolsScreen, and remembers already-scanned tracks so accidental taps
 * don't wastefully re-scan the whole library.
 */
@Singleton
class BatchTagFixerManager @Inject constructor(
    private val trackRepository: TrackRepository,
    private val preferencesManager: PreferencesManager,
    private val playerController: PlayerController,
    private val metadataResolver: MetadataResolver,
    private val artworkLoader: ArtworkLoader,
    private val audioFileTagWriter: AudioFileTagWriter,
    private val aiPlaylistClient: AiPlaylistClient,
    private val aiKeyStore: AiKeyStore,
    private val aiPlaylistRepository: AiPlaylistRepository
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _suggestions = MutableStateFlow<List<TagFixSuggestion>?>(null)
    internal val suggestions: StateFlow<List<TagFixSuggestion>?> = _suggestions.asStateFlow()

    private val _findingFixes = MutableStateFlow(false)
    val findingFixes: StateFlow<Boolean> = _findingFixes.asStateFlow()

    private val _scanProcessed = MutableStateFlow(0)
    val scanProcessed: StateFlow<Int> = _scanProcessed.asStateFlow()

    private val _scanTotal = MutableStateFlow(0)
    val scanTotal: StateFlow<Int> = _scanTotal.asStateFlow()

    private val _currentScanningTrack = MutableStateFlow("")
    val currentScanningTrack: StateFlow<String> = _currentScanningTrack.asStateFlow()

    private val _onlineMatchesCount = MutableStateFlow(0)
    val onlineMatchesCount: StateFlow<Int> = _onlineMatchesCount.asStateFlow()

    private val _suggestVpn = MutableStateFlow(false)
    val suggestVpn: StateFlow<Boolean> = _suggestVpn.asStateFlow()

    private val _reSearchingTrackId = MutableStateFlow<String?>(null)
    val reSearchingTrackId: StateFlow<String?> = _reSearchingTrackId.asStateFlow()

    private val _applyingProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val applyingProgress: StateFlow<Pair<Int, Int>?> = _applyingProgress.asStateFlow()

    private val _aiSuggestions = MutableStateFlow<List<TagFixSuggestion>?>(null)
    internal val aiSuggestions: StateFlow<List<TagFixSuggestion>?> = _aiSuggestions.asStateFlow()

    private val _findingAiFixes = MutableStateFlow(false)
    val findingAiFixes: StateFlow<Boolean> = _findingAiFixes.asStateFlow()

    private val _savingFix = MutableStateFlow<String?>(null)
    val savingFix: StateFlow<String?> = _savingFix.asStateFlow()

    private val _fixFailed = MutableStateFlow(false)
    val fixFailed: StateFlow<Boolean> = _fixFailed.asStateFlow()

    val scannedTrackCount: StateFlow<Int> = preferencesManager.tagFixerScannedIds
        .map { it.size }
        .stateIn(managerScope, SharingStarted.Eagerly, 0)

    val isAiConfigured: Boolean get() = aiKeyStore.hasKey()

    private var suggestionJob: Job? = null
    private var aiJob: Job? = null

    fun cancelScan() {
        suggestionJob?.cancel()
        _findingFixes.value = false
        _currentScanningTrack.value = ""
    }

    /** Inspects how many candidate tracks were already scanned before launching a new scan. */
    suspend fun inspectScanPlan(includeCleanTracks: Boolean): ScanPlan = withContext(Dispatchers.IO) {
        val candidates = loadPrioritizedCandidates()
        val scannedIds = preferencesManager.tagFixerScannedIds.first()
        val alreadyScanned = candidates.count { it.id in scannedIds }
        val remaining = (candidates.size - alreadyScanned).coerceAtLeast(0)
        ScanPlan(
            includeCleanTracks = includeCleanTracks,
            totalCandidates = candidates.size,
            alreadyScannedCount = alreadyScanned,
            remainingUnscannedCount = remaining,
            existingSuggestionsCount = _suggestions.value?.size ?: 0
        )
    }

    /**
     * Scans local tracks in background-safe [managerScope].
     * @param includeCleanTracks whether to also look up tracks with already-clean basic tags
     * @param skipAlreadyScanned when true, skips track IDs already scanned in previous runs
     * @param clearExisting when true, replaces existing suggestions instead of appending
     */
    fun startScan(
        includeCleanTracks: Boolean = false,
        skipAlreadyScanned: Boolean = true,
        clearExisting: Boolean = false
    ) {
        if (_findingFixes.value || _savingFix.value != null || _applyingProgress.value != null) return
        _fixFailed.value = false
        _suggestVpn.value = false
        if (clearExisting) {
            _suggestions.value = emptyList()
            _onlineMatchesCount.value = 0
        } else if (_suggestions.value == null) {
            _suggestions.value = emptyList()
        }
        _scanProcessed.value = 0
        _scanTotal.value = 0
        _currentScanningTrack.value = ""

        suggestionJob = managerScope.launch {
            _findingFixes.value = true
            val newlyScannedBuffer = mutableListOf<String>()
            val scannedMutex = Mutex()
            try {
                if (!skipAlreadyScanned) {
                    preferencesManager.clearTagFixerScannedIds()
                }
                val alreadyScanned = if (skipAlreadyScanned) {
                    preferencesManager.tagFixerScannedIds.first()
                } else {
                    emptySet()
                }

                val prioritized = loadPrioritizedCandidates().let { all ->
                    if (skipAlreadyScanned) all.filterNot { it.id in alreadyScanned } else all
                }

                _scanTotal.value = prioritized.size
                if (prioritized.isEmpty()) return@launch

                val existingList = if (clearExisting) emptyList() else _suggestions.value.orEmpty()
                val collected = existingList.toMutableList()
                val existingIds = collected.map { it.track.id }.toMutableSet()
                val listMutex = Mutex()
                val processedCounter = AtomicInteger(0)
                val onlineAttempts = AtomicInteger(0)
                val onlineHits = AtomicInteger(if (clearExisting) 0 else _onlineMatchesCount.value)
                val semaphore = Semaphore(3)

                coroutineScope {
                    prioritized.map { track ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                _currentScanningTrack.value = track.title.ifBlank {
                                    track.filePath.substringAfterLast('/')
                                }
                                val suggestion = analyzeSingleTrack(
                                    track = track,
                                    includeCleanTracks = includeCleanTracks,
                                    onlineAttempts = onlineAttempts,
                                    onlineHits = onlineHits
                                )
                                if (suggestion != null) {
                                    listMutex.withLock {
                                        if (existingIds.add(suggestion.track.id)) {
                                            collected.add(suggestion)
                                        } else {
                                            val idx = collected.indexOfFirst { it.track.id == suggestion.track.id }
                                            if (idx >= 0) collected[idx] = suggestion
                                        }
                                        _suggestions.value = collected.toList()
                                    }
                                }
                                val flushBatch: List<String>? = scannedMutex.withLock {
                                    newlyScannedBuffer.add(track.id)
                                    if (newlyScannedBuffer.size >= 8) {
                                        val copy = newlyScannedBuffer.toList()
                                        newlyScannedBuffer.clear()
                                        copy
                                    } else null
                                }
                                if (flushBatch != null) {
                                    preferencesManager.addTagFixerScannedIds(flushBatch)
                                }

                                val done = processedCounter.incrementAndGet()
                                _scanProcessed.value = done
                                _onlineMatchesCount.value = onlineHits.get()
                                if (onlineAttempts.get() >= 3 && onlineHits.get() == 0) {
                                    _suggestVpn.value = true
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (onlineAttempts.get() >= 2 && onlineHits.get() == 0) {
                    _suggestVpn.value = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _fixFailed.value = true
                _suggestVpn.value = true
            } finally {
                val remaining = scannedMutex.withLock {
                    val copy = newlyScannedBuffer.toList()
                    newlyScannedBuffer.clear()
                    copy
                }
                if (remaining.isNotEmpty()) {
                    runCatching { preferencesManager.addTagFixerScannedIds(remaining) }
                }
                _findingFixes.value = false
                _currentScanningTrack.value = ""
            }
        }
    }

    private suspend fun loadPrioritizedCandidates(): List<Track> {
        val allLocal = trackRepository.getAllTracks().first()
            .filter { it.id.startsWith("local:") }
        return allLocal.sortedByDescending { track ->
            var score = 0
            if (TagSuggestionCleaner.isNoisyField(track.title)) score += 4
            if (TagSuggestionCleaner.isNoisyField(track.artist)) score += 4
            if (MetadataNormalizer.isPlaceholderField(track.album)) score += 2
            if (track.artworkUri.isNullOrBlank()) score += 3
            if (track.year == 0) score += 1
            if (MetadataNormalizer.isPlaceholderGenre(track.genre)) score += 1
            score
        }.take(MAX_SCAN_TRACKS)
    }

    private suspend fun analyzeSingleTrack(
        track: Track,
        includeCleanTracks: Boolean,
        onlineAttempts: AtomicInteger,
        onlineHits: AtomicInteger
    ): TagFixSuggestion? {
        val baseline = baselineTags(track)
        var tags = TagSuggestionCleaner.cleanTrack(track)
        val missingAlbum = MetadataNormalizer.isPlaceholderField(track.album) || TagSuggestionCleaner.isNoisyField(track.album)
        val missingGenre = MetadataNormalizer.isPlaceholderGenre(track.genre)
        val missingYear = track.year == 0
        val missingCover = track.artworkUri.isNullOrBlank() ||
            runCatching { audioFileTagWriter.readEmbeddedTags(track)?.artwork == null }.getOrDefault(false)
        val messyPrimary = tags != baseline || TagSuggestionCleaner.isNoisyField(track.title) || TagSuggestionCleaner.isNoisyField(track.artist)

        val needsWork = includeCleanTracks || messyPrimary || missingAlbum || missingGenre || missingYear || missingCover
        if (!needsWork) return null

        var coverUrl: String? = null
        var matchedOnline = false

        val lookupPairs = buildLookupCandidates(track, tags)
        if (lookupPairs.isNotEmpty()) {
            onlineAttempts.incrementAndGet()
            for ((queryTitle, queryArtist) in lookupPairs) {
                val resolved = runCatching {
                    metadataResolver.resolve(
                        title = queryTitle,
                        artist = queryArtist,
                        durationMs = track.durationMs,
                        replaceArtist = true
                    )
                }.getOrNull() ?: continue

                if (isUsefulOnlineMatch(resolved, queryTitle, queryArtist)) {
                    val resolvedGenre = resolved.genre
                        ?.let { MetadataNormalizer.canonicalizeGenre(it, "") }
                        ?.takeIf { it.isNotEmpty() }
                    val resolvedYear = resolved.year
                        ?.trim()
                        ?.take(4)
                        ?.toIntOrNull()
                        ?.takeIf { it in 1900..2100 }
                        ?: 0

                    tags = tags.copy(
                        title = resolved.title?.takeIf { it.isNotBlank() && !MetadataNormalizer.isPlaceholderField(it) } ?: tags.title,
                        artist = resolved.artist?.takeIf { it.isNotBlank() && !MetadataNormalizer.isPlaceholderField(it) } ?: tags.artist,
                        album = if (missingAlbum || tags.album.isBlank()) {
                            resolved.album?.takeIf { it.isNotBlank() && !MetadataNormalizer.isPlaceholderField(it) } ?: tags.album
                        } else tags.album,
                        genre = if (missingGenre && resolvedGenre != null) resolvedGenre else tags.genre,
                        year = if (missingYear && resolvedYear > 0) resolvedYear else tags.year
                    )
                    if (missingCover && !resolved.artworkUrl.isNullOrBlank()) {
                        coverUrl = resolved.artworkUrl
                    }
                    matchedOnline = true
                    onlineHits.incrementAndGet()
                    break
                }
            }
        }

        return if (tags != baseline || coverUrl != null) {
            TagFixSuggestion(
                track = track,
                tags = tags,
                artworkUrl = coverUrl,
                matchedOnline = matchedOnline
            )
        } else null
    }

    private fun buildLookupCandidates(
        track: Track,
        cleaned: TagSuggestionCleaner.Tags
    ): List<Pair<String, String>> {
        val candidates = LinkedHashSet<Pair<String, String>>()
        val safeTitle = cleaned.title.trim()
        val safeArtist = if (MetadataNormalizer.isPlaceholderField(cleaned.artist)) "" else cleaned.artist.trim()

        if (safeTitle.length >= 2 && !MetadataNormalizer.isPlaceholderField(safeTitle)) {
            candidates.add(safeTitle to safeArtist)
        }

        val (fileArtist, fileTitle) = TagSuggestionCleaner.parseFilenameFallback(track.filePath)
        if (fileTitle.length >= 2 && !MetadataNormalizer.isPlaceholderField(fileTitle)) {
            val cleanFileArtist = if (MetadataNormalizer.isPlaceholderField(fileArtist)) "" else fileArtist
            candidates.add(fileTitle to cleanFileArtist)
            if (cleanFileArtist.length >= 2) {
                candidates.add(cleanFileArtist to fileTitle)
            }
        }
        return candidates.toList().take(3)
    }

    private fun isUsefulOnlineMatch(
        resolved: MetadataResolver.Resolved,
        queryTitle: String,
        queryArtist: String
    ): Boolean {
        val rTitle = resolved.title.orEmpty().trim()
        val rArtist = resolved.artist.orEmpty().trim()
        val hasNewEnrichment = !resolved.album.isNullOrBlank() ||
            !resolved.artworkUrl.isNullOrBlank() ||
            !resolved.genre.isNullOrBlank() ||
            !resolved.year.isNullOrBlank() ||
            (queryArtist.isBlank() && rArtist.isNotBlank())

        if (!hasNewEnrichment) return false
        val titleSim = DownloadMetadataMerge.similarity(rTitle, queryTitle)
        if (queryArtist.isBlank()) {
            return titleSim >= 0.62 && rArtist.isNotBlank()
        }
        val artistSim = DownloadMetadataMerge.similarity(rArtist, queryArtist)
        return (titleSim >= 0.58 && artistSim >= 0.55) || (titleSim >= 0.78 && artistSim >= 0.42)
    }

    internal fun updateSuggestion(trackId: String, updatedTags: TagSuggestionCleaner.Tags, isAi: Boolean = false) {
        val targetFlow = if (isAi) _aiSuggestions else _suggestions
        targetFlow.value = targetFlow.value?.map { item ->
            if (item.track.id == trackId) item.copy(tags = updatedTags) else item
        }
    }

    fun dismissSuggestion(trackId: String, isAi: Boolean = false) {
        val targetFlow = if (isAi) _aiSuggestions else _suggestions
        targetFlow.value = targetFlow.value?.filterNot { it.track.id == trackId }
    }

    fun reSearchTrackOnline(
        trackId: String,
        queryTitle: String,
        queryArtist: String,
        isAi: Boolean = false,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (_reSearchingTrackId.value != null) return
        val targetFlow = if (isAi) _aiSuggestions else _suggestions
        val existing = targetFlow.value?.firstOrNull { it.track.id == trackId } ?: return

        managerScope.launch {
            _reSearchingTrackId.value = trackId
            try {
                val resolved = metadataResolver.resolve(
                    title = queryTitle.trim(),
                    artist = queryArtist.trim(),
                    durationMs = existing.track.durationMs,
                    replaceArtist = true
                )
                val hasData = !resolved.album.isNullOrBlank() ||
                    !resolved.artworkUrl.isNullOrBlank() ||
                    !resolved.genre.isNullOrBlank() ||
                    !resolved.year.isNullOrBlank() ||
                    (!resolved.title.isNullOrBlank() && resolved.title != queryTitle)

                if (hasData) {
                    val resolvedGenre = resolved.genre
                        ?.let { MetadataNormalizer.canonicalizeGenre(it, "") }
                        ?.takeIf { it.isNotEmpty() }
                    val resolvedYear = resolved.year
                        ?.trim()
                        ?.take(4)
                        ?.toIntOrNull()
                        ?.takeIf { it in 1900..2100 }
                        ?: existing.tags.year

                    val updatedTags = existing.tags.copy(
                        title = resolved.title?.takeIf { it.isNotBlank() } ?: queryTitle.trim(),
                        artist = resolved.artist?.takeIf { it.isNotBlank() } ?: queryArtist.trim(),
                        album = resolved.album?.takeIf { it.isNotBlank() } ?: existing.tags.album,
                        genre = resolvedGenre ?: existing.tags.genre,
                        year = resolvedYear
                    )
                    targetFlow.value = targetFlow.value?.map { item ->
                        if (item.track.id == trackId) {
                            item.copy(
                                tags = updatedTags,
                                artworkUrl = resolved.artworkUrl ?: item.artworkUrl,
                                matchedOnline = true
                            )
                        } else item
                    }
                    _suggestVpn.value = false
                    withContext(Dispatchers.Main) { onResult(true) }
                } else {
                    _suggestVpn.value = true
                    withContext(Dispatchers.Main) { onResult(false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _suggestVpn.value = true
                withContext(Dispatchers.Main) { onResult(false) }
            } finally {
                _reSearchingTrackId.value = null
            }
        }
    }

    fun suggestAiFixes() {
        if (_findingAiFixes.value || _savingFix.value != null || !isAiConfigured) return
        val apiKey = aiKeyStore.load() ?: return
        _aiSuggestions.value = emptyList()
        _fixFailed.value = false
        aiJob = managerScope.launch {
            _findingAiFixes.value = true
            try {
                val settings = aiPlaylistRepository.observeSettings().first()
                val allTracks = trackRepository.getAllTracks().first().filter { it.id.startsWith("local:") }
                val candidates = allTracks.filter { t ->
                    TagSuggestionCleaner.isNoisyField(t.title) ||
                        TagSuggestionCleaner.isNoisyField(t.artist) ||
                        MetadataNormalizer.isPlaceholderField(t.album) ||
                        MetadataNormalizer.isPlaceholderGenre(t.genre) ||
                        t.year == 0 ||
                        t.artist.contains(Regex("""(?i)\b(feat|ft|with|&|,)\b"""))
                }.take(60)
                val targetTracks = if (candidates.isNotEmpty()) candidates else allTracks.take(20)
                val collected = mutableListOf<TagFixSuggestion>()
                val semaphore = Semaphore(4)
                for (batch in targetTracks.chunked(20)) {
                    val cleanedMap = aiPlaylistClient.cleanMetadataTagsWithAi(settings, apiKey, batch)
                    val batchSuggestions = coroutineScope {
                        batch.map { track ->
                            async {
                                semaphore.withPermit {
                                    val cleaned = cleanedMap[track.id] ?: return@withPermit null
                                    val baseline = baselineTags(track)
                                    val missingGenre = MetadataNormalizer.isPlaceholderGenre(track.genre)
                                    val missingCover = track.artworkUri.isNullOrBlank() ||
                                        runCatching { audioFileTagWriter.readEmbeddedTags(track)?.artwork == null }.getOrDefault(false)
                                    var tags = TagSuggestionCleaner.Tags(
                                        title = cleaned.title.ifBlank { track.title },
                                        artist = cleaned.artist.ifBlank { track.artist },
                                        album = cleaned.album.ifBlank { track.album },
                                        genre = when {
                                            cleaned.genre.isBlank() -> track.genre
                                            missingGenre -> cleaned.genre
                                            else -> track.genre
                                        },
                                        year = if (cleaned.year > 0 && track.year == 0) cleaned.year else track.year,
                                        albumArtist = track.albumArtist
                                    )
                                    var coverUrl: String? = null
                                    if (tags.canLookup && (missingCover || MetadataNormalizer.isPlaceholderField(tags.album) || tags.year == 0)) {
                                        val resolved = metadataResolver.resolve(tags.title, tags.artist, track.durationMs, replaceArtist = false)
                                        val tSim = DownloadMetadataMerge.similarity(resolved.title.orEmpty(), tags.title)
                                        val aSim = DownloadMetadataMerge.similarity(resolved.artist.orEmpty(), tags.artist)
                                        if (tSim >= 0.65 && aSim >= 0.60) {
                                            if (MetadataNormalizer.isPlaceholderField(tags.album) && !resolved.album.isNullOrBlank()) {
                                                tags = tags.copy(album = resolved.album)
                                            }
                                            val rYear = resolved.year?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 } ?: 0
                                            if (tags.year == 0 && rYear > 0) {
                                                tags = tags.copy(year = rYear)
                                            }
                                            if (missingCover) coverUrl = resolved.artworkUrl
                                        }
                                    }
                                    if (tags != baseline || coverUrl != null) {
                                        TagFixSuggestion(track, tags, coverUrl, matchedOnline = true)
                                    } else null
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    collected.addAll(batchSuggestions)
                    _aiSuggestions.value = collected.toList()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _fixFailed.value = true
                _suggestVpn.value = true
            } finally {
                _findingAiFixes.value = false
            }
        }
    }

    private fun baselineTags(track: Track): TagSuggestionCleaner.Tags = TagSuggestionCleaner.Tags(
        title = track.title,
        artist = track.artist,
        album = track.album,
        genre = track.genre,
        year = track.year,
        albumArtist = track.albumArtist
    )

    fun dismissSuggestions() {
        if (_savingFix.value != null) return
        suggestionJob?.cancel()
        _suggestions.value = null
    }

    fun applySuggestion(trackId: String, isAi: Boolean = false) {
        if (_savingFix.value != null || _applyingProgress.value != null) return
        val pool = if (isAi) _aiSuggestions.value else _suggestions.value
        val suggestion = pool?.firstOrNull { it.track.id == trackId } ?: return
        managerScope.launch {
            _savingFix.value = trackId
            _fixFailed.value = false
            val resume = playerController.currentTrack.value?.id == trackId && playerController.isPlaying.value
            if (resume) withContext(Dispatchers.Main) { playerController.pause() }
            try {
                val current = trackRepository.getTrackById(trackId).first()
                if (current == null) {
                    _fixFailed.value = true
                    return@launch
                }
                val edit = buildTrackEdit(current, suggestion)
                when (val result = trackRepository.updateTrackDetails(trackId, edit)) {
                    is TrackEditResult.Success -> {
                        withContext(Dispatchers.Main) {
                            playerController.updateTrackInPlayback(result.track)
                        }
                        preferencesManager.addTagFixerScannedIds(listOf(trackId))
                        if (isAi) {
                            _aiSuggestions.value = _aiSuggestions.value.orEmpty().filterNot { it.track.id == trackId }
                        } else {
                            _suggestions.value = _suggestions.value.orEmpty().filterNot { it.track.id == trackId }
                        }
                    }
                    is TrackEditResult.Failure -> _fixFailed.value = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _fixFailed.value = true
            } finally {
                if (resume && playerController.currentTrack.value?.id == trackId) {
                    withContext(Dispatchers.Main) { playerController.resume() }
                }
                _savingFix.value = null
            }
        }
    }

    fun applyAllSuggestions(isAi: Boolean = false, onDone: (Int) -> Unit = {}) {
        val pool = (if (isAi) _aiSuggestions.value else _suggestions.value).orEmpty()
        if (pool.isEmpty() || _savingFix.value != null || _applyingProgress.value != null) return
        managerScope.launch {
            _applyingProgress.value = 0 to pool.size
            val appliedIds = mutableSetOf<String>()
            try {
                pool.forEachIndexed { idx, suggestion ->
                    val trackId = suggestion.track.id
                    _savingFix.value = trackId
                    val current = trackRepository.getTrackById(trackId).first()
                    if (current != null) {
                        val edit = buildTrackEdit(current, suggestion)
                        when (val result = trackRepository.updateTrackDetails(trackId, edit)) {
                            is TrackEditResult.Success -> {
                                withContext(Dispatchers.Main) {
                                    playerController.updateTrackInPlayback(result.track)
                                }
                                appliedIds.add(trackId)
                            }
                            else -> Unit
                        }
                    }
                    _applyingProgress.value = (idx + 1) to pool.size
                }
                if (appliedIds.isNotEmpty()) {
                    preferencesManager.addTagFixerScannedIds(appliedIds)
                }
                if (isAi) {
                    _aiSuggestions.value = _aiSuggestions.value.orEmpty().filterNot { it.track.id in appliedIds }
                } else {
                    _suggestions.value = _suggestions.value.orEmpty().filterNot { it.track.id in appliedIds }
                }
                withContext(Dispatchers.Main) { onDone(appliedIds.size) }
            } finally {
                _savingFix.value = null
                _applyingProgress.value = null
            }
        }
    }

    private suspend fun buildTrackEdit(current: Track, suggestion: TagFixSuggestion): TrackEdit {
        val tags = suggestion.tags
        val artworkBytes = suggestion.artworkUrl?.let { url ->
            runCatching { artworkLoader.load(url) }.getOrNull()
        }
        return TrackEdit(
            title = tags.title,
            artist = tags.artist,
            album = tags.album,
            albumArtist = tags.albumArtist.ifBlank { current.albumArtist },
            genre = tags.genre.ifBlank { current.genre },
            year = if (tags.year > 0) tags.year else current.year,
            trackNumber = current.trackNumber,
            discNumber = current.discNumber,
            composer = current.composer,
            artwork = artworkBytes,
            artworkMime = if (artworkBytes != null) "image/jpeg" else null
        )
    }

    private companion object {
        const val MAX_SCAN_TRACKS = 400
    }
}
