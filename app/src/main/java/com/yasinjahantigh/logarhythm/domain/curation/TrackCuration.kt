package com.yasinjahantigh.logarhythm.domain.curation

import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import kotlin.math.abs

/**
 * Lightweight on-device curation based on local metadata. Scores are heuristic estimates from
 * tags and text metadata, duration, and stable IDs; no audio is uploaded or analyzed as a signal.
 */
object TrackCuration {

    data class TrackDna(
        val energyScore: Float,       // 0.0 (calm/chill) to 1.0 (intense/fast)
        val acousticScore: Float,     // estimated acoustic vs synthetic
        val danceabilityScore: Float, // heuristic rhythm/energy estimate
        val moodValence: Float        // 0.0 (melancholic/dark) to 1.0 (uplifting/bright)
    )

    enum class Mood(
        val targetEnergy: Float,
        val targetValence: Float,
        val targetAcoustic: Float,
        val targetDanceability: Float
    ) {
        LATE_NIGHT_CHILL(0.25f, 0.40f, 0.55f, 0.25f),
        DEEP_FOCUS(0.40f, 0.50f, 0.65f, 0.25f),
        ENERGY_BOOST(0.85f, 0.85f, 0.15f, 0.85f),
        MELANCHOLY_RAIN(0.30f, 0.25f, 0.45f, 0.20f),
        ACOUSTIC_MORNING(0.50f, 0.70f, 0.90f, 0.45f)
    }

    /** Derives deterministic mood/energy estimates from title, album, genre, and duration. */
    fun analyzeTrackDna(track: Track): TrackDna {
        val title = normalizeForMood(track.title)
        val album = normalizeForMood(track.album)
        val rawGenre = track.genre
        val genre: String
        val genreFamily: String
        if (rawGenre.isBlank()) {
            genre = ""
            genreFamily = ""
        } else {
            genre = normalizeForMood(MetadataNormalizer.canonicalizeGenre(rawGenre, rawGenre))
            genreFamily = MetadataNormalizer.genreGroupKey(rawGenre)
        }
        val descriptiveText = "$title $album $genre"

        var energy = 0.50f
        if ((genreFamily.isNotEmpty() && genreFamily in HIGH_ENERGY_FAMILIES) ||
            (genre.isNotEmpty() && genre.containsAnyOf(HIGH_ENERGY_GENRE_KW)) ||
            descriptiveText.containsAnyOf(HIGH_ENERGY_DESC_KW)
        ) {
            energy += 0.35f
        } else if ((genreFamily.isNotEmpty() && genreFamily in CALM_FAMILIES) ||
            (genre.isNotEmpty() && genre.containsAnyOf(CALM_GENRE_KW)) ||
            descriptiveText.containsAnyOf(CALM_DESC_KW)
        ) {
            energy -= 0.30f
        }

        if (track.durationMs in 90_000..210_000) energy += 0.05f
        else if (track.durationMs > 360_000) energy -= 0.10f

        var valence = 0.50f
        if (descriptiveText.containsAnyOf(POSITIVE_VALENCE_KW)) {
            valence += 0.30f
        } else if (descriptiveText.containsAnyOf(NEGATIVE_VALENCE_KW)) {
            valence -= 0.30f
        }

        val acoustic = when {
            (genreFamily.isNotEmpty() && genreFamily in ACOUSTIC_FAMILIES) ||
                (genre.isNotEmpty() && genre.containsAnyOf(ACOUSTIC_GENRE_KW)) ||
                descriptiveText.containsAnyOf(ACOUSTIC_DESC_KW) -> 0.88f
            genreFamily == "electronic" || genreFamily == "metal" ||
                (genre.isNotEmpty() && genre.containsAnyOf(ELECTRONIC_GENRE_KW)) ||
                descriptiveText.containsAnyOf(ELECTRONIC_DESC_KW) -> 0.12f
            else -> 0.38f
        }

        // Small stable variation makes tracks with identical metadata rank deterministically but not identically.
        val hashVariance = (abs((track.id + track.title).hashCode()) % 100) / 500f - 0.10f
        energy = (energy + hashVariance).coerceIn(0.05f, 0.95f)
        valence = (valence + hashVariance).coerceIn(0.05f, 0.95f)
        val danceability = (energy * 0.70f + valence * 0.30f).coerceIn(0.10f, 0.90f)

        return TrackDna(
            energyScore = energy,
            acousticScore = acoustic,
            danceabilityScore = danceability,
            moodValence = valence
        )
    }

