package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlin.math.abs

data class DuplicateGroup(val key: String, val tracks: List<Track>)

/** Library hygiene helpers; pure so they can be tested without Android. */
object DuplicateFinder {

    private const val DURATION_TOLERANCE_MS = 3_000L
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(value: String): String = value.lowercase().replace(NON_WORD, "")

    private fun isPlaceholder(value: String): Boolean =
        value.isBlank() || value.startsWith("Unknown ", ignoreCase = true)

    /**
     * Same normalised title and primary artist, with durations within 3 seconds of each other.
     * Tracks without a real title are never reported: filenames are not evidence.
     */
    fun find(tracks: List<Track>, separators: List<String> = ArtistSplitter.DEFAULT_SEPARATORS): List<DuplicateGroup> {
        val buckets = HashMap<String, MutableList<Track>>()
        for (track in tracks) {
            if (isPlaceholder(track.title)) continue
            val primaryArtist = ArtistSplitter.split(track.artist, separators).firstOrNull().orEmpty()
            val key = normalize(track.title) + "|" + normalize(primaryArtist)
            if (key.startsWith("|")) continue
            buckets.getOrPut(key) { mutableListOf() }.add(track)
        }
        val groups = mutableListOf<DuplicateGroup>()
        for ((key, bucket) in buckets) {
            if (bucket.size < 2) continue
            val sorted = bucket.sortedBy { it.durationMs }
            var cluster = mutableListOf(sorted.first())
            fun flush() {
                if (cluster.size >= 2) groups += DuplicateGroup(key, cluster.toList())
            }
            for (track in sorted.drop(1)) {
                if (abs(track.durationMs - cluster.first().durationMs) <= DURATION_TOLERANCE_MS) {
                    cluster.add(track)
                } else {
                    flush()
                    cluster = mutableListOf(track)
                }
            }
            flush()
        }
        return groups.sortedWith(compareByDescending<DuplicateGroup> { it.tracks.size }.thenBy { it.key })
    }

    /** Tracks whose title, artist or album tag is still a placeholder. */
    fun missingTags(tracks: List<Track>): List<Track> = tracks.filter {
        isPlaceholder(it.title) || isPlaceholder(it.artist) || isPlaceholder(it.album)
    }
}
