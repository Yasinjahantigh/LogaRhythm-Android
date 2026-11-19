package com.yasinjahantigh.logarhythm.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.DeviceEqDao
import com.yasinjahantigh.logarhythm.data.room.DeviceEqProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val deviceEqDao: DeviceEqDao,
    private val stereoBalanceAudioProcessor: StereoBalanceAudioProcessor
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var currentAudioSessionId: Int = 0
    private var currentDeviceKey: String = "speaker"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = onOutputRouteChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = onOutputRouteChanged()
    }

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    init {
        runCatching { audioManager?.registerAudioDeviceCallback(deviceCallback, null) }
        val (initKey, initLabel) = detectOutputDevice()
        currentDeviceKey = initKey

        // Preload saved preferences into state before session binds
        scope.launch(Dispatchers.IO) {
            val enabled = preferencesManager.isEqualizerEnabled.first()
            val preset = preferencesManager.equalizerPreset.first().toShort()
            val savedBass = preferencesManager.bassBoostStrength.first().toShort()
            val savedVirt = preferencesManager.virtualizerStrength.first().toShort()
            val balance = preferencesManager.stereoBalance.first()
            val mono = preferencesManager.monoAudio.first()
            val autoEq = preferencesManager.deviceAutoEqEnabled.first()
            val reverb = preferencesManager.slowedReverbEnabled.first()
            stereoBalanceAudioProcessor.setStereoBalance(balance)
            stereoBalanceAudioProcessor.setMonoAudio(mono)
            _equalizerState.update {
                it.copy(
                    isEnabled = enabled,
                    currentPreset = preset,
                    bassBoostStrength = savedBass,
                    virtualizerStrength = savedVirt,
                    stereoBalance = balance,
                    monoAudio = mono,
                    deviceAutoEqEnabled = autoEq,
                    activeDeviceLabel = initLabel,
                    reverbEnabled = reverb
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

            if (_equalizerState.value.reverbEnabled) {
                attachReverbLocked(audioSessionId)
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
        setStereoBalance(0f)
        setMonoAudio(false)
    }

    fun setStereoBalance(balance: Float) {
        val clamped = balance.coerceIn(-1f, 1f)
        stereoBalanceAudioProcessor.setStereoBalance(clamped)
        _equalizerState.update { it.copy(stereoBalance = clamped) }
        scope.launch(Dispatchers.IO) { preferencesManager.setStereoBalance(clamped) }
    }

    fun setMonoAudio(enabled: Boolean) {
        stereoBalanceAudioProcessor.setMonoAudio(enabled)
        _equalizerState.update { it.copy(monoAudio = enabled) }
        scope.launch(Dispatchers.IO) { preferencesManager.setMonoAudio(enabled) }
    }

    private fun attachReverbLocked(audioSessionId: Int) {
        if (audioSessionId == 0) return
        try {
            presetReverb?.release()
            val rev = PresetReverb(0, audioSessionId)
            rev.preset = PresetReverb.PRESET_LARGEROOM
            rev.enabled = true
            presetReverb = rev
        } catch (e: Exception) {
            Log.w("EqualizerManager", "PresetReverb not supported on session $audioSessionId: ${e.message}")
            runCatching { presetReverb?.release() }
            presetReverb = null
        }
    }

    fun setReverbEnabled(enabled: Boolean) {
        if (_equalizerState.value.reverbEnabled == enabled && (!enabled || presetReverb != null)) {
            return
        }
        if (enabled) {
            val existing = presetReverb
            if (existing != null) {
                runCatching {
                    existing.preset = PresetReverb.PRESET_LARGEROOM
                    existing.enabled = true
                }.onFailure {
                    runCatching { existing.release() }
                    presetReverb = null
                }
            } else if (currentAudioSessionId != 0) {
                attachReverbLocked(currentAudioSessionId)
            }
        } else {
            runCatching {
                presetReverb?.enabled = false
                presetReverb?.release()
            }
            presetReverb = null
        }
        _equalizerState.update { it.copy(reverbEnabled = enabled) }
    }

    fun setDeviceAutoEqEnabled(enabled: Boolean) {
        _equalizerState.update { it.copy(deviceAutoEqEnabled = enabled) }
        scope.launch(Dispatchers.IO) {
            preferencesManager.setDeviceAutoEqEnabled(enabled)
            if (enabled) saveCurrentProfileForDevice(currentDeviceKey, _equalizerState.value.activeDeviceLabel)
        }
    }

    private fun onOutputRouteChanged() {
        val (newKey, newLabel) = detectOutputDevice()
        val prevKey = currentDeviceKey
        if (newKey == prevKey) return
        currentDeviceKey = newKey
        _equalizerState.update { it.copy(activeDeviceLabel = newLabel) }

        if (_equalizerState.value.deviceAutoEqEnabled) {
            scope.launch(Dispatchers.IO) {
                saveCurrentProfileForDevice(prevKey, prevKey)
                val profile = deviceEqDao.getByKey(newKey) ?: return@launch
                applyProfileOnMain(profile)
            }
        }
    }

    private suspend fun saveCurrentProfileForDevice(deviceKey: String, label: String) {
        val st = _equalizerState.value
        val csv = st.bands.sortedBy { it.index }.joinToString(",") { it.currentLevelMb.toString() }
        deviceEqDao.upsert(
            DeviceEqProfileEntity(
                deviceKey = deviceKey,
                deviceLabel = label,
                enabled = st.isEnabled,
                presetIndex = st.currentPreset.toInt(),
                bandLevelsCsv = csv,
                bassStrength = st.bassBoostStrength.toInt(),
                virtualizerStrength = st.virtualizerStrength.toInt(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun applyProfileOnMain(profile: DeviceEqProfileEntity) {
        scope.launch(Dispatchers.Main) {
            setEqualizerEnabled(profile.enabled)
            val eq = equalizer
            if (eq != null) {
                val levels = profile.bandLevelsCsv.split(',').mapNotNull { it.toShortOrNull() }
                if (profile.presetIndex >= 0) {
                    setPreset(profile.presetIndex.toShort())
                } else if (levels.isNotEmpty()) {
                    levels.forEachIndexed { idx, mb -> setBandLevel(idx.toShort(), mb) }
                }
            }
            setBassBoostStrength(profile.bassStrength.toShort())
            setVirtualizerStrength(profile.virtualizerStrength.toShort())
        }
    }

    private fun detectOutputDevice(): Pair<String, String> {
        val outputs = runCatching { audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull()
            ?: return "speaker" to "Speaker"
        for (info in outputs) {
            when (info.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    val name = info.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                    return "bt:${name.lowercase()}" to name
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    val name = info.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
                    return "wired" to name
                }
            }
        }
        return "speaker" to "Speaker"
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
            if (state.deviceAutoEqEnabled) {
                saveCurrentProfileForDevice(currentDeviceKey, state.activeDeviceLabel)
            }
        }
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        try { presetReverb?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        presetReverb = null
    }

    fun release() {
        runCatching { audioManager?.unregisterAudioDeviceCallback(deviceCallback) }
        releaseEffects()
        currentAudioSessionId = 0
    }
}
