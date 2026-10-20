package com.yasinjahantigh.logarhythm.domain.stats

/**
 * Single definition of what counts as a play, a skip, or noise. Kept pure so the scrobbler's
 * decisions can be tested without Android and so every consumer agrees on the thresholds.
 */
object ListenPolicy {
    const val PLAY_THRESHOLD_MAX_MS = 30_000L

    /** Anything shorter than this is a tap-through while browsing, not a taste signal. */
    const val MIN_SKIP_SIGNAL_MS = 1_500L

    enum class Outcome { PLAY, SKIP, IGNORE }

    /** A track counts as played once half of it, or 30 seconds, has been heard, whichever is less. */
    fun playThresholdMs(durationMs: Long): Long =
        if (durationMs > 0L) minOf(PLAY_THRESHOLD_MAX_MS, durationMs / 2) else PLAY_THRESHOLD_MAX_MS

    fun classify(listenedMs: Long, durationMs: Long): Outcome = when {
        listenedMs >= playThresholdMs(durationMs) -> Outcome.PLAY
        listenedMs >= MIN_SKIP_SIGNAL_MS -> Outcome.SKIP
        else -> Outcome.IGNORE
    }
}
