package com.yasinjahantigh.logarhythm.domain.smart

import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.Track
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

enum class ListeningTimeSlot {
    MORNING,    // 05:00 - 11:59
    AFTERNOON,  // 12:00 - 16:59
    EVENING,    // 17:00 - 21:59
    NIGHT;      // 22:00 - 04:59

    companion object {
        fun fromHour(hour: Int): ListeningTimeSlot {
            val h = ((hour % 24) + 24) % 24
            return when (h) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..21 -> EVENING
                else -> NIGHT
            }
        }
    }
}

data class TasteInsights(
    val analyzedPlays: Int,
    val confidencePercent: Int,
    val topArtists: List<String>,
    val topGenres: List<String>,
    val activeTimeSlot: ListeningTimeSlot
)

/**
 * Genuine on-device behavioral taste learning engine.
 *
 * Uses strictly real listening behavior — ZERO fake audio-frequency/DSP/hash heuristics:
 * 1. Completion-ratio & skip-timing reinforcement per track, artist, album, genre, and decade.
 * 2. Exponential recency decay (28-day half-life) so preferences evolve naturally over time.
 * 3. Bayesian smoothing so consistently finished artists beat one-off accidental plays.
 * 4. Circadian time-of-day profiling (Morning / Afternoon / Evening / Night) learned from real play timestamps.
 * 5. Session Markov transition matrix (tracks & artists heard back-to-back within 20 min) for smooth flow.
 * 6. Habitual-skip filtering + 18-hour freshness cooldown + artist diversity cap.
 */
