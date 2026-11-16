package com.yasinjahantigh.logarhythm.domain.mix

import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.DailyMix
import com.yasinjahantigh.logarhythm.domain.model.MixTrackReason
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import kotlin.math.abs

/**
 * Offline, stable-for-the-day personalized mixes combining taste transfer learning
 * (artist, bilingual genre family, decade era, sonic DNA), multi-day mix exposure memory,
 * 48-hour replay cooldown, session skip dampening, and DJ-style smooth flow sequencing.
 */
class DailyMixGenerator {
    companion object {
        const val MIX_SIZE = 20
        const val NUM_MIXES = 3
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_STALE_DAYS = 120L
        private const val EXPOSURE_WINDOW_DAYS = 7L
        private const val MAX_PER_ARTIST_IN_MIX = 3
    }

    fun generateMixes(
        allTracks: List<Track>,
        playEvents: List<PlayEvent>,
        favoriteTrackIds: Set<String> = emptySet(),
        seedDate: Long = System.currentTimeMillis() / DAY_MS,
        tasteProfile: TasteProfile? = null,
        recentExposureDays: Map<String, Long> = emptyMap()
    ): List<DailyMix> {
        val library = allTracks.distinctBy { it.id }
        if (library.isEmpty()) return emptyList()

        val completedEvents = playEvents.filter { !it.skipped }
        val skipCounts = playEvents.filter { it.skipped }.groupingBy { it.trackId }.eachCount()
        val playCounts = completedEvents.groupingBy { it.trackId }.eachCount()
        val lastPlayed = completedEvents.groupBy { it.trackId }
            .mapValues { (_, events) -> events.maxOfOrNull { it.timestamp } ?: 0L }

        // Anchor time-based scoring to the supplied day so a mix cannot reshuffle mid-day.
        val snapshotTimeMs = seedDate * DAY_MS + DAY_MS / 2
        val profile = tasteProfile ?: TasteProfile.build(
            events = playEvents,
            favoriteTrackIds = favoriteTrackIds,
            nowMs = snapshotTimeMs,
            library = library
        )

        val nonDisliked = library.filter { !profile.isDisliked(it.id) }
        val workingPool = if (nonDisliked.size >= MIX_SIZE || (nonDisliked.isNotEmpty() && library.size <= MIX_SIZE)) {
            nonDisliked
        } else {
            library
        }

        val bridgeAnchors = workingPool
            .sortedByDescending { track ->
                profile.affinity(track.id) + (if (track.id in favoriteTrackIds) 3.0 else 0.0)
            }
            .take(5)
            .map { it.id }

        fun commonPenalties(track: Track): Double {
            val dislikedPenalty = if (profile.isDisliked(track.id)) 30.0 else 0.0
            val skipPenalty = (skipCounts[track.id] ?: 0) * 2.2
            val sessionSkipPenalty = profile.sessionPenalty(track) * 6.0
            val replayCooldown = profile.recentPlayPenalty(track.id, snapshotTimeMs) * 4.0
            val multiDayExposure = exposurePenalty(track.id, seedDate, recentExposureDays)
            return dislikedPenalty + skipPenalty + sessionSkipPenalty + replayCooldown + multiDayExposure
        }

        // Mix 1: Rotation & Favorites (+ co-listen bridges), kept fresh with daily rotation.
        val familiarScored = workingPool.map { track ->
            val plays = (playCounts[track.id] ?: 0).coerceAtMost(12)
            val favBonus = if (track.id in favoriteTrackIds) 5.5 else 0.0
            val coListenBoost = bridgeAnchors.maxOfOrNull { anchor ->
                if (anchor == track.id) 0.0 else profile.coListenStrength(anchor, track.id) * 3.0
            } ?: 0.0
            val score = plays * 1.0 +
                profile.affinity(track.id).coerceAtLeast(0.0) * 1.8 +
                profile.tasteMatchScore(track).coerceAtLeast(0.0) * 1.2 +
                favBonus +
                coListenBoost +
                dailyWave(track.id, seedDate, 1L) * 0.85 -
                commonPenalties(track)
            track to score
        }.sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })

        val familiarSelected = selectTopDiverse(familiarScored, MIX_SIZE, MAX_PER_ARTIST_IN_MIX)
        val familiar = sequenceForSmoothFlow(familiarSelected, profile, seedDate, mixSalt = 1L)
        val familiarIds = familiar.mapTo(HashSet()) { it.id }

        // Mix 2: Rediscover & Deep Cuts (stale played tracks + deep library cuts).
        fun rediscoveryScore(track: Track): Double {
            val count = playCounts[track.id] ?: 0
            val lastTime = lastPlayed[track.id] ?: 0L
            val staleDays = if (lastTime <= 0L) {
                MAX_STALE_DAYS
            } else {
                ((snapshotTimeMs - lastTime).coerceAtLeast(0L) / DAY_MS).coerceAtMost(MAX_STALE_DAYS)
            }
            val staleBonus = if (count > 0 && staleDays >= 7L) staleDays / 8.5 else 0.0
            val neverPlayedScore = if (count == 0) {
                5.0 + profile.tasteMatchScore(track).coerceIn(0.0, 4.0) * 1.3 + recentAddBonus(track.dateAdded, snapshotTimeMs)
            } else {
                0.0
            }
            val favStaleBonus = if (track.id in favoriteTrackIds && staleDays >= 7L) 5.0 else 0.0
            return staleBonus +
                neverPlayedScore +
                favStaleBonus +
                dailyWave(track.id, seedDate, 2L) -
                count.coerceAtMost(10) * 1.15 -
                commonPenalties(track)
        }

        val rediscoveryUnique = workingPool.filter { it.id !in familiarIds }
            .map { it to rediscoveryScore(it) }
            .sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })
        val rediscoveryFill = workingPool.filter { it.id in familiarIds }
            .map { it to rediscoveryScore(it) - 10.0 }
            .sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })
        val rediscoverySelected = selectUniqueThenFill(rediscoveryUnique, rediscoveryFill)
        val rediscovery = sequenceForSmoothFlow(rediscoverySelected, profile, seedDate, mixSalt = 2L)

        // Mix 3: Taste-Matched Discovery (replaces blind random shuffle with artist/genre/era/DNA fit).
        val alreadyFeatured = (familiar + rediscovery).mapTo(HashSet()) { it.id }
        fun discoveryScore(track: Track): Double {
            val count = playCounts[track.id] ?: 0
            val noveltyScore = when {
                count == 0 -> 4.5
                count <= 2 -> 2.2
                else -> (2.0 - count * 0.7).coerceAtLeast(-4.0)
            }
            val tasteFit = profile.tasteMatchScore(track) * 2.4
            val freshness = recentAddBonus(track.dateAdded, snapshotTimeMs) * 1.2
            return noveltyScore +
                tasteFit +
                freshness +
                dailyWave(track.id, seedDate, 3L) -
                commonPenalties(track)
        }

        val discoveryUnique = workingPool.filter { it.id !in alreadyFeatured }
            .map { it to discoveryScore(it) }
            .sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })
        val discoveryFill = workingPool.filter { it.id in alreadyFeatured }
            .map { it to discoveryScore(it) - 10.0 }
            .sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })
        val discoverySelected = selectUniqueThenFill(discoveryUnique, discoveryFill)
        val discovery = sequenceForSmoothFlow(discoverySelected, profile, seedDate, mixSalt = 3L)

        val sets = listOf(familiar, rediscovery, discovery)
        return sets.mapIndexed { index, selected ->
            val mixNumber = index + 1
            val reasons = selected.associate { track ->
                track.id to determineReason(
                    track = track,
                    mixNumber = mixNumber,
                    profile = profile,
                    playCount = playCounts[track.id] ?: 0,
                    lastPlayedMs = lastPlayed[track.id] ?: 0L,
                    favoriteTrackIds = favoriteTrackIds,
                    snapshotTimeMs = snapshotTimeMs
                )
            }
            DailyMix(
                id = "daily_mix_$mixNumber",
                tracks = selected,
                seedDate = seedDate,
                trackReasons = reasons
            )
        }
    }

    private fun determineReason(
        track: Track,
        mixNumber: Int,
        profile: TasteProfile,
        playCount: Int,
        lastPlayedMs: Long,
        favoriteTrackIds: Set<String>,
        snapshotTimeMs: Long
    ): MixTrackReason {
        val ageDays = if (lastPlayedMs > 0L) {
            (snapshotTimeMs - lastPlayedMs).coerceAtLeast(0L) / DAY_MS
        } else 0L
        if (mixNumber == 2 && playCount > 0 && ageDays >= 14L) {
            return MixTrackReason.REDISCOVER_GEM
        }
        if (track.id in favoriteTrackIds) {
            return MixTrackReason.FAVORITE
        }
        if (playCount >= 3 || profile.affinity(track.id) >= 2.0) {
            return MixTrackReason.HEAVY_ROTATION
        }
        if (profile.artistAffinity(track.artist) >= 1.2) {
            return MixTrackReason.ARTIST_AFFINITY
        }
        if (profile.genreAffinity(track.genre) >= 1.2 ||
            (profile.preferredDna != null && profile.tasteMatchScore(track) >= 0.8)
        ) {
            return MixTrackReason.GENRE_MOOD_MATCH
        }
        if (playCount == 0) {
            return MixTrackReason.FRESH_ADDITION
        }
        return if (mixNumber == 2) MixTrackReason.REDISCOVER_GEM else MixTrackReason.GENRE_MOOD_MATCH
    }

    private fun exposurePenalty(trackId: String, seedDate: Long, exposureDays: Map<String, Long>): Double {
        val lastShown = exposureDays[trackId] ?: return 0.0
        val delta = seedDate - lastShown
        // delta <= 0 means shown today (or future): zero penalty so today's mixes remain stable.
        if (delta <= 0L || delta > EXPOSURE_WINDOW_DAYS) return 0.0
        return (EXPOSURE_WINDOW_DAYS + 1L - delta) * 1.1
    }

    private fun recentAddBonus(dateAddedSecOrMs: Long, snapshotTimeMs: Long): Double {
        if (dateAddedSecOrMs <= 0L) return 0.0
        val addedMs = if (dateAddedSecOrMs < 10_000_000_000L) dateAddedSecOrMs * 1000L else dateAddedSecOrMs
        val ageDays = ((snapshotTimeMs - addedMs).coerceAtLeast(0L)) / DAY_MS
        return when {
            ageDays <= 7L -> 1.8
            ageDays <= 30L -> 1.1
            ageDays <= 90L -> 0.5
            else -> 0.0
        }
    }

    /** Deterministic daily jitter in `[0.0, 4.5]` so top candidates rotate smoothly day-to-day. */
    private fun dailyWave(trackId: String, seedDate: Long, mixSalt: Long): Double {
        var h = trackId.hashCode().toLong() xor
            (seedDate * 0x517CC1B727220A95L) xor
            (mixSalt * 0x2545F4914F6CDD1DL)
        h = (h xor (h ushr 30)) * 0x2545F4914F6CDD1DL
        h = (h xor (h ushr 27)) * 0x4CF5AD432745937FL
        h = h xor (h ushr 31)
        val unit = ((h ushr 11) and 0x1FFFFFL).toDouble() / 2097151.0
        return unit * 4.5
    }

    private fun selectUniqueThenFill(
        unique: List<Pair<Track, Double>>,
        fill: List<Pair<Track, Double>>
    ): List<Track> {
        if (unique.size >= MIX_SIZE) {
            return selectTopDiverse(unique, MIX_SIZE, MAX_PER_ARTIST_IN_MIX)
        }
        val fromUnique = selectTopDiverse(unique, unique.size, MAX_PER_ARTIST_IN_MIX)
        val fromFill = selectTopDiverse(fill, MIX_SIZE - fromUnique.size, MAX_PER_ARTIST_IN_MIX)
        return fromUnique + fromFill
    }

    private fun selectTopDiverse(
        ranked: List<Pair<Track, Double>>,
        limit: Int,
        maxPerArtist: Int
    ): List<Track> {
        if (ranked.isEmpty() || limit <= 0) return emptyList()
        val chosen = ArrayList<Track>(minOf(limit, ranked.size))
        val chosenIds = HashSet<String>()
        val artistCounts = HashMap<String, Int>()

        for ((track, _) in ranked) {
            if (chosen.size >= limit) break
            val key = primaryArtistKey(track)
            val current = artistCounts[key] ?: 0
            if (current < maxPerArtist) {
                chosen.add(track)
                chosenIds.add(track.id)
                artistCounts[key] = current + 1
            }
        }

        if (chosen.size < limit) {
            for ((track, _) in ranked) {
                if (chosen.size >= limit) break
                if (chosenIds.add(track.id)) {
                    chosen.add(track)
                }
            }
        }
        return chosen
    }

    /**
     * DJ-style smooth flow ordering: chains tracks along a continuous energy/valence/acoustic
     * curve while favoring co-listened pairs and separating same-artist/album tracks.
     */
    private fun sequenceForSmoothFlow(
        tracks: List<Track>,
        profile: TasteProfile,
        seedDate: Long,
        mixSalt: Long
    ): List<Track> {
        if (tracks.size <= 2) return tracks
        val dnaMap = tracks.associate { it.id to TrackCuration.analyzeTrackDna(it) }
        val openerPoolSize = minOf(3, tracks.size)
        val openerIdx = (((seedDate + mixSalt) % openerPoolSize) + openerPoolSize).toInt() % openerPoolSize
        val remaining = tracks.toMutableList()
        val ordered = ArrayList<Track>(tracks.size)
        ordered.add(remaining.removeAt(openerIdx))

        while (remaining.isNotEmpty()) {
            val prev = ordered.last()
            val prev2 = if (ordered.size >= 2) ordered[ordered.size - 2] else null
            val prevDna = dnaMap.getValue(prev.id)
            val prevArtist = primaryArtistKey(prev)
            val prev2Artist = prev2?.let { primaryArtistKey(it) }
            val prevGenre = MetadataNormalizer.genreGroupKey(prev.genre)

            var bestIdx = 0
            var bestCost = Float.MAX_VALUE
            for (i in remaining.indices) {
                val cand = remaining[i]
                val candDna = dnaMap.getValue(cand.id)
                val candArtist = primaryArtistKey(cand)
                val candGenre = MetadataNormalizer.genreGroupKey(cand.genre)

                var cost = abs(prevDna.energyScore - candDna.energyScore) * 0.45f +
                    abs(prevDna.moodValence - candDna.moodValence) * 0.30f +
                    abs(prevDna.acousticScore - candDna.acousticScore) * 0.15f -
                    profile.coListenStrength(prev.id, cand.id).toFloat() * 0.35f

                if (prevGenre.isNotEmpty() && prevGenre == candGenre) cost -= 0.08f
                if (candArtist == prevArtist && !candArtist.startsWith("track:")) cost += 0.80f
                else if (prev2Artist != null && candArtist == prev2Artist && !candArtist.startsWith("track:")) cost += 0.30f
                if (hasKnownSameAlbum(prev.album, cand.album)) cost += 0.25f

                if (cost < bestCost) {
                    bestCost = cost
                    bestIdx = i
                }
            }
            ordered.add(remaining.removeAt(bestIdx))
        }
        return ordered
    }

    private fun primaryArtistKey(track: Track): String {
        val primary = ArtistSplitter.split(track.artist).firstOrNull().orEmpty()
        if (MetadataNormalizer.isPlaceholderField(primary)) return "track:${track.id}"
        return MetadataNormalizer.normalizeForComparison(primary).ifBlank { "track:${track.id}" }
    }

    private fun hasKnownSameAlbum(left: String, right: String): Boolean {
        if (MetadataNormalizer.isPlaceholderField(left) || MetadataNormalizer.isPlaceholderField(right)) return false
        val nl = MetadataNormalizer.normalizeForComparison(left)
        val nr = MetadataNormalizer.normalizeForComparison(right)
        return nl.isNotEmpty() && nl == nr
    }
}
