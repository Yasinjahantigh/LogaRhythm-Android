package com.example.musicplayer.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.example.musicplayer.data.datastore.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("DEPRECATION")
class EqualizerManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentAudioSessionId: Int = 0

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    init {
        // Preload saved preferences into state before session binds
        scope.launch(Dispatchers.IO) {
            val enabled = preferencesManager.isEqualizerEnabled.first()
            val preset = preferencesManager.equalizerPreset.first().toShort()
            val savedBass = preferencesManager.bassBoostStrength.first().toShort()
            val savedVirt = preferencesManager.virtualizerStrength.first().toShort()
            _equalizerState.update {
                it.copy(
                    isEnabled = enabled,
                    currentPreset = preset,
                    bassBoostStrength = savedBass,
                    virtualizerStrength = savedVirt
                )
            }
        }
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (currentAudioSessionId == audioSessionId && equalizer != null) return

        currentAudioSessionId = audioSessionId
        try {
            releaseEffects()

            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            initEqualizer(eq)

            try {
                val bb = BassBoost(0, audioSessionId)
                bassBoost = bb
            } catch (e: Exception) {
                Log.w("EqualizerManager", "BassBoost not supported: ${e.message}")
                _equalizerState.update { it.copy(isBassBoostSupported = false) }
            }

            try {
                val virt = Virtualizer(0, audioSessionId)
                virtualizer = virt
            } catch (e: Exception) {
                Log.w("EqualizerManager", "Virtualizer not supported: ${e.message}")
                _equalizerState.update { it.copy(isVirtualizerSupported = false) }
            }
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Equalizer not supported on this device/session: ${e.message}")
            _equalizerState.update { it.copy(isSupported = false) }
        }
    }

    private fun initEqualizer(eq: Equalizer) {
        try {
            val numBands = eq.numberOfBands
            val levelRange = eq.bandLevelRange
            val minLevel = levelRange[0]
            val maxLevel = levelRange[1]

            val presets = mutableListOf<String>()
            val numPresets = eq.numberOfPresets
            for (p in 0 until numPresets) {
                presets.add(eq.getPresetName(p.toShort()))
            }

            scope.launch {
                val savedEnabled = preferencesManager.isEqualizerEnabled.first()
                val savedPreset = preferencesManager.equalizerPreset.first().toShort()
                val savedLevels = preferencesManager.equalizerBandLevels.first()
                val savedBass = preferencesManager.bassBoostStrength.first().toShort()
                val savedVirt = preferencesManager.virtualizerStrength.first().toShort()

                try {
                    eq.enabled = savedEnabled
                    if (savedPreset in 0 until numPresets) {
                        eq.usePreset(savedPreset)
                    } else if (savedLevels.isNotEmpty()) {
                        savedLevels.forEach { (band, level) ->
                            if (band.toShort() in 0 until numBands) {
                                eq.setBandLevel(band.toShort(), level.toShort().coerceIn(minLevel, maxLevel))
                            }
                        }
                    }

                    bassBoost?.let {
                        it.enabled = savedEnabled
                        it.setStrength(savedBass)
                    }

                    virtualizer?.let {
                        it.enabled = savedEnabled
                        it.setStrength(savedVirt)
                    }
                } catch (e: Exception) {
                    Log.w("EqualizerManager", "Error restoring equalizer settings: ${e.message}")
                }

                val currentBands = (0 until numBands).map { bandIdx ->
                    val band = bandIdx.toShort()
                    val centerFreqHz = eq.getCenterFreq(band) / 1000
                    val currentLevel = eq.getBandLevel(band)
                    EqualizerBand(band, centerFreqHz, minLevel, maxLevel, currentLevel)
                }

                _equalizerState.update {
                    it.copy(
                        isSupported = true,
                        isEnabled = eq.enabled,
                        currentPreset = savedPreset,
                        presets = presets,
                        bands = currentBands,
                        bassBoostStrength = savedBass,
                        virtualizerStrength = savedVirt
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Failed to query Equalizer capabilities: ${e.message}")
            _equalizerState.update { it.copy(isSupported = false) }
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Failed to set equalizer enabled: ${e.message}")
        }
        _equalizerState.update { it.copy(isEnabled = enabled) }
        persistSettings()
    }

    fun setPreset(presetIndex: Short) {
        val eq = equalizer
        if (eq != null && presetIndex >= 0) {
            try {
                eq.usePreset(presetIndex)
                val updatedBands = _equalizerState.value.bands.map { band ->
                    band.copy(currentLevelMb = eq.getBandLevel(band.index))
                }
                _equalizerState.update { it.copy(currentPreset = presetIndex, bands = updatedBands) }
            } catch (e: Exception) {
                Log.w("EqualizerManager", "Failed to apply preset: ${e.message}")
            }
        } else {
            _equalizerState.update { it.copy(currentPreset = -1) }
        }
        persistSettings()
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer
        val clampedLevel = if (_equalizerState.value.bands.isNotEmpty()) {
            val band = _equalizerState.value.bands.getOrNull(bandIndex.toInt())
            if (band != null) levelMb.coerceIn(band.minLevelMb, band.maxLevelMb) else levelMb
        } else levelMb

        try {
            eq?.setBandLevel(bandIndex, clampedLevel)
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Failed to set band level: ${e.message}")
        }

        val updatedBands = _equalizerState.value.bands.map {
            if (it.index == bandIndex) it.copy(currentLevelMb = clampedLevel) else it
        }
        _equalizerState.update { it.copy(currentPreset = -1, bands = updatedBands) }
        persistSettings()
    }

    fun setBassBoostStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            bassBoost?.setStrength(clamped)
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Failed to set BassBoost: ${e.message}")
        }
        _equalizerState.update { it.copy(bassBoostStrength = clamped) }
        persistSettings()
    }

    fun setVirtualizerStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        try {
            virtualizer?.setStrength(clamped)
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Failed to set Virtualizer: ${e.message}")
        }
        _equalizerState.update { it.copy(virtualizerStrength = clamped) }
        persistSettings()
    }

    fun resetEqualizer() {
        _equalizerState.value.bands.forEach { band ->
            setBandLevel(band.index, 0)
        }
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
    }

    private fun persistSettings() {
        val state = _equalizerState.value
        val levels = state.bands.associate { it.index.toInt() to it.currentLevelMb.toInt() }
        scope.launch(Dispatchers.IO) {
            preferencesManager.saveEqualizerSettings(
                enabled = state.isEnabled,
                preset = state.currentPreset.toInt(),
                bandLevels = levels,
                bassBoost = state.bassBoostStrength.toInt(),
                virtualizer = state.virtualizerStrength.toInt()
            )
        }
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun release() {
        releaseEffects()
        currentAudioSessionId = 0
    }
}