class UserTasteModel private constructor(
    private val trackScore: Map<String, Double>,
    private val artistScore: Map<String, Double>,
    private val artistEvidence: Map<String, Double>,
    private val artistDisplay: Map<String, String>,
    private val albumScore: Map<String, Double>,
    private val genreScore: Map<String, Double>,
    private val genreEvidence: Map<String, Double>,
    private val genreDisplay: Map<String, String>,
    private val decadeScore: Map<Int, Double>,
    private val slotTrackScore: Map<ListeningTimeSlot, Map<String, Double>>,
    private val slotArtistScore: Map<ListeningTimeSlot, Map<String, Double>>,
    private val slotGenreScore: Map<ListeningTimeSlot, Map<String, Double>>,
    private val artistTransitions: Map<String, Map<String, Double>>,
    private val trackTransitions: Map<String, Map<String, Double>>,
    private val completedCounts: Map<String, Int>,
    private val skipCounts: Map<String, Int>,
    private val lastPlayedAt: Map<String, Long>,
    private val favoriteIds: Set<String>,
    val insights: TasteInsights,
    private val nowMs: Long
) {
    fun isHabituallySkipped(trackId: String): Boolean {
        val skips = skipCounts[trackId] ?: 0
        val completed = completedCounts[trackId] ?: 0
        return skips >= 2 && skips > completed * 2 && trackId !in favoriteIds
    }

    fun completedPlayCount(trackId: String): Int = completedCounts[trackId] ?: 0

    fun artistAffinity(rawArtist: String): Double {
        val keys = artistKeys(rawArtist)
        if (keys.isEmpty()) return 0.0
        return keys.maxOf { (k, _) ->
            val sum = artistScore[k] ?: 0.0
            val ev = artistEvidence[k] ?: 0.0
            // Bayesian-smoothed mean * log evidence weight
            val mean = sum / (ev + ARTIST_PRIOR)
            mean * (1.0 + 0.55 * ln(1.0 + ev))
        }
    }

    fun genreAffinity(rawGenre: String): Double {
        val key = MetadataNormalizer.genreGroupKey(rawGenre)
        if (key.isEmpty()) return 0.0
        val sum = genreScore[key] ?: 0.0
        val ev = genreEvidence[key] ?: 0.0
        val mean = sum / (ev + GENRE_PRIOR)
        return mean * (1.0 + 0.40 * ln(1.0 + ev))
    }

    fun albumAffinity(rawAlbum: String): Double {
        if (MetadataNormalizer.isPlaceholderField(rawAlbum)) return 0.0
        val key = MetadataNormalizer.normalizeForComparison(rawAlbum)
        if (key.isEmpty()) return 0.0
        return saturate(albumScore[key] ?: 0.0, scale = 3.0)
    }

    fun decadeAffinity(year: Int): Double {
        if (year !in 1920..2100) return 0.0
        return saturate(decadeScore[(year / 10) * 10] ?: 0.0, scale = 5.0)
    }

    /** Freshness cooldown: smoothly penalizes tracks played in the last 18 hours so mixes stay lively. */
    fun freshnessPenalty(trackId: String): Double {
        val last = lastPlayedAt[trackId] ?: return 0.0
        val ageMs = nowMs - last
        if (ageMs < 0L || ageMs >= COOLDOWN_MS) return 0.0
        return (1.0 - ageMs.toDouble() / COOLDOWN_MS) * 1.15
    }

    fun directTrackAffinity(trackId: String): Double =
        saturate(trackScore[trackId] ?: 0.0, scale = 2.8)

    fun slotAffinity(track: Track, slot: ListeningTimeSlot): Double {
        val tScore = saturate(slotTrackScore[slot]?.get(track.id) ?: 0.0, scale = 1.8)
        val aScore = artistKeys(track.artist).maxOfOrNull { (k, _) ->
            saturate(slotArtistScore[slot]?.get(k) ?: 0.0, scale = 2.5)
        } ?: 0.0
        val gKey = MetadataNormalizer.genreGroupKey(track.genre)
        val gScore = if (gKey.isNotEmpty()) saturate(slotGenreScore[slot]?.get(gKey) ?: 0.0, scale = 3.0) else 0.0
        return tScore * 1.7 + aScore * 1.15 + gScore * 0.65
    }

    /** Behavioral score for the general "Picked For You" adaptive mix. */
    fun forYouScore(track: Track): Double {
        if (isHabituallySkipped(track.id)) return -10.0
        val direct = directTrackAffinity(track.id) * 2.2
        val artist = artistAffinity(track.artist) * 1.85
        val album = albumAffinity(track.album) * 0.70
        val genre = genreAffinity(track.genre) * 0.90
        val era = decadeAffinity(track.year) * 0.35
        val favBoost = if (track.id in favoriteIds) 0.85 else 0.0
        val currentSlotBoost = slotAffinity(track, insights.activeTimeSlot) * 0.45
        val cooldown = freshnessPenalty(track.id)
        return direct + artist + album + genre + era + favBoost + currentSlotBoost - cooldown
    }

    /** Behavioral score for the "For Right Now" time-of-day adaptive mix. */
    fun timeOfDayScore(track: Track, slot: ListeningTimeSlot = insights.activeTimeSlot): Double {
        if (isHabituallySkipped(track.id)) return -10.0
        val slotMatch = slotAffinity(track, slot)
        val baseTaste = forYouScore(track)
        return slotMatch * 2.6 + baseTaste * 0.55 - freshnessPenalty(track.id) * 0.6
    }

    /**
     * Behavioral score for "Hidden Gems For You":
     * surfaces unplayed or rarely played tracks in the user's library whose artist/album/genre/era
     * strongly match what the user has actually loved.
     */
    fun hiddenGemScore(track: Track): Double {
        if (isHabituallySkipped(track.id)) return -10.0
        val plays = completedPlayCount(track.id)
        if (plays > 2) return -5.0 // Already heavily played — not a hidden gem
        val artist = artistAffinity(track.artist) * 2.1
        val album = albumAffinity(track.album) * 1.1
        val genre = genreAffinity(track.genre) * 1.0
        val era = decadeAffinity(track.year) * 0.45
        val discoveryBonus = when (plays) {
            0 -> 0.55
            1 -> 0.25
            else -> 0.0
        }
        val favBoost = if (track.id in favoriteIds && plays == 0) 0.4 else 0.0
        return artist + album + genre + era + discoveryBonus + favBoost - freshnessPenalty(track.id)
    }

    /** Session transition affinity between [prev] and [candidate] for smooth playlist ordering. */
    fun transitionBonus(prev: Track, candidate: Track): Double {
        val directCo = trackTransitions[prev.id]?.get(candidate.id) ?: 0.0
        val directNorm = saturate(directCo, scale = 1.5) * 0.85
        val prevArtists = artistKeys(prev.artist).map { it.first }
        val candArtists = artistKeys(candidate.artist).map { it.first }
        var artistTrans = 0.0
        for (pa in prevArtists) {
            val map = artistTransitions[pa] ?: continue
            for (ca in candArtists) {
                val w = map[ca] ?: 0.0
                if (w > artistTrans) artistTrans = w
            }
        }
        val artistNorm = saturate(artistTrans, scale = 2.0) * 0.45
        return directNorm + artistNorm
    }

    companion object {
        private const val HOUR_MS = 60L * 60 * 1000
        private const val DAY_MS = 24L * HOUR_MS
        private const val HALF_LIFE_DAYS = 28.0
        private const val COOLDOWN_MS = 18L * HOUR_MS
        private const val SESSION_GAP_MS = 20L * 60 * 1000
        private const val ARTIST_PRIOR = 1.5
        private const val GENRE_PRIOR = 2.0

        fun build(
            tracks: List<Track>,
            events: List<PlayEvent>,
            favoriteIds: Set<String>,
            nowMs: Long = System.currentTimeMillis(),
            utcOffsetMs: Int = TimeZone.getDefault().getOffset(nowMs)
        ): UserTasteModel {
            val byId = tracks.associateBy { it.id }
            val currentHour = localHour(nowMs, utcOffsetMs)
            val currentSlot = ListeningTimeSlot.fromHour(currentHour)

            val trackScore = HashMap<String, Double>()
            val artistScore = HashMap<String, Double>()
            val artistEvidence = HashMap<String, Double>()
            val artistDisplay = HashMap<String, String>()
            val albumScore = HashMap<String, Double>()
            val genreScore = HashMap<String, Double>()
            val genreEvidence = HashMap<String, Double>()
            val genreDisplay = HashMap<String, String>()
            val decadeScore = HashMap<Int, Double>()

            val slotTrack = ListeningTimeSlot.entries.associateWith { HashMap<String, Double>() }
            val slotArtist = ListeningTimeSlot.entries.associateWith { HashMap<String, Double>() }
            val slotGenre = ListeningTimeSlot.entries.associateWith { HashMap<String, Double>() }

            val completedCounts = HashMap<String, Int>()
            val skipCounts = HashMap<String, Int>()
            val lastPlayedAt = HashMap<String, Long>()

            fun recency(ts: Long): Double {
                val ageDays = ((nowMs - ts).coerceAtLeast(0L)).toDouble() / DAY_MS
                return 0.5.pow(ageDays / HALF_LIFE_DAYS)
            }

            fun applyTransfer(
                track: Track,
                signedDelta: Double,
                evidenceWeight: Double,
                slot: ListeningTimeSlot? = null,
                positiveSlotDelta: Double = 0.0
            ) {
                val aKeys = artistKeys(track.artist)
                if (aKeys.isNotEmpty()) {
                    val share = signedDelta / aKeys.size
                    val evShare = evidenceWeight / aKeys.size
                    for ((k, disp) in aKeys) {
                        artistScore[k] = (artistScore[k] ?: 0.0) + share
                        artistEvidence[k] = (artistEvidence[k] ?: 0.0) + evShare
                        artistDisplay.putIfAbsent(k, disp)
                        if (slot != null && positiveSlotDelta > 0.0) {
                            slotArtist[slot]!![k] = (slotArtist[slot]!![k] ?: 0.0) + positiveSlotDelta / aKeys.size
                        }
                    }
                }
                if (!MetadataNormalizer.isPlaceholderField(track.album)) {
                    val albKey = MetadataNormalizer.normalizeForComparison(track.album)
                    if (albKey.isNotEmpty()) {
                        albumScore[albKey] = (albumScore[albKey] ?: 0.0) + signedDelta
                    }
                }
                val gKey = MetadataNormalizer.genreGroupKey(track.genre)
                if (gKey.isNotEmpty()) {
                    genreScore[gKey] = (genreScore[gKey] ?: 0.0) + signedDelta
                    genreEvidence[gKey] = (genreEvidence[gKey] ?: 0.0) + evidenceWeight
                    genreDisplay.putIfAbsent(gKey, MetadataNormalizer.canonicalizeGenre(track.genre, track.genre))
                    if (slot != null && positiveSlotDelta > 0.0) {
                        slotGenre[slot]!![gKey] = (slotGenre[slot]!![gKey] ?: 0.0) + positiveSlotDelta
                    }
                }
                if (track.year in 1920..2100) {
                    val dec = (track.year / 10) * 10
                    decadeScore[dec] = (decadeScore[dec] ?: 0.0) + signedDelta
                }
            }

            var analyzedPlays = 0
            for (event in events) {
                val r = recency(event.timestamp)
                val track = byId[event.trackId]
                val ratio = if (track != null && track.durationMs > 0L && event.listenedMs > 0L) {
                    (event.listenedMs.toDouble() / track.durationMs.toDouble()).coerceIn(0.0, 1.0)
                } else null

                if (event.skipped) {
                    skipCounts[event.trackId] = (skipCounts[event.trackId] ?: 0) + 1
                    val skipWeight = when {
                        ratio != null && ratio < 0.25 -> -1.40
                        ratio != null && ratio < 0.50 -> -0.95
                        else -> -0.65
                    }
                    val delta = skipWeight * r
                    trackScore[event.trackId] = (trackScore[event.trackId] ?: 0.0) + delta
                    if (track != null) {
                        applyTransfer(track, delta * 0.75, evidenceWeight = r * 0.5)
                    }
                } else {
                    analyzedPlays++
                    completedCounts[event.trackId] = (completedCounts[event.trackId] ?: 0) + 1
                    val completionWeight = when {
                        ratio == null -> 1.0
                        ratio >= 0.80 -> 1.25
                        ratio >= 0.45 -> 0.80
                        else -> 0.45
                    }
                    val delta = completionWeight * r
                    trackScore[event.trackId] = (trackScore[event.trackId] ?: 0.0) + delta
                    val prevLast = lastPlayedAt[event.trackId]
                    if (prevLast == null || event.timestamp > prevLast) {
                        lastPlayedAt[event.trackId] = event.timestamp
                    }
                    val slot = ListeningTimeSlot.fromHour(localHour(event.timestamp, utcOffsetMs))
                    slotTrack[slot]!![event.trackId] = (slotTrack[slot]!![event.trackId] ?: 0.0) + delta
                    if (track != null) {
                        applyTransfer(track, delta, evidenceWeight = r, slot = slot, positiveSlotDelta = delta)
                    }
                }
            }

            // Explicit favorites boost track + gently transfer to artist/album/genre
            for (favId in favoriteIds) {
                trackScore[favId] = (trackScore[favId] ?: 0.0) + 1.85
                val track = byId[favId]
                if (track != null) {
                    applyTransfer(track, signedDelta = 1.15, evidenceWeight = 0.8)
                }
            }

            // Session Markov transitions (back-to-back completed listens within 20 minutes)
            val trackTransitions = HashMap<String, HashMap<String, Double>>()
            val artistTransitions = HashMap<String, HashMap<String, Double>>()
            val completedOrdered = events.filter { !it.skipped }.sortedBy { it.timestamp }
            for (i in 1 until completedOrdered.size) {
                val prev = completedOrdered[i - 1]
                val next = completedOrdered[i]
                if (prev.trackId == next.trackId) continue
                if (next.timestamp - prev.timestamp > SESSION_GAP_MS) continue
                val w = recency(next.timestamp)
                trackTransitions.getOrPut(prev.trackId) { HashMap() }.merge(next.trackId, w, Double::plus)
                trackTransitions.getOrPut(next.trackId) { HashMap() }.merge(prev.trackId, w * 0.65, Double::plus)

                val prevTrack = byId[prev.trackId]
                val nextTrack = byId[next.trackId]
                if (prevTrack != null && nextTrack != null) {
                    val pArtists = artistKeys(prevTrack.artist).map { it.first }
                    val nArtists = artistKeys(nextTrack.artist).map { it.first }
                    for (pa in pArtists) {
                        for (na in nArtists) {
                            if (pa == na) continue
                            artistTransitions.getOrPut(pa) { HashMap() }.merge(na, w, Double::plus)
                            artistTransitions.getOrPut(na) { HashMap() }.merge(pa, w * 0.65, Double::plus)
                        }
                    }
                }
            }

            // Build transparent TasteInsights for the UI
            val totalSignal = analyzedPlays + favoriteIds.size * 2
            val confidence = ((1.0 - exp(-totalSignal.toDouble() / 25.0)) * 100.0).toInt().coerceIn(0, 99)

            val topArtists = artistScore.entries
                .asSequence()
                .filter { it.value > 0.2 }
                .sortedByDescending { (k, sum) ->
                    val ev = artistEvidence[k] ?: 1.0
                    (sum / (ev + ARTIST_PRIOR)) * (1.0 + 0.55 * ln(1.0 + ev))
                }
                .take(4)
                .mapNotNull { artistDisplay[it.key] }
                .toList()

            val topGenres = genreScore.entries
                .asSequence()
                .filter { it.value > 0.2 }
                .sortedByDescending { (k, sum) ->
                    val ev = genreEvidence[k] ?: 1.0
                    (sum / (ev + GENRE_PRIOR)) * (1.0 + 0.40 * ln(1.0 + ev))
                }
                .take(3)
                .mapNotNull { genreDisplay[it.key] }
                .toList()

            val insights = TasteInsights(
                analyzedPlays = analyzedPlays,
                confidencePercent = confidence,
                topArtists = topArtists,
                topGenres = topGenres,
                activeTimeSlot = currentSlot
            )

            return UserTasteModel(
                trackScore = trackScore,
                artistScore = artistScore,
                artistEvidence = artistEvidence,
                artistDisplay = artistDisplay,
                albumScore = albumScore,
                genreScore = genreScore,
                genreEvidence = genreEvidence,
                genreDisplay = genreDisplay,
                decadeScore = decadeScore,
                slotTrackScore = slotTrack,
                slotArtistScore = slotArtist,
                slotGenreScore = slotGenre,
                artistTransitions = artistTransitions,
                trackTransitions = trackTransitions,
                completedCounts = completedCounts,
                skipCounts = skipCounts,
                lastPlayedAt = lastPlayedAt,
                favoriteIds = favoriteIds,
                insights = insights,
                nowMs = nowMs
            )
        }

        private fun localHour(timestampMs: Long, utcOffsetMs: Int): Int =
            ((((timestampMs + utcOffsetMs) / HOUR_MS) % 24 + 24) % 24).toInt()

        internal fun artistKeys(rawArtist: String): List<Pair<String, String>> {
            if (MetadataNormalizer.isPlaceholderField(rawArtist)) return emptyList()
            return ArtistSplitter.split(rawArtist)
                .mapNotNull { part ->
                    val clean = MetadataNormalizer.cleanDisplayText(part)
                    if (clean.isEmpty() || MetadataNormalizer.isPlaceholderField(clean)) return@mapNotNull null
                    val key = MetadataNormalizer.normalizeForComparison(clean)
                    if (key.isEmpty()) null else key to clean
                }
                .distinctBy { it.first }
        }

        private fun saturate(raw: Double, scale: Double): Double {
            if (raw == 0.0) return 0.0
            val a = abs(raw)
            val norm = a / (a + scale)
            return if (raw > 0.0) norm else -norm
        }
    }
}

