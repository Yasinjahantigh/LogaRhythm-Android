package com.yasinjahantigh.logarhythm.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController
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

    fun onConsentDenied() {
        _failure.value = TrackEditFailure.DENIED
    }

    fun clearFailure() {
        _failure.value = null
    }
}
