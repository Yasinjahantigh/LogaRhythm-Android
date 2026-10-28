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
import com.yasinjahantigh.logarhythm.data.download.metadata.MetadataResolver
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal data class TagFixSuggestion(val track: Track, val tags: TagSuggestionCleaner.Tags)

/** Duplicates, "missing tags", and hidden tracks views; computed off the main thread from the library. */
@HiltViewModel
class LibraryToolsViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    preferencesManager: PreferencesManager,
    private val playerController: PlayerController,
    private val metadataResolver: MetadataResolver
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
    private val _savingFix = MutableStateFlow<String?>(null)
    val savingFix = _savingFix.asStateFlow()
    private val _fixFailed = MutableStateFlow(false)
    val fixFailed = _fixFailed.asStateFlow()
    private var suggestionJob: Job? = null

    fun suggestFixes() {
        if (_findingFixes.value || _savingFix.value != null) return
        _suggestions.value = emptyList()
        _fixFailed.value = false
        suggestionJob = viewModelScope.launch {
            _findingFixes.value = true
            try {
                for (track in trackRepository.getAllTracks().first().filter { it.id.startsWith("local:") }) {
                    var tags = TagSuggestionCleaner.clean(track.title, track.artist, track.album)
                    val missingAlbum = track.album.isBlank() || track.album.equals("Unknown Album", true)
                    if (tags == TagSuggestionCleaner.Tags(track.title, track.artist, track.album) && !missingAlbum) continue
                    if (tags.canLookup) {
                        val resolved = metadataResolver.resolve(tags.title, tags.artist, track.durationMs, replaceArtist = true)
                        if (DownloadMetadataMerge.similarity(resolved.title.orEmpty(), tags.title) >= 0.9 &&
                            DownloadMetadataMerge.similarity(resolved.artist.orEmpty(), tags.artist) >= 0.9) {
                            tags = tags.copy(
                                title = resolved.title?.takeIf { it.isNotBlank() } ?: tags.title,
                                artist = resolved.artist?.takeIf { it.isNotBlank() } ?: tags.artist,
                                album = if (missingAlbum) resolved.album?.takeIf { it.isNotBlank() } ?: tags.album else tags.album
                            )
                        }
                    }
                    if (tags != TagSuggestionCleaner.Tags(track.title, track.artist, track.album)) {
                        _suggestions.value = _suggestions.value.orEmpty() + TagFixSuggestion(track, tags)
                    }
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

    fun dismissSuggestions() {
        if (_savingFix.value != null) return
        suggestionJob?.cancel()
        _suggestions.value = null
    }

    fun applySuggestion(trackId: String) {
        if (_savingFix.value != null || _findingFixes.value) return
        val suggestion = _suggestions.value?.firstOrNull { it.track.id == trackId } ?: return
        viewModelScope.launch {
            _savingFix.value = trackId
            _fixFailed.value = false
            val resume = currentTrack.value?.id == trackId && isPlaying.value
            if (resume) playerController.pause()
            try {
                val current = trackRepository.getTrackById(trackId).first()
                if (current == null || current.title != suggestion.track.title || current.artist != suggestion.track.artist || current.album != suggestion.track.album) {
                    _fixFailed.value = true
                    return@launch
                }
                val tags = suggestion.tags
                val edit = TrackEdit(tags.title, tags.artist, tags.album, current.albumArtist, current.genre,
                    current.year, current.trackNumber, current.discNumber, current.composer)
                when (val result = trackRepository.updateTrackDetails(trackId, edit)) {
                    is TrackEditResult.Success -> {
                        playerController.updateTrackInPlayback(result.track)
                        _suggestions.value = _suggestions.value.orEmpty().filterNot { it.track.id == trackId }
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