/**
 * Generates adaptive playlists from [UserTasteModel] with smooth session-aware sequencing
 * and artist-diversity caps.
 */
object UserTasteEngine {

    private const val DEFAULT_LIMIT = 35

    fun buildForYou(
        tracks: List<Track>,
        model: UserTasteModel,
        limit: Int = DEFAULT_LIMIT
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val scored = tracks
            .filterNot { model.isHabituallySkipped(it.id) }
            .map { it to model.forYouScore(it) }
            .sortedWith(
                compareByDescending<Pair<Track, Double>> { it.second }
                    .thenByDescending { it.first.dateAdded }
            )
        return sequenceWithFlowAndDiversity(scored, model, limit)
    }

    fun buildTimeOfDayVibe(
        tracks: List<Track>,
        model: UserTasteModel,
        slot: ListeningTimeSlot = model.insights.activeTimeSlot,
        limit: Int = DEFAULT_LIMIT
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val scored = tracks
            .filterNot { model.isHabituallySkipped(it.id) }
            .map { it to model.timeOfDayScore(it, slot) }
            .sortedWith(
                compareByDescending<Pair<Track, Double>> { it.second }
                    .thenByDescending { it.first.dateAdded }
            )
        return sequenceWithFlowAndDiversity(scored, model, limit)
    }

