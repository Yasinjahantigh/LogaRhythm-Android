package com.yasinjahantigh.logarhythm.domain.session

import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlin.random.Random

/**
 * Builds a queue that fits the listener's available time using only the local library.
 * Familiar tracks are preferred; artist repetition is penalized to keep the session varied.
 */
object ListeningSessionPlanner {
    fun buildSession(
        tracks: List<Track>,
        targetDurationMs: Long,
        preferredTrackIds: Set<String> = emptySet(),
        seed: Long = System.currentTimeMillis()
    ): List<Track> {
        if (targetDurationMs <= 0L) return emptyList()

        val candidates = tracks
            .asSequence()
            .filter { it.id.isNotBlank() && it.uri.isNotBlank() && it.durationMs > 0L }
            .distinctBy { it.id }
            .toList()
        if (candidates.isEmpty()) return emptyList()

        val random = Random(seed)
        val remaining = candidates.shuffled(random).toMutableList()
        val session = mutableListOf<Track>()
        val artistCounts = mutableMapOf<String, Int>()
        var elapsedMs = 0L
        var previousArtist = ""

        while (remaining.isNotEmpty()) {
            val timeLeft = targetDurationMs - elapsedMs
            val fitting = remaining.filter { it.durationMs <= timeLeft }
            if (fitting.isEmpty()) break

            val next = fitting.minByOrNull { candidate ->
                val normalizedLeftover = (timeLeft - candidate.durationMs).toDouble() /
                    targetDurationMs.coerceAtLeast(1L).toDouble()
                val artist = candidate.artist.trim().lowercase()
                val repeats = artistCounts[artist] ?: 0
                val sameAsPrevious = artist.isNotBlank() && artist == previousArtist
                val preferenceBonus = if (candidate.id in preferredTrackIds) 0.12 else 0.0
                normalizedLeftover + (repeats * 0.10) + (if (sameAsPrevious) 0.20 else 0.0) -
                    preferenceBonus + random.nextDouble() * 0.08
            } ?: break

            session += next
            elapsedMs += next.durationMs
            remaining.remove(next)
            val artist = next.artist.trim().lowercase()
            if (artist.isNotBlank()) artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
            previousArtist = artist
        }

        return session
    }
}
