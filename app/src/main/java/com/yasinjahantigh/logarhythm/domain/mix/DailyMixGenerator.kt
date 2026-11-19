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

        val skipCounts = HashMap<String, Int>()
        val playCounts = HashMap<String, Int>()
        val lastPlayed = HashMap<String, Long>()
        for (i in playEvents.indices) {
            val ev = playEvents[i]
            val id = ev.trackId
            if (ev.skipped) {
                skipCounts[id] = (skipCounts[id] ?: 0) + 1
            } else {
                playCounts[id] = (playCounts[id] ?: 0) + 1
                val prev = lastPlayed[id]
                if (prev == null || ev.timestamp > prev) {
                    lastPlayed[id] = ev.timestamp
                }
            }
        }

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

        // Precompute per-track tasteMatchScore and commonPenalties once for all 3 mixes.
        val poolSize = workingPool.size
        val precomputedTaste = DoubleArray(poolSize)
        val precomputedPenalty = DoubleArray(poolSize)
        for (i in 0 until poolSize) {
            val track = workingPool[i]
            val id = track.id
            val sessionPen = profile.sessionPenalty(track)
            precomputedTaste[i] = profile.tasteMatchScoreWithPrecomputed(track, dna = null, sessionPen = sessionPen)
            val dislikedPenalty = if (profile.isDisliked(id)) 30.0 else 0.0
            val skipPenalty = (skipCounts[id] ?: 0) * 2.2
            val replayCooldown = profile.recentPlayPenalty(id, snapshotTimeMs) * 4.0
            val multiDayExposure = exposurePenalty(id, seedDate, recentExposureDays)
            precomputedPenalty[i] = dislikedPenalty + skipPenalty + sessionPen * 6.0 + replayCooldown + multiDayExposure
        }

        val artistKeyCache = HashMap<String, String>()
        fun cachedArtistKey(track: Track): String =
            artistKeyCache.getOrPut(track.id) { primaryArtistKey(track) }

        val scoreComparator = compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id }

        // Mix 1: Rotation & Favorites (+ co-listen bridges), kept fresh with daily rotation.
        val familiarScored = ArrayList<Pair<Track, Double>>(poolSize)
        for (i in 0 until poolSize) {
            val track = workingPool[i]
            val id = track.id
            val plays = (playCounts[id] ?: 0).coerceAtMost(12)
            val favBonus = if (id in favoriteTrackIds) 5.5 else 0.0
            var coListenBoost = 0.0
            for (j in bridgeAnchors.indices) {
                val anchor = bridgeAnchors[j]
                if (anchor != id) {
                    val v = profile.coListenStrength(anchor, id) * 3.0
                    if (v > coListenBoost) coListenBoost = v
                }
            }
            val score = plays * 1.0 +
                profile.affinity(id).coerceAtLeast(0.0) * 1.8 +
                precomputedTaste[i].coerceAtLeast(0.0) * 1.2 +
                favBonus +
                coListenBoost +
                dailyWave(id, seedDate, 1L) * 0.85 -
                precomputedPenalty[i]
            familiarScored.add(track to score)
        }
        familiarScored.sortWith(scoreComparator)

        val familiarSelected = selectTopDiverse(familiarScored, MIX_SIZE, MAX_PER_ARTIST_IN_MIX, ::cachedArtistKey)
        val familiar = sequenceForSmoothFlow(familiarSelected, profile, seedDate, mixSalt = 1L, ::cachedArtistKey)
        val familiarIds = familiar.mapTo(HashSet()) { it.id }

        // Mix 2: Rediscover & Deep Cuts (stale played tracks + deep library cuts).
        fun rediscoveryScore(index: Int, track: Track): Double {
            val id = track.id
            val count = playCounts[id] ?: 0
            val lastTime = lastPlayed[id] ?: 0L
            val staleDays = if (lastTime <= 0L) {
                MAX_STALE_DAYS
            } else {
                ((snapshotTimeMs - lastTime).coerceAtLeast(0L) / DAY_MS).coerceAtMost(MAX_STALE_DAYS)
            }
            val staleBonus = if (count > 0 && staleDays >= 7L) staleDays / 8.5 else 0.0
            val neverPlayedScore = if (count == 0) {
                5.0 + precomputedTaste[index].coerceIn(0.0, 4.0) * 1.3 + recentAddBonus(track.dateAdded, snapshotTimeMs)
            } else {
                0.0
            }
            val favStaleBonus = if (id in favoriteTrackIds && staleDays >= 7L) 5.0 else 0.0
            return staleBonus +
                neverPlayedScore +
                favStaleBonus +
                dailyWave(id, seedDate, 2L) -
                count.coerceAtMost(10) * 1.15 -
                precomputedPenalty[index]
        }

        val rediscoveryUnique = ArrayList<Pair<Track, Double>>(poolSize)
        for (i in 0 until poolSize) {
            val track = workingPool[i]
            if (track.id !in familiarIds) {
                rediscoveryUnique.add(track to rediscoveryScore(i, track))
            }
        }
        rediscoveryUnique.sortWith(scoreComparator)
        val rediscoverySelected = if (rediscoveryUnique.size >= MIX_SIZE) {
            selectTopDiverse(rediscoveryUnique, MIX_SIZE, MAX_PER_ARTIST_IN_MIX, ::cachedArtistKey)
        } else {
            val rediscoveryFill = ArrayList<Pair<Track, Double>>()
            for (i in 0 until poolSize) {
                val track = workingPool[i]
                if (track.id in familiarIds) {
                    rediscoveryFill.add(track to (rediscoveryScore(i, track) - 10.0))
                }
            }
            rediscoveryFill.sortWith(scoreComparator)
            selectUniqueThenFill(rediscoveryUnique, rediscoveryFill, ::cachedArtistKey)
        }
        val rediscovery = sequenceForSmoothFlow(rediscoverySelected, profile, seedDate, mixSalt = 2L, ::cachedArtistKey)

        // Mix 3: Taste-Matched Discovery (replaces blind random shuffle with artist/genre/era/DNA fit).
        val alreadyFeatured = HashSet<String>(familiar.size + rediscovery.size)
        for (t in familiar) alreadyFeatured.add(t.id)
        for (t in rediscovery) alreadyFeatured.add(t.id)

        fun discoveryScore(index: Int, track: Track): Double {
            val id = track.id
            val count = playCounts[id] ?: 0
            val noveltyScore = when {
                count == 0 -> 4.5
                count <= 2 -> 2.2
                else -> (2.0 - count * 0.7).coerceAtLeast(-4.0)
            }
            val tasteFit = precomputedTaste[index] * 2.4
            val freshness = recentAddBonus(track.dateAdded, snapshotTimeMs) * 1.2
            return noveltyScore +
                tasteFit +
                freshness +
                dailyWave(id, seedDate, 3L) -
                precomputedPenalty[index]
        }

        val discoveryUnique = ArrayList<Pair<Track, Double>>(poolSize)
        for (i in 0 until poolSize) {
            val track = workingPool[i]
            if (track.id !in alreadyFeatured) {
                discoveryUnique.add(track to discoveryScore(i, track))
            }
        }
        discoveryUnique.sortWith(scoreComparator)
        val discoverySelected = if (discoveryUnique.size >= MIX_SIZE) {
            selectTopDiverse(discoveryUnique, MIX_SIZE, MAX_PER_ARTIST_IN_MIX, ::cachedArtistKey)
        } else {
            val discoveryFill = ArrayList<Pair<Track, Double>>()
            for (i in 0 until poolSize) {
                val track = workingPool[i]
                if (track.id in alreadyFeatured) {
                    discoveryFill.add(track to (discoveryScore(i, track) - 10.0))
                }
            }
            discoveryFill.sortWith(scoreComparator)
            selectUniqueThenFill(discoveryUnique, discoveryFill, ::cachedArtistKey)
        }
        val discovery = sequenceForSmoothFlow(discoverySelected, profile, seedDate, mixSalt = 3L, ::cachedArtistKey)

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
        fill: List<Pair<Track, Double>>,
        artistKeyOf: (Track) -> String
    ): List<Track> {
        if (unique.size >= MIX_SIZE) {
            return selectTopDiverse(unique, MIX_SIZE, MAX_PER_ARTIST_IN_MIX, artistKeyOf)
        }
        val fromUnique = selectTopDiverse(unique, unique.size, MAX_PER_ARTIST_IN_MIX, artistKeyOf)
        val fromFill = selectTopDiverse(fill, MIX_SIZE - fromUnique.size, MAX_PER_ARTIST_IN_MIX, artistKeyOf)
        return fromUnique + fromFill
    }

    private fun selectTopDiverse(
        ranked: List<Pair<Track, Double>>,
        limit: Int,
        maxPerArtist: Int,
        artistKeyOf: (Track) -> String
    ): List<Track> {
        if (ranked.isEmpty() || limit <= 0) return emptyList()
        val chosen = ArrayList<Track>(minOf(limit, ranked.size))
        val chosenIds = HashSet<String>()
        val artistCounts = HashMap<String, Int>()

        for ((track, _) in ranked) {
            if (chosen.size >= limit) break
            val key = artistKeyOf(track)
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

    private class FlowNode(
        val track: Track,
        val dna: TrackCuration.TrackDna,
        val artistKey: String,
        val isRealArtist: Boolean,
        val genreKey: String,
        val albumNorm: String?
    )

    /**
     * DJ-style smooth flow ordering: chains tracks along a continuous energy/valence/acoustic
     * curve while favoring co-listened pairs and separating same-artist/album tracks.
     */
    private fun sequenceForSmoothFlow(
        tracks: List<Track>,
        profile: TasteProfile,
        seedDate: Long,
        mixSalt: Long,
        artistKeyOf: (Track) -> String
    ): List<Track> {
        if (tracks.size <= 2) return tracks
        val remaining = ArrayList<FlowNode>(tracks.size)
        for (i in tracks.indices) {
            val t = tracks[i]
            val aKey = artistKeyOf(t)
            val albumNorm = if (MetadataNormalizer.isPlaceholderField(t.album)) {
                null
            } else {
                MetadataNormalizer.normalizeForComparison(t.album).ifEmpty { null }
            }
            remaining.add(
                FlowNode(
                    track = t,
                    dna = TrackCuration.analyzeTrackDna(t),
                    artistKey = aKey,
                    isRealArtist = !aKey.startsWith("track:"),
                    genreKey = MetadataNormalizer.genreGroupKey(t.genre),
                    albumNorm = albumNorm
                )
            )
        }
        val openerPoolSize = minOf(3, remaining.size)
        val openerIdx = (((seedDate + mixSalt) % openerPoolSize) + openerPoolSize).toInt() % openerPoolSize
        val orderedNodes = ArrayList<FlowNode>(remaining.size)
        orderedNodes.add(remaining.removeAt(openerIdx))

        while (remaining.isNotEmpty()) {
            val prev = orderedNodes.last()
            val prev2 = if (orderedNodes.size >= 2) orderedNodes[orderedNodes.size - 2] else null
            val prevDna = prev.dna
            val prevArtist = prev.artistKey
            val prev2Artist = prev2?.artistKey
            val prevGenre = prev.genreKey
            val prevAlbum = prev.albumNorm

            var bestIdx = 0
            var bestCost = Float.MAX_VALUE
            for (i in remaining.indices) {
                val cand = remaining[i]
                val candDna = cand.dna

                var cost = abs(prevDna.energyScore - candDna.energyScore) * 0.45f +
                    abs(prevDna.moodValence - candDna.moodValence) * 0.30f +
                    abs(prevDna.acousticScore - candDna.acousticScore) * 0.15f -
                    profile.coListenStrength(prev.track.id, cand.track.id).toFloat() * 0.35f

                if (prevGenre.isNotEmpty() && prevGenre == cand.genreKey) cost -= 0.08f
                if (cand.isRealArtist) {
                    if (cand.artistKey == prevArtist) cost += 0.80f
                    else if (prev2Artist != null && cand.artistKey == prev2Artist) cost += 0.30f
                }
                if (prevAlbum != null && cand.albumNorm != null && prevAlbum == cand.albumNorm) {
                    cost += 0.25f
                }

                if (cost < bestCost) {
                    bestCost = cost
                    bestIdx = i
                }
            }
            orderedNodes.add(remaining.removeAt(bestIdx))
        }
        return orderedNodes.map { it.track }
    }

    private fun primaryArtistKey(track: Track): String {
        val primary = ArtistSplitter.split(track.artist).firstOrNull().orEmpty()
        if (MetadataNormalizer.isPlaceholderField(primary)) return "track:${track.id}"
        return MetadataNormalizer.normalizeForComparison(primary).ifBlank { "track:${track.id}" }
    }
}