    fun buildHiddenGems(
        tracks: List<Track>,
        model: UserTasteModel,
        limit: Int = DEFAULT_LIMIT
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val candidates = tracks.filter { !model.isHabituallySkipped(it.id) && model.completedPlayCount(it.id) <= 2 }
        if (candidates.isEmpty()) return emptyList()
        val scored = candidates
            .map { it to model.hiddenGemScore(it) }
            .sortedWith(
                compareByDescending<Pair<Track, Double>> { it.second }
                    .thenByDescending { it.first.dateAdded }
            )
        return sequenceWithFlowAndDiversity(scored, model, limit)
    }

    /**
     * Greedy transition-aware sequencer:
     * - Caps tracks per primary artist so no single artist floods the playlist.
     * - Avoids back-to-back tracks by the same artist when alternatives exist.
     * - Rewards pairs that have strong learned co-listening transition affinity.
     */
    private fun sequenceWithFlowAndDiversity(
        scored: List<Pair<Track, Double>>,
        model: UserTasteModel,
        limit: Int
    ): List<Track> {
        if (scored.isEmpty()) return emptyList()
        val pool = scored.take(min(scored.size, limit * 3)).toMutableList()
        val maxPerArtist = (limit / 5).coerceAtLeast(3)
        val result = ArrayList<Track>(min(limit, pool.size))
        val artistCounts = HashMap<String, Int>()

        fun primaryArtistKey(track: Track): String =
            UserTasteModel.artistKeys(track.artist).firstOrNull()?.first
                ?: MetadataNormalizer.normalizeForComparison(track.artist)

        while (result.size < limit && pool.isNotEmpty()) {
            val prev = result.lastOrNull()
            val prevArtist = prev?.let(::primaryArtistKey).orEmpty()

            var bestIdx = -1
            var bestValue = Double.NEGATIVE_INFINITY
            for (i in pool.indices) {
                val (track, baseScore) = pool[i]
                val aKey = primaryArtistKey(track)
                val count = if (aKey.isNotEmpty()) artistCounts[aKey] ?: 0 else 0
                if (aKey.isNotEmpty() && count >= maxPerArtist) continue

                var effective = baseScore
                if (prev != null) {
                    effective += model.transitionBonus(prev, track)
                    if (aKey.isNotEmpty() && aKey == prevArtist) {
                        effective -= 1.35 // Avoid consecutive same-artist tracks
                    }
                }
                if (effective > bestValue) {
                    bestValue = effective
                    bestIdx = i
                }
            }

            if (bestIdx < 0) {
                // Fallback if every remaining track hit the artist cap
                val next = pool.removeAt(0).first
                result += next
            } else {
                val chosen = pool.removeAt(bestIdx).first
                result += chosen
                val aKey = primaryArtistKey(chosen)
                if (aKey.isNotEmpty()) {
                    artistCounts[aKey] = (artistCounts[aKey] ?: 0) + 1
                }
            }
        }
        return result
    }
}
