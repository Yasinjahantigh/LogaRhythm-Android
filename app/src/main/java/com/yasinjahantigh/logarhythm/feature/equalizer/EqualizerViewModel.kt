package com.yasinjahantigh.logarhythm.feature.equalizer

import androidx.lifecycle.ViewModel
import com.yasinjahantigh.logarhythm.playback.EqualizerState
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {

    val equalizerState: StateFlow<EqualizerState> = playerController.equalizerState

    fun setEqualizerEnabled(enabled: Boolean) {
        playerController.setEqualizerEnabled(enabled)
    }

    fun setPreset(presetIndex: Short) {
        playerController.setEqualizerPreset(presetIndex)
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        playerController.setEqualizerBandLevel(bandIndex, levelMb)
    }

    fun setBassBoostStrength(strength: Short) {
        playerController.setBassBoostStrength(strength)
    }

    fun setVirtualizerStrength(strength: Short) {
        playerController.setVirtualizerStrength(strength)
    }

    fun resetEqualizer() {
        playerController.resetEqualizer()
    }
}
