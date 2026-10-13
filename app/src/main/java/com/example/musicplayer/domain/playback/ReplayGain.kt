package com.example.musicplayer.domain.playback

import kotlin.math.pow

enum class ReplayGainMode { OFF, TRACK, ALBUM;
    companion object { fun from(value: String?) = entries.firstOrNull { it.name.equals(value, true) } ?: OFF }
}

/** ReplayGain helpers. The player can only attenuate, so positive gains are clamped to unity. */
object ReplayGain {
    private val NUMBER = Regex("[-+]?\\d+(?:[.,]\\d+)?")

    fun parseDb(value: String?): Float? =
        value?.let { NUMBER.find(it)?.value?.replace(',', '.')?.toFloatOrNull() }

    fun volumeFor(gainDb: Float?): Float =
        if (gainDb == null) 1f else 10f.pow(gainDb / 20f).coerceIn(0.05f, 1f)

    fun pick(mode: ReplayGainMode, trackGainDb: Float?, albumGainDb: Float?): Float? = when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.TRACK -> trackGainDb ?: albumGainDb
        ReplayGainMode.ALBUM -> albumGainDb ?: trackGainDb
    }
}