    /** Lower score means a closer metadata match. */
    fun calculateSimilarity(t1: Track, t2: Track): Float {
        if (t1.id == t2.id) return 0f
        val dna1 = analyzeTrackDna(t1)
        val dna2 = analyzeTrackDna(t2)
        return calculateSimilarityCore(
            t1 = t1,
            dna1 = dna1,
            t1ArtistNorm = normalizedKnownField(t1.artist),
            t1GenreKey = MetadataNormalizer.genreGroupKey(t1.genre),
            t1GenreNorm = normalizedKnownField(t1.genre),
            t2 = t2,
            dna2 = dna2
        )
    }

    private fun calculateSimilarityCore(
        t1: Track,
        dna1: TrackDna,
        t1ArtistNorm: String?,
        t1GenreKey: String,
        t1GenreNorm: String?,
        t2: Track,
        dna2: TrackDna
    ): Float {
        val energyDiff = abs(dna1.energyScore - dna2.energyScore)
        val valenceDiff = abs(dna1.moodValence - dna2.moodValence)
        val acousticDiff = abs(dna1.acousticScore - dna2.acousticScore)
        val durationDiff = abs(t1.durationMs - t2.durationMs).toFloat() / 300_000f

        var distance = energyDiff * 0.40f + valenceDiff * 0.30f + acousticDiff * 0.10f + durationDiff.coerceAtMost(1f) * 0.20f
        if (t1ArtistNorm != null && t1ArtistNorm == normalizedKnownField(t2.artist)) {
            distance -= 0.12f
        }
        val genreKey2 = MetadataNormalizer.genreGroupKey(t2.genre)
        if (t1GenreKey.isNotEmpty() && t1GenreKey == genreKey2) {
            distance -= 0.12f
        } else if (t1GenreNorm != null && t1GenreNorm == normalizedKnownField(t2.genre)) {
            distance -= 0.10f
        }
        if (t1.year > 1900 && t2.year > 1900 && abs(t1.year - t2.year) <= 5) {
            distance -= 0.04f
        }
        return distance.coerceAtLeast(0.01f)
    }

    /** Recommends a relevance-first, artist-diverse radio queue. */
    fun recommendNextTracks(
        currentTrack: Track,
        allTracks: List<Track>,
        limit: Int = 10,
        excludedTrackIds: Set<String> = emptySet(),
        profile: TasteProfile? = null
    ): List<Track> {
        if (limit <= 0 || allTracks.isEmpty()) return emptyList()
        val seedDna = analyzeTrackDna(currentTrack)
        val seedArtistNorm = normalizedKnownField(currentTrack.artist)
        val seedGenreKey = MetadataNormalizer.genreGroupKey(currentTrack.genre)
        val seedGenreNorm = normalizedKnownField(currentTrack.genre)

        val seenIds = HashSet<String>(allTracks.size)
        val scored = ArrayList<Pair<Track, Float>>(allTracks.size)
        for (i in allTracks.indices) {
            val candidate = allTracks[i]
            val id = candidate.id
            if (!seenIds.add(id)) continue
            if (id == currentTrack.id || id in excludedTrackIds) continue
            val candDna = analyzeTrackDna(candidate)
            var distance = calculateSimilarityCore(
                t1 = currentTrack,
                dna1 = seedDna,
                t1ArtistNorm = seedArtistNorm,
                t1GenreKey = seedGenreKey,
                t1GenreNorm = seedGenreNorm,
                t2 = candidate,
                dna2 = candDna
            )
            if (profile != null) {
                // Real listening beats metadata guesses: songs heard together pull closer,
                // taste-aligned & loved tracks get boosted, and skipped artists/tracks sink.
                val sessionPen = profile.sessionPenalty(candidate)
                val tasteMatch = profile.tasteMatchScoreWithPrecomputed(candidate, candDna, sessionPen)
                distance -= profile.coListenStrength(currentTrack.id, id).toFloat() * 0.45f
                distance -= profile.affinity(id).coerceIn(-2.0, 3.0).toFloat() * 0.025f
                distance -= tasteMatch.coerceIn(-1.5, 3.0).toFloat() * 0.045f
                distance += sessionPen.toFloat() * 0.35f
                if (profile.isDisliked(id)) distance += 0.5f
            }
            scored.add(candidate to distance)
        }
        scored.sortWith(compareBy<Pair<Track, Float>> { it.second }.thenBy { it.first.title.lowercase() })
        return selectDiverse(scored, limit)
    }

