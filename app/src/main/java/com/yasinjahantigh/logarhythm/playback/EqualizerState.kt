package com.yasinjahantigh.logarhythm.playback

/**
 * Model representing an individual frequency band in the equalizer.
 */
data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val currentLevelMb: Short
)

/**
 * UI and playback state for the Equalizer.
 * currentPreset: -1 indicates custom mode, >= 0 is a built-in system preset index.
 */
data class EqualizerState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val currentPreset: Short = -1,
    val presets: List<String> = emptyList(),
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoostStrength: Short = 0,
    val virtualizerStrength: Short = 0,
    val isBassBoostSupported: Boolean = true,
    val isVirtualizerSupported: Boolean = true
)
