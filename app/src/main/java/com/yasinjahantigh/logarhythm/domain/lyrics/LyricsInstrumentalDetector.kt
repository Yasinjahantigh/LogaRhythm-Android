package com.yasinjahantigh.logarhythm.domain.lyrics

import com.yasinjahantigh.logarhythm.domain.model.LyricLine

/**
 * Represents a live instrumental break currently happening at playback position.
 * [slotIndex] == -1 means an instrumental intro before line 0;
 * [slotIndex] >= 0 means an instrumental interlude immediately following [slotIndex]
 * (or occupying [slotIndex] if that LRC line itself is an explicit blank/instrumental marker).
 */
data class ActiveInstrumentalGap(
    val slotIndex: Int,
    val progress: Float
)

/**
 * Pure detector for instrumental breaks in synced lyrics.
 * Only reports a gap for the exact live playback moment — never for past or future gaps —
 * so scrolling backward or forward leaves zero visual trace.
 */
object LyricsInstrumentalDetector {

    private const val MIN_INTRO_GAP_MS = 4_500L
    private const val MIN_BETWEEN_LINES_GAP_MS = 5_500L
    private const val MIN_VISIBLE_BREAK_MS = 2_000L
    private const val EXIT_LEAD_MS = 350L

    private val INSTRUMENTAL_LABELS = setOf(
        "instrumental", "music", "interlude", "solo", "intro", "outro",
        "بی کلام", "بیکلام", "موسیقی"
    )
    private val ONLY_SYMBOLS = Regex("^[\\s♪♫♬🎵🎶•·\\.\\-–—~\\*\\(\\)\\[\\]]+$")

    fun isInstrumentalMarker(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (ONLY_SYMBOLS.matches(trimmed)) return true
        val stripped = trimmed
            .trim('[', ']', '(', ')', '♪', '♫', '♬', ' ', '-', '–', '—')
            .replace('\u200c', ' ')
            .lowercase()
        return stripped in INSTRUMENTAL_LABELS
    }

    fun findActiveGap(
        lines: List<LyricLine>,
        activeIndex: Int,
        effectivePosMs: Long
    ): ActiveInstrumentalGap? {
        if (lines.isEmpty() || effectivePosMs < 0L) return null

        // 1. Instrumental intro before the very first vocal line
        val firstTs = lines.first().timestampMs
        if (activeIndex < 0 && firstTs >= MIN_INTRO_GAP_MS) {
            val start = 300L
            val end = firstTs - EXIT_LEAD_MS
            if (effectivePosMs in start..end && end > start) {
                val p = ((effectivePosMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                return ActiveInstrumentalGap(slotIndex = -1, progress = p)
            }
            return null
        }

        if (activeIndex !in lines.indices) return null
        val current = lines[activeIndex]
        val next = lines.getOrNull(activeIndex + 1)

        // 2. Explicit blank or instrumental-marker LRC line
        if (isInstrumentalMarker(current.text)) {
            val start = current.timestampMs
            val end = (next?.timestampMs?.minus(EXIT_LEAD_MS)) ?: (start + 10_000L)
            if (effectivePosMs in start..end && end > start) {
                val p = ((effectivePosMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                return ActiveInstrumentalGap(slotIndex = activeIndex, progress = p)
            }
            return null
        }

        // 3. Natural instrumental gap between two vocal lines (e.g. 10s..20s music break)
        if (next == null) return null
        val gapMs = next.timestampMs - current.timestampMs
        if (gapMs < MIN_BETWEEN_LINES_GAP_MS) return null

        val estimatedSingEnd = if (current.words.isNotEmpty()) {
            (current.words.last().startMs + 1_300L)
                .coerceIn(current.timestampMs + 1_800L, next.timestampMs - MIN_VISIBLE_BREAK_MS - EXIT_LEAD_MS)
        } else {
            val estDuration = (1_700L + current.text.length * 60L).coerceIn(2_300L, 4_000L)
            (current.timestampMs + estDuration).coerceAtMost(next.timestampMs - MIN_VISIBLE_BREAK_MS - EXIT_LEAD_MS)
        }

        val breakEnd = next.timestampMs - EXIT_LEAD_MS
        if (breakEnd - estimatedSingEnd < MIN_VISIBLE_BREAK_MS) return null

        if (effectivePosMs in estimatedSingEnd..breakEnd) {
            val p = ((effectivePosMs - estimatedSingEnd).toFloat() / (breakEnd - estimatedSingEnd).toFloat()).coerceIn(0f, 1f)
            return ActiveInstrumentalGap(slotIndex = activeIndex, progress = p)
        }

        return null
    }
}
