package com.yasinjahantigh.logarhythm.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.download.metadata.MetadataResolver
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditFailure
import com.yasinjahantigh.logarhythm.domain.model.TrackEditResult
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.metadata.ItunesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class CoverCandidate(val url: String, val title: String, val artist: String)

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController,
    private val metadataResolver: MetadataResolver,
    private val itunesClient: ItunesClient,
    private val deezerClient: DeezerClient,
    private val httpClient: OkHttpClient
) : ViewModel() {

    val trackId: String = Uri.decode(savedStateHandle.get<String>("trackId").orEmpty())

    val track: StateFlow<Track?> = trackRepository.getTrackById(trackId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _failure = MutableStateFlow<TrackEditFailure?>(null)
    val failure: StateFlow<TrackEditFailure?> = _failure.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    private val _isFilling = MutableStateFlow(false)
    val isFilling: StateFlow<Boolean> = _isFilling.asStateFlow()

    private val suggestionChannel = Channel<MetadataResolver.Resolved?>(Channel.BUFFERED)
    val suggestions: Flow<MetadataResolver.Resolved?> = suggestionChannel.receiveAsFlow()

    fun autoFill() {
        val current = track.value ?: return
        if (_isFilling.value || !current.id.startsWith("local:")) return
        viewModelScope.launch {
            _isFilling.value = true
            val resolved = try {
                metadataResolver.resolve(
                    title = current.title,
                    artist = current.artist,
                    durationMs = current.durationMs,
                    replaceArtist = true
                )
            } catch (_: Exception) {
                null
            }
            suggestionChannel.trySend(resolved)
            _isFilling.value = false
        }
    }

    fun save(edit: TrackEdit) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _failure.value = null
            val wasCurrent = playerController.currentTrack.value?.id == trackId
            val wasPlaying = wasCurrent && playerController.isPlaying.value
            if (wasPlaying) playerController.pause()
            try {
                when (val result = trackRepository.updateTrackDetails(trackId, edit)) {
                    is TrackEditResult.Success -> {
                        playerController.updateTrackInPlayback(result.track)
                        savedChannel.trySend(Unit)
                    }
                    is TrackEditResult.Failure -> _failure.value = result.failure
                }
            } finally {
                if (wasPlaying) playerController.resume()
                _isSaving.value = false
            }
        }
    }

    private val _coverCandidates = MutableStateFlow<List<CoverCandidate>?>(null)
    val coverCandidates: StateFlow<List<CoverCandidate>?> = _coverCandidates.asStateFlow()
    private val _findingCovers = MutableStateFlow(false)
    val findingCovers: StateFlow<Boolean> = _findingCovers.asStateFlow()
    private val _selectedCover = MutableStateFlow<Bitmap?>(null)
    val selectedCover: StateFlow<Bitmap?> = _selectedCover.asStateFlow()

    fun findCovers() {
        val current = track.value ?: return
        if (_findingCovers.value) return
        viewModelScope.launch {
            _findingCovers.value = true
            _coverCandidates.value = emptyList()
            val title = current.title
            val artist = current.artist
            val itunes = runCatching { itunesClient.searchCandidates(title, artist) }.getOrDefault(emptyList())
                .map { CoverCandidate(it.url, it.title, it.artist) }
            val deezer = runCatching { deezerClient.searchCandidates(title, artist) }.getOrDefault(emptyList())
                .map { CoverCandidate(it.url, it.title, it.artist) }
            _coverCandidates.value = (itunes + deezer)
                .distinctBy { it.url }
                .take(10)
            _findingCovers.value = false
        }
    }

    fun dismissCovers() {
        _coverCandidates.value = null
    }

    fun chooseCover(url: String) {
        if (_selectedCover.value != null) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val bytes = response.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }.getOrNull()
            }
            if (bitmap != null) {
                _selectedCover.value = bitmap
                _coverCandidates.value = null
            }
        }
    }

    fun clearSelectedCover() {
        _selectedCover.value = null
    }

    fun onConsentDenied() {
        _failure.value = TrackEditFailure.DENIED
    }

    fun clearFailure() {
        _failure.value = null
    }
}
