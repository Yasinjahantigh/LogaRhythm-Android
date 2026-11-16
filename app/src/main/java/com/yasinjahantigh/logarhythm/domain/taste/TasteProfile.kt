package com.yasinjahantigh.logarhythm.domain.taste

import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.Track
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.pow

/**
 * What the listener's own behavior says about their taste, computed entirely on-device from
 * play history. Combines track-level signals with artist/genre/era transfer learning,
 * completion-ratio weighting, sonic DNA centroid, 48h replay cooldown, and 3h skip dampening.
 */
class TasteProfile private constructor(
    private val affinities: Map<String, Double>,
    private val coListen: Map<String, Map<String, Double>>,
    private val hourly: Map<String, DoubleArray>,
    private val playCounts: Map<String, Int>,
    private val skipCounts: Map<String, Int>,
    private val artistAffinities: Map<String, Double>,
    private val artistDisplayNames: Map<String, String>,
    private val genreAffinities: Map<String, Double>,
    private val genreDisplayNames: Map<String, String>,
    private val eraAffinities: Map<Int, Double>,
    private val lastPlayedAt: Map<String, Long>,
    private val sessionArtistSkips: Map<String, Int>,
    private val sessionGenreSkips: Map<String, Int>,
    val preferredDna: TrackCuration.TrackDna?,
    private val builtAtMs: Long,
    val hasHistory: Boolean
) {
    /** Positive when liked and recently played, negative when habitually skipped. */
    fun affinity(trackId: String): Double = affinities[trackId] ?: 0.0

    /** 0..1 — how strongly two tracks have been heard together in the same sitting. */
    fun coListenStrength(a: String, b: String): Double {
        val raw = coListen[a]?.get(b) ?: return 0.0
        return raw / (raw + CO_LISTEN_SATURATION)
    }

    /** Recency-weighted amount the listener has played [trackId] around [hour] (0..23). */
    fun hourWeight(trackId: String, hour: Int): Double =
        hourly[trackId]?.getOrNull(((hour % 24) + 24) % 24) ?: 0.0

    fun playCount(trackId: String): Int = playCounts[trackId] ?: 0

    fun skipCount(trackId: String): Int = skipCounts[trackId] ?: 0

    /** Passed over repeatedly and rarely finished: safe to keep out of suggestions. */
    fun isDisliked(trackId: String): Boolean {
        val skips = skipCount(trackId)
        return skips >= 2 && skips > playCount(trackId) * 2
    }

    fun artistAffinity(artist: String): Double {
        val keys = artistKeys(artist)
        if (keys.isEmpty()) return 0.0
        return keys.maxOf { (key, _) -> artistAffinities[key] ?: 0.0 }
    }

    fun genreAffinity(genre: String): Double {
        val key = MetadataNormalizer.genreGroupKey(genre)
        if (key.isEmpty()) return 0.0
        return genreAffinities[key] ?: 0.0
    }

    fun eraAffinity(year: Int): Double {
        if (year !in 1920..2100) return 0.0
        return eraAffinities[(year / 10) * 10] ?: 0.0
    }

    fun lastPlayedMs(trackId: String): Long? = lastPlayedAt[trackId]

    /**
     * Returns 0.0..1.0 cooldown penalty for tracks played within the last 48 hours
     * (`1.0` if just played, decaying linearly to `0.0` at 48 hours).
     */
    fun recentPlayPenalty(trackId: String, referenceNowMs: Long = builtAtMs): Double {
        val playedAt = lastPlayedAt[trackId] ?: return 0.0
        val ageMs = referenceNowMs - playedAt
        if (ageMs < 0L || ageMs >= RECENT_COOLDOWN_MS) return 0.0
        return 1.0 - (ageMs.toDouble() / RECENT_COOLDOWN_MS)
    }

    /**
     * Session-reactive skip dampening: returns > 0.0 when the listener skipped
     * 2+ tracks by the track's artist or 3+ tracks in its genre family within the last 3 hours.
     */
    fun sessionPenalty(track: Track): Double {
        val artistSkips = artistKeys(track.artist).maxOfOrNull { (key, _) -> sessionArtistSkips[key] ?: 0 } ?: 0
        val genreKey = MetadataNormalizer.genreGroupKey(track.genre)
        val genreSkips = if (genreKey.isNotEmpty()) sessionGenreSkips[genreKey] ?: 0 else 0

        var penalty = 0.0
        if (artistSkips >= SESSION_ARTIST_SKIP_THRESHOLD) {
            penalty += (0.70 + (artistSkips - SESSION_ARTIST_SKIP_THRESHOLD) * 0.15).coerceAtMost(1.0)
        }
        if (genreSkips >= SESSION_GENRE_SKIP_THRESHOLD) {
            penalty += (0.50 + (genreSkips - SESSION_GENRE_SKIP_THRESHOLD) * 0.10).coerceAtMost(0.8)
        }
        return penalty.coerceAtMost(1.5)
    }

    /**
     * Composite personalized fit score combining direct track affinity with transfer learning
     * across artist, bilingual genre family, decade era, sonic DNA centroid, minus skip penalties.
     */
    fun tasteMatchScore(track: Track): Double {
        val trackScore = (affinity(track.id) * 0.45).coerceIn(-2.0, 2.5)
        val artistScore = saturate(artistAffinity(track.artist), scale = 4.0) * 1.8
        val genreScore = saturate(genreAffinity(track.genre), scale = 8.0) * 1.2
        val eraScore = saturate(eraAffinity(track.year), scale = 8.0) * 0.5
        val dnaScore = preferredDna?.let { target ->
            val dna = TrackCuration.analyzeTrackDna(track)
            val dist = abs(target.energyScore - dna.energyScore) +
                abs(target.moodValence - dna.moodValence) +
                abs(target.acousticScore - dna.acousticScore)
            ((1.0 - dist / 1.5).coerceIn(0.0, 1.0)) * 0.6
        } ?: 0.0
        val dislikedPenalty = if (isDisliked(track.id)) 3.0 else 0.0
        val activeSkipPenalty = sessionPenalty(track) * 2.0
        return trackScore + artistScore + genreScore + eraScore + dnaScore - dislikedPenalty - activeSkipPenalty
    }

    fun topArtists(limit: Int = 5): List<String> {
        if (limit <= 0) return emptyList()
        return artistAffinities.entries
            .asSequence()
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { artistDisplayNames[it.key] }
            .toList()
    }

    fun topGenres(limit: Int = 5): List<String> {
        if (limit <= 0) return emptyList()
        return genreAffinities.entries
            .asSequence()
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { genreDisplayNames[it.key] }
            .toList()
    }

    fun toSummary(limit: Int = 4): String {
        val artists = topArtists(limit)
        val genres = topGenres(limit)
        val topEra = eraAffinities.entries
            .filter { it.value > 0.0 }
            .maxByOrNull { it.value }
            ?.key
        val dna = preferredDna
        if (artists.isEmpty() && genres.isEmpty() && topEra == null && dna == null) return ""

        return buildList {
            if (artists.isNotEmpty()) add("Top artists: ${artists.joinToString(", ")}")
            if (genres.isNotEmpty()) add("Preferred genres: ${genres.joinToString(", ")}")
            if (topEra != null) add("Favorite era: ${topEra}s")
            if (dna != null) {
                val energyLabel = when {
                    dna.energyScore >= 0.65f -> "high-energy"
                    dna.energyScore <= 0.38f -> "chill/calm"
                    else -> "mid-tempo"
                }
                val moodLabel = when {
                    dna.moodValence >= 0.62f -> "uplifting"
                    dna.moodValence <= 0.40f -> "moody/melancholic"
                    else -> "balanced"
                }
                add("Typical vibe: $energyLabel, $moodLabel")
            }
        }.joinToString(" | ")
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val HOUR_MS = 60L * 60 * 1000
        private const val HALF_LIFE_DAYS = 45.0
        private const val SITTING_GAP_MS = 20L * 60 * 1000
        private const val CO_LISTEN_SATURATION = 1.5
        private const val SKIP_WEIGHT = 1.5
        private const val FAVORITE_BONUS = 2.0
        private const val RECENT_COOLDOWN_MS = 48L * HOUR_MS
        private const val SESSION_WINDOW_MS = 3L * HOUR_MS
        private const val SESSION_ARTIST_SKIP_THRESHOLD = 2
        private const val SESSION_GENRE_SKIP_THRESHOLD = 3

        val EMPTY = TasteProfile(
            affinities = emptyMap(),
            coListen = emptyMap(),
            hourly = emptyMap(),
            playCounts = emptyMap(),
            skipCounts = emptyMap(),
            artistAffinities = emptyMap(),
            artistDisplayNames = emptyMap(),
            genreAffinities = emptyMap(),
            genreDisplayNames = emptyMap(),
            eraAffinities = emptyMap(),
            lastPlayedAt = emptyMap(),
            sessionArtistSkips = emptyMap(),
            sessionGenreSkips = emptyMap(),
            preferredDna = null,
            builtAtMs = 0L,
            hasHistory = false
        )

        fun localHour(timestampMs: Long, utcOffsetMs: Int): Int =
            ((((timestampMs + utcOffsetMs) / HOUR_MS) % 24 + 24) % 24).toInt()

        fun build(
            events: List<PlayEvent>,
            favoriteTrackIds: Set<String> = emptySet(),
            nowMs: Long = System.currentTimeMillis(),
            utcOffsetMs: Int = TimeZone.getDefault().getOffset(nowMs),
            library: List<Track> = emptyList()
        ): TasteProfile {
            if (events.isEmpty() && favoriteTrackIds.isEmpty()) return EMPTY

            val libraryById = if (library.isEmpty()) emptyMap() else library.associateBy { it.id }
            val affinities = HashMap<String, Double>()
            val plays = HashMap<String, Int>()
            val skips = HashMap<String, Int>()
            val hourly = HashMap<String, DoubleArray>()
            val artistAffinities = HashMap<String, Double>()
            val artistDisplayNames = HashMap<String, String>()
            val genreAffinities = HashMap<String, Double>()
            val genreDisplayNames = HashMap<String, String>()
            val eraAffinities = HashMap<Int, Double>()
            val lastPlayedAt = HashMap<String, Long>()
            val sessionArtistSkips = HashMap<String, Int>()
            val sessionGenreSkips = HashMap<String, Int>()

            var dnaWeightSum = 0.0
            var dnaEnergySum = 0.0
            var dnaAcousticSum = 0.0
            var dnaDanceSum = 0.0
            var dnaValenceSum = 0.0

            fun recencyWeight(timestamp: Long): Double {
                val ageDays = ((nowMs - timestamp).coerceAtLeast(0L)).toDouble() / DAY_MS
                return 0.5.pow(ageDays / HALF_LIFE_DAYS)
            }

            fun addTransferWeights(track: Track, signedDelta: Double, positiveForDna: Double = 0.0) {
                val aKeys = artistKeys(track.artist)
                if (aKeys.isNotEmpty()) {
                    val share = signedDelta / aKeys.size
                    for ((key, display) in aKeys) {
                        artistAffinities[key] = (artistAffinities[key] ?: 0.0) + share
                        artistDisplayNames.putIfAbsent(key, display)
                    }
                }
                val genreKey = MetadataNormalizer.genreGroupKey(track.genre)
                if (genreKey.isNotEmpty()) {
                    genreAffinities[genreKey] = (genreAffinities[genreKey] ?: 0.0) + signedDelta
                    if (!genreDisplayNames.containsKey(genreKey)) {
                        genreDisplayNames[genreKey] = MetadataNormalizer.canonicalizeGenre(track.genre, track.genre)
                    }
                }
                if (track.year in 1920..2100) {
                    val decade = (track.year / 10) * 10
                    eraAffinities[decade] = (eraAffinities[decade] ?: 0.0) + signedDelta
                }
                if (positiveForDna > 0.0) {
                    val dna = TrackCuration.analyzeTrackDna(track)
                    dnaWeightSum += positiveForDna
                    dnaEnergySum += dna.energyScore * positiveForDna
                    dnaAcousticSum += dna.acousticScore * positiveForDna
                    dnaDanceSum += dna.danceabilityScore * positiveForDna
                    dnaValenceSum += dna.moodValence * positiveForDna
                }
            }

            for (event in events) {
                val recency = recencyWeight(event.timestamp)
                val track = libraryById[event.trackId]
                if (event.skipped) {
                    skips[event.trackId] = (skips[event.trackId] ?: 0) + 1
                    val earlyMult = if (track != null && track.durationMs > 0L && event.listenedMs > 0L) {
                        val ratio = event.listenedMs.toDouble() / track.durationMs.toDouble()
                        if (ratio < 0.25) 1.35 else 1.0
                    } else {
                        1.0
                    }
                    val delta = -SKIP_WEIGHT * recency * earlyMult
                    affinities[event.trackId] = (affinities[event.trackId] ?: 0.0) + delta
                    if (track != null) {
                        addTransferWeights(track, delta)
                        val ageMs = nowMs - event.timestamp
                        if (ageMs in 0L..SESSION_WINDOW_MS) {
                            for ((key, _) in artistKeys(track.artist)) {
                                sessionArtistSkips[key] = (sessionArtistSkips[key] ?: 0) + 1
                            }
                            val genreKey = MetadataNormalizer.genreGroupKey(track.genre)
                            if (genreKey.isNotEmpty()) {
                                sessionGenreSkips[genreKey] = (sessionGenreSkips[genreKey] ?: 0) + 1
                            }
                        }
                    }
                } else {
                    plays[event.trackId] = (plays[event.trackId] ?: 0) + 1
                    val completionMult = if (track != null && track.durationMs > 0L && event.listenedMs > 0L) {
                        val ratio = event.listenedMs.toDouble() / track.durationMs.toDouble()
                        when {
                            ratio >= 0.85 -> 1.25
                            ratio >= 0.50 -> 1.0
                            else -> 0.65
                        }
                    } else {
                        1.0
                    }
                    val w = recency * completionMult
                    affinities[event.trackId] = (affinities[event.trackId] ?: 0.0) + w
                    val prevLast = lastPlayedAt[event.trackId]
                    if (prevLast == null || event.timestamp > prevLast) {
                        lastPlayedAt[event.trackId] = event.timestamp
                    }
                    val buckets = hourly.getOrPut(event.trackId) { DoubleArray(24) }
                    val h = localHour(event.timestamp, utcOffsetMs)
                    buckets[h] += w
                    buckets[(h + 1) % 24] += w * 0.5
                    buckets[(h + 23) % 24] += w * 0.5
                    if (track != null) {
                        addTransferWeights(track, w, positiveForDna = w)
                    }
                }
            }

            for (id in favoriteTrackIds) {
                affinities[id] = (affinities[id] ?: 0.0) + FAVORITE_BONUS
                val track = libraryById[id]
                if (track != null) {
                    addTransferWeights(track, FAVORITE_BONUS * 0.75, positiveForDna = FAVORITE_BONUS * 0.75)
                }
            }

            // Co-listening: consecutive completed plays within one sitting.
            val coListen = HashMap<String, HashMap<String, Double>>()
            val ordered = events.filter { !it.skipped }.sortedBy { it.timestamp }
            for (i in 1 until ordered.size) {
                val prev = ordered[i - 1]
                val next = ordered[i]
                if (prev.trackId == next.trackId) continue
                if (next.timestamp - prev.timestamp > SITTING_GAP_MS) continue
                val w = recencyWeight(next.timestamp)
                coListen.getOrPut(prev.trackId) { HashMap() }.merge(next.trackId, w, Double::plus)
                coListen.getOrPut(next.trackId) { HashMap() }.merge(prev.trackId, w, Double::plus)
            }

            val centroid = if (dnaWeightSum > 0.0) {
                TrackCuration.TrackDna(
                    energyScore = (dnaEnergySum / dnaWeightSum).toFloat().coerceIn(0.05f, 0.95f),
                    acousticScore = (dnaAcousticSum / dnaWeightSum).toFloat().coerceIn(0.05f, 0.95f),
                    danceabilityScore = (dnaDanceSum / dnaWeightSum).toFloat().coerceIn(0.10f, 0.90f),
                    moodValence = (dnaValenceSum / dnaWeightSum).toFloat().coerceIn(0.05f, 0.95f)
                )
            } else {
                null
            }

            return TasteProfile(
                affinities = affinities,
                coListen = coListen,
                hourly = hourly,
                playCounts = plays,
                skipCounts = skips,
                artistAffinities = artistAffinities,
                artistDisplayNames = artistDisplayNames,
                genreAffinities = genreAffinities,
                genreDisplayNames = genreDisplayNames,
                eraAffinities = eraAffinities,
                lastPlayedAt = lastPlayedAt,
                sessionArtistSkips = sessionArtistSkips,
                sessionGenreSkips = sessionGenreSkips,
                preferredDna = centroid,
                builtAtMs = nowMs,
                hasHistory = events.isNotEmpty()
            )
        }

        private fun artistKeys(rawArtist: String): List<Pair<String, String>> {
            if (MetadataNormalizer.isPlaceholderField(rawArtist)) return emptyList()
            return ArtistSplitter.split(rawArtist)
                .mapNotNull { part ->
                    val cleaned = MetadataNormalizer.cleanDisplayText(part)
                    if (cleaned.isEmpty() || MetadataNormalizer.isPlaceholderField(cleaned)) return@mapNotNull null
                    val key = MetadataNormalizer.normalizeForComparison(cleaned)
                    if (key.isEmpty()) null else key to cleaned
                }
                .distinctBy { it.first }
        }

        private fun saturate(raw: Double, scale: Double): Double {
            if (raw == 0.0) return 0.0
            val absVal = abs(raw)
            val norm = absVal / (absVal + scale)
            return if (raw > 0.0) norm else -norm
        }
    }
}
