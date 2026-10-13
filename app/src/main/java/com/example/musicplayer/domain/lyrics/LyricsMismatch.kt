package com.example.musicplayer.domain.lyrics

import com.example.musicplayer.domain.model.LyricLine

/** Detects synced lyrics whose timing cannot belong to a song of the given length. */
object LyricsMismatch {
    private const val OVERRUN_MS = 8_000L

    fun isLikely(lines: List<LyricLine>, durationMs: Long): Boolean {
        if (durationMs <= 0L || lines.size < 3) return false
        val first = lines.minOf { it.timestampMs }
        val last = lines.maxOf { it.timestampMs }
        if (last > durationMs + OVERRUN_MS) return true
        if (first > durationMs * 6 / 10) return true
        return lines.size >= 6 && durationMs > 120_000L && last < durationMs / 4
    }
}
