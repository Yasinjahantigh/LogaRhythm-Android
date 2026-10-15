package com.example.musicplayer.domain.playback

import kotlin.math.pow

enum class ReplayGainMode { OFF, TRACK, ALBUM;
    companion object { fun from(value: String?) = entries.firstOrNull { it.name.equals(value, true) } ?: OFF }
}

/**
 * ReplayGain helpers.
 *
 * Two output stages: attenuation rides on `ExoPlayer.volume` (always safe), while positive gain
 * (quiet tracks that need boosting) rides on `LoudnessEnhancer` in milli-bels, capped so a
 * broken tag can never blow the speaker. The old code clamped everything to unity, which is why
 * leveling "did nothing" for exactly the quiet songs that needed it most.
 */
object ReplayGain {
    private val NUMBER = Regex("[-+]?\\d+(?:[.,]\\d+)?")

    /** Max boost the DSP stage may apply (dB). Attenuation is uncapped below. */
    const val MAX_BOOST_DB = 6f

    fun parseDb(value: String?): Float? =
        value?.let { NUMBER.find(it)?.value?.replace(',', '.')?.toFloatOrNull() }

    /** R128_*_GAIN tags are stored in 1/256 dB units (Opus-style); convert to dB. */
    fun parseR128(value: String?): Float? =
        value?.let { NUMBER.find(it)?.value?.replace(',', '.')?.toFloatOrNull()?.div(256f) }

    /** Legacy attenuation-only mapping, kept for the `player.volume` stage and old tests. */
    fun volumeFor(gainDb: Float?): Float =
        if (gainDb == null) 1f else 10f.pow(gainDb / 20f).coerceIn(0.05f, 1f)

    fun pick(mode: ReplayGainMode, trackGainDb: Float?, albumGainDb: Float?): Float? = when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.TRACK -> trackGainDb ?: albumGainDb
        ReplayGainMode.ALBUM -> albumGainDb ?: trackGainDb
    }

    /**
     * Splits the effective gain (picked tag + user preamp) into the two output stages.
     * @return volume factor in [0.05, 1] for `player.volume`, and boost in milli-bels [0, 600]
     * for `LoudnessEnhancer.setTargetGain`.
     */
    fun splitForOutput(pickedGainDb: Float?, preampDb: Float): Pair<Float, Int> {
        val total = (pickedGainDb ?: 0f) + preampDb
        val volume = 10f.pow(minOf(total, 0f) / 20f).coerceIn(0.05f, 1f)
        val boostMb = (maxOf(total, 0f).coerceIn(0f, MAX_BOOST_DB) * 100f).toInt()
        return volume to boostMb
    }
}
