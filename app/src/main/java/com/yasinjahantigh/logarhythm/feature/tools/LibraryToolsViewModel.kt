package com.yasinjahantigh.logarhythm.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder
import com.yasinjahantigh.logarhythm.domain.library.DuplicateGroup
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yasinjahantigh.logarhythm.data.ai.AiKeyStore
import com.yasinjahantigh.logarhythm.data.ai.AiPlaylistClient
import com.yasinjahantigh.logarhythm.data.download.ArtworkLoader
import com.yasinjahantigh.logarhythm.domain.repository.AiPlaylistRepository
import com.yasinjahantigh.logarhythm.data.download.metadata.MetadataResolver
import com.yasinjahantigh.logarhythm.data.tagger.AudioFileTagWriter
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class TagFixSuggestion(
    val track: Track,
    val tags: TagSuggestionCleaner.Tags,
    val artworkUrl: String? = null
)

/** Duplicates, "missing tags", and hidden tracks views; computed off the main thread from the library. */
@HiltViewModel
class LibraryToolsViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    preferencesManager: PreferencesManager,
    private val playerController: PlayerController,
    private val metadataResolver: MetadataResolver,
    private val artworkLoader: ArtworkLoader,
    private val audioFileTagWriter: AudioFileTagWriter,
    private val aiPlaylistClient: AiPlaylistClient,
    private val aiKeyStore: AiKeyStore,
    private val aiPlaylistRepository: AiPlaylistRepository
) : ViewModel() {

    private val separators = preferencesManager.artistSeparators.map { ArtistSplitter.parseSeparators(it) }

    val duplicates: StateFlow<List<DuplicateGroup>?> =
        combine(trackRepository.getAllTracks(), separators) { tracks, seps ->
            DuplicateFinder.find(tracks, seps)
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

    val currentTrack: StateFlow<Track?> = playerController.currentTrack
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying

    private val _suggestions = MutableStateFlow<List<TagFixSuggestion>?>(null)
    internal val suggestions = _suggestions.asStateFlow()
    private val _findingFixes = MutableStateFlow(false)
    val findingFixes = _findingFixes.asStateFlow()

    private val _aiSuggestions = MutableStateFlow<List<TagFixSuggestion>?>(null)
    internal val aiSuggestions = _aiSuggestions.asStateFlow()
    private val _findingAiFixes = MutableStateFlow(false)
    val findingAiFixes = _findingAiFixes.asStateFlow()

    val isAiConfigured: Boolean get() = aiKeyStore.hasKey()

    private val _savingFix = MutableStateFlow<String?>(null)
    val savingFix = _savingFix.asStateFlow()
    private val _fixFailed = MutableStateFlow(false)
    val fixFailed = _fixFailed.asStateFlow()
    private var suggestionJob: Job? = null
    private var aiJob: Job? = null

    fun suggestFixes() {
        if (_findingFixes.value || _savingFix.value != null) return
        _suggestions.value = emptyList()
        _fixFailed.value = false
        suggestionJob = viewModelScope.launch {
            _findingFixes.value = true
            try {
                val localTracks = trackRepository.getAllTracks().first()
                    .filter { it.id.startsWith("local:") }
                val semaphore = Semaphore(4)
                coroutineScope {
                    localTracks.take(80).map { track ->
                        async {
                            semaphore.withPermit {
                                val baseline = baselineTags(track)
                                var tags = TagSuggestionCleaner.cleanTrack(track)
                                val missingAlbum = MetadataNormalizer.isPlaceholderField(track.album)
                                val missingGenre = MetadataNormalizer.isPlaceholderGenre(track.genre)
                                val missingYear = track.year == 0
                                val missingCover = track.artworkUri.isNullOrBlank() ||
                                    runCatching { audioFileTagWriter.readEmbeddedTags(track)?.artwork == null }.getOrDefault(false)

                                val needsWork = tags != baseline || missingAlbum || missingGenre || missingYear || missingCover
                                if (!needsWork) return@withPermit null

                                var coverUrl: String? = null
                                if (tags.canLookup) {
                                    val resolved = metadataResolver.resolve(
                                        title = tags.title,
                                        artist = tags.artist,
                                        durationMs = track.durationMs,
                                        replaceArtist = true
                                    )
                                    val titleMatch = DownloadMetadataMerge.similarity(resolved.title.orEmpty(), tags.title)
                                    val artistMatch = DownloadMetadataMerge.similarity(resolved.artist.orEmpty(), tags.artist)
                                    if (titleMatch >= 0.72 && artistMatch >= 0.70) {
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
                                            title = resolved.title?.takeIf { it.isNotBlank() } ?: tags.title,
                                            artist = resolved.artist?.takeIf { it.isNotBlank() } ?: tags.artist,
                                            album = if (missingAlbum) resolved.album?.takeIf { it.isNotBlank() } ?: tags.album else tags.album,
                                            genre = if (missingGenre && resolvedGenre != null) resolvedGenre else tags.genre,
                                            year = if (missingYear && resolvedYear > 0) resolvedYear else tags.year
                                        )
                                        if (missingCover) {
                                            coverUrl = resolved.artworkUrl
                                        }
                                    }
                                }

                                if (tags != baseline || coverUrl != null) TagFixSuggestion(track, tags, coverUrl) else null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }.let { suggestions ->
                    _suggestions.value = suggestions
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _fixFailed.value = true
            } finally {
                _findingFixes.value = false
            }
        }
    }

    fun suggestAiFixes() {
        if (_findingAiFixes.value || _savingFix.value != null || !isAiConfigured) return
        val apiKey = aiKeyStore.load() ?: return
        _aiSuggestions.value = emptyList()
        _fixFailed.value = false
        aiJob = viewModelScope.launch {
            _findingAiFixes.value = true
            try {
                val settings = aiPlaylistRepository.observeSettings().first()
                val allTracks = trackRepository.getAllTracks().first().filter { it.id.startsWith("local:") }
                val candidates = allTracks.filter { t ->
                    t.title.contains(Regex("""[\[\(\]_\-–—@]|(?i)\b(feat|ft|official|remix|4k|hd|video|audio|lyrics|320|128)\b|\.(?:ir|com|net)\b|دانلود""")) ||
                        MetadataNormalizer.isPlaceholderField(t.artist) ||
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
                                        if (tSim >= 0.72 && aSim >= 0.70) {
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
                                    if (tags != baseline || coverUrl != null) TagFixSuggestion(track, tags, coverUrl) else null
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
        if (_savingFix.value != null || _findingFixes.value || _findingAiFixes.value) return
        val pool = if (isAi) _aiSuggestions.value else _suggestions.value
        val suggestion = pool?.firstOrNull { it.track.id == trackId } ?: return
        viewModelScope.launch {
            _savingFix.value = trackId
            _fixFailed.value = false
            val resume = currentTrack.value?.id == trackId && isPlaying.value
            if (resume) playerController.pause()
            try {
                val current = trackRepository.getTrackById(trackId).first()
                if (current == null) {
                    _fixFailed.value = true
                    return@launch
                }
                val edit = buildTrackEdit(current, suggestion)
                when (val result = trackRepository.updateTrackDetails(trackId, edit)) {
                    is TrackEditResult.Success -> {
                        playerController.updateTrackInPlayback(result.track)
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
                if (resume && currentTrack.value?.id == trackId) playerController.resume()
                _savingFix.value = null
            }
        }
    }

    fun applyAllSuggestions(isAi: Boolean = false, onDone: (Int) -> Unit = {}) {
        val pool = (if (isAi) _aiSuggestions.value else _suggestions.value).orEmpty()
        if (pool.isEmpty() || _savingFix.value != null) return
        viewModelScope.launch {
            val appliedIds = mutableSetOf<String>()
            for (suggestion in pool) {
                val trackId = suggestion.track.id
                val current = trackRepository.getTrackById(trackId).first() ?: continue
                val edit = buildTrackEdit(current, suggestion)
                when (val result = trackRepository.updateTrackDetails(trackId, edit)) {
                    is TrackEditResult.Success -> {
                        playerController.updateTrackInPlayback(result.track)
                        appliedIds.add(trackId)
                    }
                    else -> Unit
                }
            }
            if (isAi) {
                _aiSuggestions.value = _aiSuggestions.value.orEmpty().filterNot { it.track.id in appliedIds }
            } else {
                _suggestions.value = _suggestions.value.orEmpty().filterNot { it.track.id in appliedIds }
            }
            onDone(appliedIds.size)
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
}
