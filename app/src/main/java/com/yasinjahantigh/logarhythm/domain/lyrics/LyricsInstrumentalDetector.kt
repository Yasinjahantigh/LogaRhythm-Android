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

    // Show instrumental/silence animations only when the silent break exceeds 4 seconds.
    private const val MIN_SILENCE_MS = 4_000L
    private const val INTRO_START_MS = 300L
    private const val EXIT_LEAD_MS = 350L
    private const val MIN_INTRO_GAP_MS = INTRO_START_MS + MIN_SILENCE_MS + EXIT_LEAD_MS + 1L

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
        effectivePosMs: Long,
        trackDurationMs: Long = 0L
    ): ActiveInstrumentalGap? {
        if (lines.isEmpty() || effectivePosMs < 0L) return null

        // 1. Instrumental intro before the very first vocal line (> 4s visible silence)
        val firstTs = lines.first().timestampMs
        if (activeIndex < 0 && firstTs >= MIN_INTRO_GAP_MS) {
            val start = INTRO_START_MS
            val end = firstTs - EXIT_LEAD_MS
            if ((end - start) > MIN_SILENCE_MS && effectivePosMs in start..end) {
                val p = ((effectivePosMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                return ActiveInstrumentalGap(slotIndex = -1, progress = p)
            }
            return null
        }

        if (activeIndex !in lines.indices) return null
        val current = lines[activeIndex]
        val next = lines.getOrNull(activeIndex + 1)

        // 2. Explicit blank or instrumental-marker LRC line (only when > 4s long)
        if (isInstrumentalMarker(current.text)) {
            val start = current.timestampMs
            val end = (next?.timestampMs?.minus(EXIT_LEAD_MS))
                ?: if (trackDurationMs > 0L) (trackDurationMs - EXIT_LEAD_MS) else (start + 10_000L)
            if ((end - start) > MIN_SILENCE_MS && effectivePosMs in start..end) {
                val p = ((effectivePosMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                return ActiveInstrumentalGap(slotIndex = activeIndex, progress = p)
            }
            return null
        }

        // 3. Instrumental outro after the final vocal line when trackDurationMs is known
        if (next == null) {
            if (trackDurationMs <= 0L) return null
            val estSingEnd = if (current.words.isNotEmpty()) {
                (current.words.last().startMs + 1_300L).coerceAtLeast(current.timestampMs + 1_800L)
            } else {
                val estDuration = (1_700L + current.text.length * 60L).coerceIn(2_300L, 4_000L)
                current.timestampMs + estDuration
            }
            val outroEnd = (trackDurationMs - EXIT_LEAD_MS).coerceAtLeast(estSingEnd)
            if ((outroEnd - estSingEnd) <= MIN_SILENCE_MS) return null
            if (effectivePosMs in estSingEnd..outroEnd) {
                val p = ((effectivePosMs - estSingEnd).toFloat() / (outroEnd - estSingEnd).toFloat()).coerceIn(0f, 1f)
                return ActiveInstrumentalGap(slotIndex = -2, progress = p)
            }
            return null
        }

        // 4. Natural instrumental gap between two vocal lines (must leave > 4s of real silence)
        val gapMs = next.timestampMs - current.timestampMs
        if (gapMs <= MIN_SILENCE_MS) return null

        val estimatedSingEnd = if (current.words.isNotEmpty()) {
            (current.words.last().startMs + 1_300L).coerceAtLeast(current.timestampMs + 1_800L)
        } else {
            val estDuration = (1_700L + current.text.length * 60L).coerceIn(2_300L, 4_000L)
            current.timestampMs + estDuration
        }

        val breakEnd = next.timestampMs - EXIT_LEAD_MS
        if ((breakEnd - estimatedSingEnd) <= MIN_SILENCE_MS) return null

        if (effectivePosMs in estimatedSingEnd..breakEnd) {
            val p = ((effectivePosMs - estimatedSingEnd).toFloat() / (breakEnd - estimatedSingEnd).toFloat()).coerceIn(0f, 1f)
            return ActiveInstrumentalGap(slotIndex = activeIndex, progress = p)
        }

        return null
    }
}
