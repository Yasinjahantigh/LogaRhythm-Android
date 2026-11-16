package com.yasinjahantigh.logarhythm.domain.ai

import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import kotlin.math.abs

/**
 * Keeps the prompt small on big libraries. A 4,000-song library is never sent whole: the songs
 * named by the request go first, then a taste-aware, artist-diverse stratified sample fills
 * the remaining slots while avoiding recent AI/mix repeats and skipped artists.
 */
object CatalogSelector {

    const val MAX_PROMPT_TRACKS = 150
    const val MAX_MIX_CANDIDATES = 80
    private const val MAX_KEYWORD_MATCHES = 80
    private const val MAX_PER_ARTIST = 3

    fun forPlaylist(
        library: List<Track>,
        prompt: String,
        limit: Int = MAX_PROMPT_TRACKS,
        profile: TasteProfile? = null,
        recentTrackIds: Set<String> = emptySet(),
        seed: Long = 0L
    ): List<Track> {
        val unique = library.distinctBy { it.id }
        if (unique.size <= limit) return unique

        val nonDisliked = if (profile == null) unique else unique.filterNot { profile.isDisliked(it.id) }
        val pool = if (nonDisliked.size >= limit) nonDisliked else unique

        val tokens = promptTokens(prompt)
        val promptGenreKeys = tokens.mapNotNull { token ->
            MetadataNormalizer.genreGroupKey(token).takeIf { it.isNotEmpty() }
        }.toSet()

        fun commonAdjustment(track: Track): Double {
            val recentPenalty = if (track.id in recentTrackIds) 3.0 else 0.0
            val sessionPenalty = (profile?.sessionPenalty(track) ?: 0.0) * 4.0
            return (profile?.tasteMatchScore(track) ?: 0.0) - recentPenalty - sessionPenalty
        }

        val matches = if (tokens.isEmpty()) {
            emptyList()
        } else {
            pool.asSequence()
                .map { track -> track to promptScore(track, tokens, promptGenreKeys) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<Track, Int>> { it.second }
                        .thenByDescending { commonAdjustment(it.first) + deterministicJitter(it.first.id, seed) * 0.5 }
                        .thenBy { it.first.id }
                )
                .take(MAX_KEYWORD_MATCHES)
                .map { it.first }
                .toList()
        }

        val matchIds = matches.mapTo(HashSet()) { it.id }
        val rest = pool.filter { it.id !in matchIds }
        val remainingBudget = (limit - matches.size).coerceAtLeast(0)
        if (remainingBudget == 0 || rest.isEmpty()) {
            return diversifyByArtist(matches, limit)
        }

        val sampledRest = if (profile != null && profile.hasHistory) {
            val pickedIds = HashSet<String>(matchIds)
            val restPicked = ArrayList<Track>(remainingBudget)

            // 1) High taste affinity matches (up to 50% of remaining budget).
            val tasteQuota = (remainingBudget * 5) / 10
            val tasteSorted = rest.sortedWith(
                compareByDescending<Track> {
                    profile.tasteMatchScore(it) * 1.4 -
                        (if (it.id in recentTrackIds) 3.0 else 0.0) -
                        profile.sessionPenalty(it) * 4.0 +
                        deterministicJitter(it.id, seed) * 0.75
                }.thenBy { it.id }
            )
            for (t in diversifyByArtist(tasteSorted, tasteQuota)) {
                if (pickedIds.add(t.id)) restPicked.add(t)
            }

            // 2) Unplayed / discovery tracks matching listener's broad taste (~30% of remaining budget).
            val discoveryQuota = ((remainingBudget - restPicked.size).coerceAtLeast(0) * 6) / 10
            if (discoveryQuota > 0) {
                val unplayedSorted = rest
                    .asSequence()
                    .filter { it.id !in pickedIds && profile.playCount(it.id) == 0 }
                    .sortedWith(
                        compareByDescending<Track> {
                            profile.tasteMatchScore(it) * 0.9 -
                                (if (it.id in recentTrackIds) 3.0 else 0.0) -
                                profile.sessionPenalty(it) * 4.0 +
                                deterministicJitter(it.id, seed + 17L)
                        }.thenBy { it.id }
                    )
                    .toList()
                for (t in diversifyByArtist(unplayedSorted, discoveryQuota)) {
                    if (pickedIds.add(t.id)) restPicked.add(t)
                }
            }

            // 3) Rotating variety across the rest of the catalog.
            val varietyQuota = (remainingBudget - restPicked.size).coerceAtLeast(0)
            if (varietyQuota > 0) {
                val varietySorted = rest
                    .filter { it.id !in pickedIds }
                    .sortedWith(
                        compareByDescending<Track> {
                            deterministicJitter(it.id, seed + 31L) * 2.0 -
                                (if (it.id in recentTrackIds) 3.0 else 0.0) -
                                profile.sessionPenalty(it) * 4.0
                        }.thenBy { it.id }
                    )
                restPicked.addAll(diversifyByArtist(varietySorted, varietyQuota))
            }
            restPicked
        } else {
            val rotated = rest.sortedWith(
                compareByDescending<Track> {
                    deterministicJitter(it.id, seed) * 2.0 - (if (it.id in recentTrackIds) 3.0 else 0.0)
                }.thenBy { it.id }
            )
            diversifyByArtist(rotated, remainingBudget)
        }

        return (matches + diversifyByArtist(sampledRest, remainingBudget))
            .distinctBy { it.id }
            .take(limit)
    }

