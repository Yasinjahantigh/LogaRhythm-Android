package com.yasinjahantigh.logarhythm.domain.stats

/**
 * Single definition of what counts as a play, a skip, or noise. Kept pure so the scrobbler's
 * decisions can be tested without Android and so every consumer agrees on the thresholds.
 */
object ListenPolicy {
    /** Anything shorter than this is a tap-through while browsing, not a taste signal. */
    const val MIN_SKIP_SIGNAL_MS = 1_500L

    enum class Outcome { PLAY, SKIP, IGNORE }

    /** A track counts as played once half of it has been heard (or 30 seconds if duration is unknown). */
    fun playThresholdMs(durationMs: Long): Long =
        if (durationMs > 0L) durationMs / 2 else 30_000L

    fun classify(listenedMs: Long, durationMs: Long): Outcome = when {
        listenedMs >= playThresholdMs(durationMs) -> Outcome.PLAY
        listenedMs >= MIN_SKIP_SIGNAL_MS -> Outcome.SKIP
        else -> Outcome.IGNORE
    }
}
