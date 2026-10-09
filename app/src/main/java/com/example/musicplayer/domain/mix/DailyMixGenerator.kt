package com.example.musicplayer.domain.mix

import com.example.musicplayer.domain.model.DailyMix
import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.Track
import kotlin.random.Random

/**
 * Offline, stable-for-the-day personalized mixes. Each mix has a distinct intent:
 * familiar favorites, rediscovery, and lower-played discoveries.
 */
class DailyMixGenerator {
    companion object {
        const val MIX_SIZE = 15
        const val NUM_MIXES = 3
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_STALE_DAYS = 120L
    }

    fun generateMixes(
        allTracks: List<Track>,
        playEvents: List<PlayEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        seedDate: Long = System.currentTimeMillis() / DAY_MS
    ): List<DailyMix> {
        val library = allTracks.distinctBy { it.id }
        if (library.isEmpty()) return emptyList()

        // Skips are not plays: they must not inflate familiarity, and they count against a track.
        val completedEvents = playEvents.filter { !it.skipped }
        val skipCounts = playEvents.filter { it.skipped }.groupingBy { it.trackId }.eachCount()
        val playCounts = completedEvents.groupingBy { it.trackId }.eachCount()
        val lastPlayed = completedEvents.groupBy { it.trackId }
            .mapValues { (_, events) -> events.maxOfOrNull { it.timestamp } ?: 0L }
        // Anchor time-based scoring to the supplied day so a mix cannot reshuffle mid-day.
        val snapshotTimeMs = seedDate * DAY_MS + DAY_MS / 2

        // Mix 1: familiar music, with favorites boosted and random tie ordering for daily variation.
        val familiarRanked = library.shuffled(Random(seedDate * 31L + 1L)).sortedByDescending { track ->
            (playCounts[track.id] ?: 0) * 2.0 + (if (track.id in favoriteTrackIds) 6.0 else 0.0) -
                (skipCounts[track.id] ?: 0) * 2.5
        }
        val familiar = diversifyByArtist(familiarRanked.take(MIX_SIZE), Random(seedDate * 31L + 11L))

        // Mix 2: favorites that have gone quiet, forgotten tracks, and music not heard yet.
        val familiarIds = familiar.mapTo(mutableSetOf()) { it.id }
        fun rediscoveryScore(track: Track): Double {
            val count = playCounts[track.id] ?: 0
            val lastTime = lastPlayed[track.id] ?: 0L
            val staleDays = if (lastTime <= 0L) MAX_STALE_DAYS else
                ((snapshotTimeMs - lastTime).coerceAtLeast(0L) / DAY_MS).coerceAtMost(MAX_STALE_DAYS)
            val neverPlayedBonus = if (count == 0) 8.0 else 0.0
            return staleDays / 8.0 + neverPlayedBonus +
                (if (track.id in favoriteTrackIds) 7.0 else 0.0) - count * 1.3 -
                (skipCounts[track.id] ?: 0) * 1.5
        }
        // Prefer tracks not already in the familiar mix; only overlap when the library is small.
        val rediscoveryUnique = library.filter { it.id !in familiarIds }
            .shuffled(Random(seedDate * 31L + 2L)).sortedByDescending(::rediscoveryScore)
        val rediscoveryFill = library.filter { it.id in familiarIds }
            .shuffled(Random(seedDate * 31L + 22L)).sortedByDescending(::rediscoveryScore)
        val rediscoveryRanked = rediscoveryUnique + rediscoveryFill
        val rediscovery = diversifyByArtist(rediscoveryRanked.take(MIX_SIZE), Random(seedDate * 31L + 22L))

        // Mix 3: a deliberate change of pace, prioritizing tracks absent from the first two mixes.
        val alreadyFeatured = (familiar + rediscovery).mapTo(mutableSetOf()) { it.id }
        val lowPlayedRanked = library
            .filter { it.id !in alreadyFeatured }
            .shuffled(Random(seedDate * 31L + 3L))
            .sortedBy { playCounts[it.id] ?: 0 }
            .toMutableList()
        if (lowPlayedRanked.size < MIX_SIZE) {
            val fillIds = lowPlayedRanked.mapTo(mutableSetOf()) { it.id }
            val additional = library
                .filter { it.id !in fillIds }
                .shuffled(Random(seedDate * 31L + 33L))
                .sortedBy { playCounts[it.id] ?: 0 }
            lowPlayedRanked.addAll(additional.take(MIX_SIZE - lowPlayedRanked.size))
        }
        val discovery = diversifyByArtist(lowPlayedRanked.take(MIX_SIZE), Random(seedDate * 31L + 33L))

        val sets = listOf(familiar, rediscovery, discovery)
        val titles = listOf("In Your Rotation", "Time to Revisit", "Off the Beaten Track")
        val descriptions = listOf(
            "Familiar tracks and favorites",
            "Music worth hearing again",
            "Less-played corners of your library"
        )

        return sets.mapIndexed { index, selected ->
            val artists = selected.map { it.artist.trim() }
                .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                .distinct()
                .take(3)
            DailyMix(
                id = "daily_mix_${index + 1}",
                title = titles[index],
                description = artists.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: descriptions[index],
                tracks = selected,
                seedDate = seedDate
            )
        }
    }

    private fun diversifyByArtist(tracks: List<Track>, random: Random): List<Track> {
        if (tracks.size < 2) return tracks
        val buckets = tracks.shuffled(random).groupBy { track ->
            track.artist.trim().lowercase().takeIf { it.isNotBlank() && it != "unknown artist" }
                ?: "track:${track.id}"
        }.mapValues { (_, value) -> value.toMutableList() }.toMutableMap()
        val artists = buckets.keys.shuffled(random).toMutableList()
        val result = mutableListOf<Track>()
        while (artists.isNotEmpty()) {
            val iterator = artists.iterator()
            while (iterator.hasNext()) {
                val artist = iterator.next()
                val bucket = buckets[artist]
                if (bucket == null || bucket.isEmpty()) {
                    iterator.remove()
                } else {
                    result += bucket.removeAt(0)
                    if (bucket.isEmpty()) iterator.remove()
                }
            }
        }
        return result
    }
}
