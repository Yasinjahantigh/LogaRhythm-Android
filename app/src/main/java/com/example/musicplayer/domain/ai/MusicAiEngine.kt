package com.example.musicplayer.domain.ai

import com.example.musicplayer.domain.model.Track
import kotlin.math.abs

/**
 * Lightweight on-device curation based on local metadata. Scores are heuristic estimates from
 * tags and text metadata, duration, and stable IDs; no audio is uploaded or analyzed as a signal.
 */
object MusicAiEngine {

    data class TrackDna(
        val energyScore: Float,       // 0.0 (calm/chill) to 1.0 (intense/fast)
        val acousticScore: Float,     // estimated acoustic vs synthetic
        val danceabilityScore: Float, // heuristic rhythm/energy estimate
        val moodValence: Float        // 0.0 (melancholic/dark) to 1.0 (uplifting/bright)
    )

    enum class AiMood(
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
        val genre = normalizeForMood(track.genre)
        val descriptiveText = "$title $album $genre"

        var energy = 0.50f
        if (genre.containsAny("rock", "metal", "edm", "electronic", "dance", "techno", "rap", "راک", "متال", "الکترونیک", "رپ") ||
            descriptiveText.containsAny("remix", "fast", "pump", "workout", "ریمیکس", "انرژی", "رقص", "تند", "باشگاه", "پارتی")
        ) {
            energy += 0.35f
        } else if (genre.containsAny("classical", "ambient", "acoustic", "jazz", "lo-fi", "folk", "کلاسیک", "آکوستیک") ||
            descriptiveText.containsAny("slow", "sleep", "calm", "piano", "آرام", "خواب", "ملایم", "لالایی", "بی کلام", "پیانو")
        ) {
            energy -= 0.30f
        }

        if (track.durationMs in 90_000..210_000) energy += 0.05f
        else if (track.durationMs > 360_000) energy -= 0.10f

        var valence = 0.50f
        if (descriptiveText.containsAny("happy", "sun", "love", "dance", "light", "joy", "شاد", "خوشحال", "عشق", "عاشق", "لبخند", "بهار", "رقص", "امید")) {
            valence += 0.30f
        } else if (descriptiveText.containsAny("sad", "dark", "rain", "night", "alone", "cry", "غمگین", "غم", "باران", "شب", "تنهایی", "گریه", "دلتنگ", "شکست", "اشک")) {
            valence -= 0.30f
        }

        // An acoustic signal is now derived from actual tags and recognizable text, not just one genre label.
        val acoustic = when {
            genre.containsAny("acoustic", "classical", "folk", "jazz", "ambient", "آکوستیک", "کلاسیک") ||
                descriptiveText.containsAny("acoustic", "unplugged", "piano", "guitar", "instrumental", "آکوستیک", "پیانو", "گیتار", "بی کلام") -> 0.88f
            genre.containsAny("edm", "electronic", "techno", "house", "metal", "الکترونیک", "متال") ||
                descriptiveText.containsAny("remix", "synth", "ریمیکس") -> 0.12f
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
        val energyDiff = abs(dna1.energyScore - dna2.energyScore)
        val valenceDiff = abs(dna1.moodValence - dna2.moodValence)
        val acousticDiff = abs(dna1.acousticScore - dna2.acousticScore)
        val durationDiff = abs(t1.durationMs - t2.durationMs).toFloat() / 300_000f

        var distance = energyDiff * 0.40f + valenceDiff * 0.30f + acousticDiff * 0.10f + durationDiff.coerceAtMost(1f) * 0.20f
        if (t1.artist.isNotBlank() && t1.artist.equals(t2.artist, ignoreCase = true)) distance -= 0.12f
        if (t1.genre.isNotBlank() && t1.genre.equals(t2.genre, ignoreCase = true)) distance -= 0.10f
        return distance.coerceAtLeast(0.01f)
    }

    /** Recommends a relevance-first, artist-diverse radio queue. */
    fun recommendNextTracks(
        currentTrack: Track,
        allTracks: List<Track>,
        limit: Int = 10,
        excludedTrackIds: Set<String> = emptySet()
    ): List<Track> {
        if (limit <= 0) return emptyList()
        val candidates = allTracks.distinctBy { it.id }
            .filter { it.id != currentTrack.id && it.id !in excludedTrackIds }
            .map { candidate -> candidate to calculateSimilarity(currentTrack, candidate) }
            .sortedWith(compareBy<Pair<Track, Float>> { it.second }.thenBy { it.first.title.lowercase() })
        return selectDiverse(candidates, limit)
    }

    /** Generates a mood queue using energy, valence, acoustic character, and estimated danceability. */
    fun generateMoodPlaylist(mood: AiMood, allTracks: List<Track>, count: Int = 15): List<Track> {
        if (allTracks.isEmpty() || count <= 0) return emptyList()
        val ranked = allTracks.distinctBy { it.id }.map { track ->
            val dna = analyzeTrackDna(track)
            val distance = abs(dna.energyScore - mood.targetEnergy) * 0.42f +
                abs(dna.moodValence - mood.targetValence) * 0.30f +
                abs(dna.acousticScore - mood.targetAcoustic) * 0.16f +
                abs(dna.danceabilityScore - mood.targetDanceability) * 0.12f
            track to distance
        }.sortedWith(compareBy<Pair<Track, Float>> { it.second }.thenBy { it.first.title.lowercase() })

        // Diversity is applied only to a relevant candidate pool, so novelty cannot swamp mood fit.
        return selectDiverse(ranked.take((count * 2).coerceAtMost(ranked.size)), count)
    }

    /** Greedy sequencing avoids consecutive same-artist/album tracks when similarly good alternatives exist. */
    private fun selectDiverse(ranked: List<Pair<Track, Float>>, limit: Int): List<Track> {
        if (limit <= 0 || ranked.isEmpty()) return emptyList()
        val remaining = ranked.toMutableList()
        val selected = mutableListOf<Pair<Track, Float>>()

        while (remaining.isNotEmpty() && selected.size < limit) {
            var bestIndex = 0
            var bestAdjustedScore = Float.MAX_VALUE
            remaining.forEachIndexed { index, candidate ->
                val track = candidate.first
                val previous = selected.lastOrNull()?.first
                var penalty = 0f
                if (previous != null && hasSameKnownMetadata(previous.artist, track.artist)) penalty += 0.12f
                if (previous != null && hasSameKnownMetadata(previous.album, track.album)) penalty += 0.05f
                val artistRepeats = selected.count { hasSameKnownMetadata(it.first.artist, track.artist) }
                if (artistRepeats > 0) penalty += (artistRepeats * 0.035f).coerceAtMost(0.14f)
                val adjustedScore = candidate.second + penalty
                if (adjustedScore < bestAdjustedScore) {
                    bestAdjustedScore = adjustedScore
                    bestIndex = index
                }
            }
            selected += remaining.removeAt(bestIndex)
        }
        return selected.map { it.first }
    }

    private fun hasSameKnownMetadata(left: String, right: String): Boolean =
        left.isNotBlank() && right.isNotBlank() &&
            !left.equals("unknown artist", ignoreCase = true) &&
            !right.equals("unknown artist", ignoreCase = true) &&
            !left.equals("unknown album", ignoreCase = true) &&
            !right.equals("unknown album", ignoreCase = true) &&
            left.trim().equals(right.trim(), ignoreCase = true)

    private fun normalizeForMood(value: String): String = value.lowercase()
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('ؤ', 'و')
        .replace('ئ', 'ی')
        .replace('ـ', ' ')
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace('‌', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { contains(it) }
}