    fun forMix(
        library: List<Track>,
        base: List<Track>,
        limit: Int = MAX_MIX_CANDIDATES,
        profile: TasteProfile? = null,
        recentTrackIds: Set<String> = emptySet(),
        seed: Long = 0L
    ): List<Track> {
        val baseIds = base.mapTo(HashSet()) { it.id }
        val candidates = library.filter { it.id !in baseIds }.distinctBy { it.id }
        if (candidates.size <= limit) return candidates

        val nonDisliked = if (profile == null) candidates else candidates.filterNot { profile.isDisliked(it.id) }
        val pool = if (nonDisliked.size >= limit) nonDisliked else candidates

        val artistCounts = base
            .flatMap { track -> ArtistSplitter.split(track.artist) }
            .mapNotNull { MetadataNormalizer.normalizeForComparison(it).takeIf(String::isNotEmpty) }
            .groupingBy { it }
            .eachCount()
        val genreKeys = base
            .mapNotNull { MetadataNormalizer.genreGroupKey(it.genre).takeIf(String::isNotEmpty) }
            .toSet()
        val years = base.map { it.year }.filter { it > 0 }
        val baseDna = if (base.isEmpty()) null else {
            val dnas = base.map { TrackCuration.analyzeTrackDna(it) }
            TrackCuration.TrackDna(
                energyScore = dnas.map { it.energyScore }.average().toFloat(),
                acousticScore = dnas.map { it.acousticScore }.average().toFloat(),
                danceabilityScore = dnas.map { it.danceabilityScore }.average().toFloat(),
                moodValence = dnas.map { it.moodValence }.average().toFloat()
            )
        }

        val ranked = pool.map { track ->
            val trackArtists = ArtistSplitter.split(track.artist)
                .mapNotNull { MetadataNormalizer.normalizeForComparison(it).takeIf(String::isNotEmpty) }
            var score = (trackArtists.maxOfOrNull { artistCounts[it] ?: 0 } ?: 0) * 4.0

            val gKey = MetadataNormalizer.genreGroupKey(track.genre)
            if (gKey.isNotEmpty() && gKey in genreKeys) score += 3.5

            if (years.isNotEmpty() && track.year > 0) {
                val distance = years.minOf { abs(it - track.year) }
                score += when {
                    distance <= 1 -> 2.0
                    distance <= 3 -> 1.0
                    else -> 0.0
                }
            }
            if (baseDna != null) {
                val dna = TrackCuration.analyzeTrackDna(track)
                val dist = abs(dna.energyScore - baseDna.energyScore) +
                    abs(dna.moodValence - baseDna.moodValence) +
                    abs(dna.acousticScore - baseDna.acousticScore)
                score += ((1.0 - dist / 1.5).coerceIn(0.0, 1.0)) * 2.0
            }
            if (profile != null) {
                score += profile.tasteMatchScore(track) * 1.3
                score -= profile.sessionPenalty(track) * 4.0
            }
            if (track.id in recentTrackIds) score -= 3.5
            score += deterministicJitter(track.id, seed) * 1.4
            track to score
        }.sortedWith(
            compareByDescending<Pair<Track, Double>> { it.second }
                .thenBy { it.first.id }
        )

        return diversifyByArtist(ranked.map { it.first }, limit)
    }

    private fun diversifyByArtist(tracks: List<Track>, limit: Int): List<Track> {
        if (limit <= 0) return emptyList()
        val picked = ArrayList<Track>(minOf(limit, tracks.size))
        val overflow = ArrayList<Track>()
        val perArtist = HashMap<String, Int>()
        for (track in tracks) {
            if (picked.size >= limit) return picked
            val primary = ArtistSplitter.split(track.artist).firstOrNull().orEmpty()
            val normArtist = MetadataNormalizer.normalizeForComparison(primary)
            val key = if (normArtist.isEmpty() || MetadataNormalizer.isPlaceholderField(primary)) {
                "track:${track.id}"
            } else {
                normArtist
            }
            val count = perArtist[key] ?: 0
            if (count < MAX_PER_ARTIST) {
                picked.add(track)
                perArtist[key] = count + 1
            } else {
                overflow.add(track)
            }
        }
        for (track in overflow) {
            if (picked.size >= limit) break
            picked.add(track)
        }
        return picked
    }

    private fun deterministicJitter(trackId: String, seed: Long): Double {
        var h = trackId.hashCode().toLong() xor (seed * 0x517CC1B727220A95L)
        h = (h xor (h ushr 30)) * 0x2545F4914F6CDD1DL
        h = (h xor (h ushr 27)) * 0x4CF5AD432745937FL
        h = h xor (h ushr 31)
        return ((h ushr 11) and 0x1FFFFFL).toDouble() / 2097151.0
    }

    private fun promptTokens(prompt: String): Set<String> =
        MetadataNormalizer.normalizeForComparison(prompt)
            .split(' ')
            .filter { it.length >= 2 }
            .toSet()

    private fun promptScore(track: Track, tokens: Set<String>, promptGenreKeys: Set<String>): Int {
        if (tokens.isEmpty()) return 0
        val title = MetadataNormalizer.normalizeForComparison(track.title)
        val artist = MetadataNormalizer.normalizeForComparison(track.artist)
        val album = MetadataNormalizer.normalizeForComparison(track.album)
        val genre = MetadataNormalizer.normalizeForComparison(track.genre)
        val trackGenreKey = MetadataNormalizer.genreGroupKey(track.genre)
        var score = tokens.fold(0) { total, token ->
            if (token.length < 3) return@fold total
            total + when {
                artist.contains(token) -> 5
                title.contains(token) -> 4
                genre.contains(token) -> 3
                album.contains(token) -> 1
                else -> 0
            }
        }
        if (trackGenreKey.isNotEmpty() && trackGenreKey in promptGenreKeys) {
            score += 4
        }
        return score
    }
}
