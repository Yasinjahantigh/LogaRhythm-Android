package com.example.musicplayer.domain.mix

import com.example.musicplayer.domain.model.DailyMix
import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.Track
import kotlin.random.Random

/**
 * Pure Kotlin offline generator for personalized Daily Mixes.
 * Uses PlayEvent history, metadata relationships, and a date-based seed for stability.
 */
class DailyMixGenerator {

    companion object {
        const val MIX_SIZE = 15
        const val NUM_MIXES = 3
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }

    fun generateMixes(
        allTracks: List<Track>,
        playEvents: List<PlayEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        seedDate: Long = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
    ): List<DailyMix> {
        if (allTracks.isEmpty()) return emptyList()

        val trackById = allTracks.associateBy { it.id }
        val playCounts = playEvents.groupingBy { it.trackId }.eachCount()
        val lastPlayed = playEvents.groupBy { it.trackId }
            .mapValues { (_, events) -> events.maxOfOrNull { it.timestamp } ?: 0L }

        val currentTimeMs = System.currentTimeMillis()
        val sevenDaysAgo = currentTimeMs - SEVEN_DAYS_MS

        val frequentTracks = allTracks
            .filter { (playCounts[it.id] ?: 0) >= 1 }
            .sortedByDescending { playCounts[it.id] ?: 0 }

        val forgottenLikedTracks = allTracks.filter { track ->
            val isFavorite = track.id in favoriteTrackIds
            val hadPlayed = (playCounts[track.id] ?: 0) > 0
            val lastPlayTime = lastPlayed[track.id] ?: 0L
            (isFavorite || hadPlayed) && (lastPlayTime == 0L || lastPlayTime < sevenDaysAgo)
        }

        val topArtists = allTracks
            .map { it.artist }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinct()
            .sortedByDescending { artist ->
                allTracks.filter { it.artist.equals(artist, ignoreCase = true) }
                    .sumOf { playCounts[it.id] ?: 0 }
            }

        val mixes = mutableListOf<DailyMix>()

        for (mixIndex in 0 until NUM_MIXES) {
            val mixSeed = seedDate * 1000L + mixIndex * 37L
            val random = Random(mixSeed)

            val anchorArtist = topArtists.getOrNull(mixIndex)
            val selectedTrackIds = LinkedHashSet<String>()

            // 1. Frequently played tracks for this anchor or overall
            if (anchorArtist != null) {
                val anchorFrequent = frequentTracks.filter { it.artist.equals(anchorArtist, ignoreCase = true) }
                anchorFrequent.shuffled(random).take(5).forEach { selectedTrackIds.add(it.id) }
            }
            frequentTracks.shuffled(random).take(4).forEach { selectedTrackIds.add(it.id) }

            // 2. Tracks not played recently but liked before
            forgottenLikedTracks.shuffled(random).take(4).forEach { selectedTrackIds.add(it.id) }

            // 3. Similar artists or same album/folder tracks in library
            if (anchorArtist != null) {
                val anchorLibrary = allTracks.filter { it.artist.equals(anchorArtist, ignoreCase = true) }
                anchorLibrary.shuffled(random).take(4).forEach { selectedTrackIds.add(it.id) }
            }

            // 4. Fill remaining spots deterministically from library
            if (selectedTrackIds.size < MIX_SIZE) {
                val remaining = allTracks.filter { it.id !in selectedTrackIds }.shuffled(random)
                for (track in remaining) {
                    selectedTrackIds.add(track.id)
                    if (selectedTrackIds.size >= MIX_SIZE) break
                }
            }

            val finalTracks = selectedTrackIds.mapNotNull { trackById[it] }.shuffled(random)

            val artistsFeatured = finalTracks
                .map { it.artist }
                .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                .distinct()
                .take(3)

            val description = if (artistsFeatured.isNotEmpty()) {
                artistsFeatured.joinToString(", ")
            } else {
                "Curated mix of your music"
            }

            mixes.add(
                DailyMix(
                    id = "daily_mix_${mixIndex + 1}",
                    title = "Daily Mix ${mixIndex + 1}",
                    description = description,
                    tracks = finalTracks,
                    seedDate = seedDate
                )
            )
        }

        return mixes
    }
}
