package com.yasinjahantigh.logarhythm.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.data.ai.AiSettings
import com.yasinjahantigh.logarhythm.domain.model.AiFailure
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylist
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylistResult
import com.yasinjahantigh.logarhythm.domain.repository.AiPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MagicPlaylistUiState(
    val prompt: String = "",
    val trackCount: Int = 20,
    val isGenerating: Boolean = false,
    val failure: AiFailure? = null,
    val result: MagicPlaylist? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class MagicPlaylistViewModel @Inject constructor(
    private val repository: AiPlaylistRepository
) : ViewModel() {

    val settings: StateFlow<AiSettings> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettings())

    private val _state = MutableStateFlow(MagicPlaylistUiState())
    val state: StateFlow<MagicPlaylistUiState> = _state.asStateFlow()

    fun setPrompt(prompt: String) {
        _state.update { it.copy(prompt = prompt, failure = null) }
    }

    fun setTrackCount(count: Int) {
        _state.update { it.copy(trackCount = count.coerceIn(MIN_TRACKS, MAX_TRACKS)) }
    }

    fun generate() {
        val current = _state.value
        if (current.isGenerating || current.prompt.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, failure = null, result = null) }
            try {
                when (val result = repository.generatePlaylist(current.prompt, current.trackCount)) {
                    is MagicPlaylistResult.Success -> _state.update {
                        it.copy(isGenerating = false, result = result.playlist)
                    }

                    is MagicPlaylistResult.Failure -> _state.update {
                        it.copy(isGenerating = false, failure = result.reason)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isGenerating = false, failure = AiFailure.SERVER) }
            }
        }
    }

    fun save(name: String, onSaved: () -> Unit) {
        val tracks = _state.value.result?.tracks ?: return
        if (name.isBlank() || tracks.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val id = repository.savePlaylist(name, tracks.map { it.id })
            _state.update { it.copy(isSaving = false) }
            if (id > 0) onSaved()
        }
    }

    fun reset() {
        _state.value = MagicPlaylistUiState()
    }

    companion object {
        const val MIN_TRACKS = 5
        const val MAX_TRACKS = 50
    }
}