    /** Generates a mood queue using energy, valence, acoustic character, and estimated danceability. */
    fun generateMoodPlaylist(
        mood: Mood,
        allTracks: List<Track>,
        count: Int = 15,
        profile: TasteProfile? = null
    ): List<Track> {
        if (allTracks.isEmpty() || count <= 0) return emptyList()
        val seenIds = HashSet<String>(allTracks.size)
        val ranked = ArrayList<Pair<Track, Float>>(allTracks.size)
        for (i in allTracks.indices) {
            val track = allTracks[i]
            if (!seenIds.add(track.id)) continue
            val dna = analyzeTrackDna(track)
            val distance = abs(dna.energyScore - mood.targetEnergy) * 0.42f +
                abs(dna.moodValence - mood.targetValence) * 0.30f +
                abs(dna.acousticScore - mood.targetAcoustic) * 0.16f +
                abs(dna.danceabilityScore - mood.targetDanceability) * 0.12f
            val tasteAdjusted = if (profile == null) distance else {
                val sessionPen = profile.sessionPenalty(track)
                val tasteMatch = profile.tasteMatchScoreWithPrecomputed(track, dna, sessionPen)
                distance - profile.affinity(track.id).coerceIn(-2.0, 3.0).toFloat() * 0.015f -
                    tasteMatch.coerceIn(-2.0, 3.5).toFloat() * 0.04f +
                    sessionPen.toFloat() * 0.35f +
                    (if (profile.isDisliked(track.id)) 0.4f else 0f)
            }
            ranked.add(track to tasteAdjusted)
        }
        ranked.sortWith(compareBy<Pair<Track, Float>> { it.second }.thenBy { it.first.title.lowercase() })

        // Diversity is applied only to a relevant candidate pool, so novelty cannot swamp mood fit.
        val poolLimit = (count * 2).coerceAtMost(ranked.size)
        return selectDiverse(if (poolLimit == ranked.size) ranked else ranked.subList(0, poolLimit), count)
    }

    private class DiverseCandidate(
        val track: Track,
        val baseScore: Float,
        val artistNorm: String?,
        val albumNorm: String?
    )

    /** Greedy sequencing avoids consecutive same-artist/album tracks when similarly good alternatives exist. */
    private fun selectDiverse(ranked: List<Pair<Track, Float>>, limit: Int): List<Track> {
        if (limit <= 0 || ranked.isEmpty()) return emptyList()
        val remaining = ArrayList<DiverseCandidate>(ranked.size)
        for (i in ranked.indices) {
            val (track, score) = ranked[i]
            remaining.add(
                DiverseCandidate(
                    track = track,
                    baseScore = score,
                    artistNorm = normalizedKnownField(track.artist),
                    albumNorm = normalizedKnownField(track.album)
                )
            )
        }
        val selected = ArrayList<Track>(minOf(limit, remaining.size))
        val artistCounts = HashMap<String, Int>(limit * 2)
        var prevArtistNorm: String? = null
        var prevAlbumNorm: String? = null

        while (remaining.isNotEmpty() && selected.size < limit) {
            var bestIndex = 0
            var bestAdjustedScore = Float.MAX_VALUE
            for (index in remaining.indices) {
                val candidate = remaining[index]
                // remaining is sorted ascending by baseScore and penalty is always >= 0f,
                // so once baseScore >= bestAdjustedScore no later candidate can improve bestAdjustedScore.
                if (candidate.baseScore >= bestAdjustedScore) break
                var penalty = 0f
                val candArtist = candidate.artistNorm
                if (candArtist != null) {
                    if (prevArtistNorm != null && prevArtistNorm == candArtist) penalty += 0.12f
                    val artistRepeats = artistCounts[candArtist] ?: 0
                    if (artistRepeats > 0) penalty += (artistRepeats * 0.035f).coerceAtMost(0.14f)
                }
                val candAlbum = candidate.albumNorm
                if (prevAlbumNorm != null && candAlbum != null && prevAlbumNorm == candAlbum) {
                    penalty += 0.05f
                }
                val adjustedScore = candidate.baseScore + penalty
                if (adjustedScore < bestAdjustedScore) {
                    bestAdjustedScore = adjustedScore
                    bestIndex = index
                }
            }
            val chosen = remaining.removeAt(bestIndex)
            selected.add(chosen.track)
            prevArtistNorm = chosen.artistNorm
            prevAlbumNorm = chosen.albumNorm
            if (prevArtistNorm != null) {
                artistCounts[prevArtistNorm] = (artistCounts[prevArtistNorm] ?: 0) + 1
            }
        }
        return selected
    }

    private fun normalizedKnownField(value: String): String? {
        if (MetadataNormalizer.isPlaceholderField(value)) return null
        val norm = MetadataNormalizer.normalizeForComparison(value)
        return norm.ifEmpty { null }
    }

    private fun normalizeForMood(value: String): String =
        MetadataNormalizer.normalizeForComparison(value)

    private fun String.containsAnyOf(keywords: Array<String>): Boolean {
        for (i in keywords.indices) {
            if (contains(keywords[i])) return true
        }
        return false
    }

    private val HIGH_ENERGY_FAMILIES = setOf("rock", "metal", "electronic", "rap")
    private val CALM_FAMILIES = setOf("classical", "chill", "acoustic", "jazz", "instrumental")
    private val ACOUSTIC_FAMILIES = setOf("acoustic", "classical", "traditional", "jazz", "chill", "instrumental", "nostalgic")

    private val HIGH_ENERGY_GENRE_KW = arrayOf("rock", "metal", "edm", "electronic", "dance", "techno", "rap", "راک", "متال", "الکترونیک", "رپ")
    private val HIGH_ENERGY_DESC_KW = arrayOf("remix", "fast", "pump", "workout", "ریمیکس", "انرژی", "رقص", "تند", "باشگاه", "پارتی")
    private val CALM_GENRE_KW = arrayOf("classical", "ambient", "acoustic", "jazz", "lo-fi", "folk", "کلاسیک", "آکوستیک")
    private val CALM_DESC_KW = arrayOf("slow", "sleep", "calm", "piano", "آرام", "خواب", "ملایم", "لالایی", "بی کلام", "پیانو")
    private val POSITIVE_VALENCE_KW = arrayOf("happy", "sun", "love", "dance", "light", "joy", "شاد", "خوشحال", "عشق", "عاشق", "لبخند", "بهار", "رقص", "امید")
    private val NEGATIVE_VALENCE_KW = arrayOf("sad", "dark", "rain", "night", "alone", "cry", "غمگین", "غم", "باران", "شب", "تنهایی", "گریه", "دلتنگ", "شکست", "اشک")
    private val ACOUSTIC_GENRE_KW = arrayOf("acoustic", "classical", "folk", "jazz", "ambient", "آکوستیک", "کلاسیک")
    private val ACOUSTIC_DESC_KW = arrayOf("acoustic", "unplugged", "piano", "guitar", "instrumental", "آکوستیک", "پیانو", "گیتار", "بی کلام")
    private val ELECTRONIC_GENRE_KW = arrayOf("edm", "electronic", "techno", "house", "metal", "الکترونیک", "متال")
    private val ELECTRONIC_DESC_KW = arrayOf("remix", "synth", "ریمیکس")
}
