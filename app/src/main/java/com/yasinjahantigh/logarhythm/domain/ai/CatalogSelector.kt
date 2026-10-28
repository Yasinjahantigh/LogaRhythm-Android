package com.yasinjahantigh.logarhythm.domain.ai

import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlin.math.abs

/**
 * Keeps the prompt small on big libraries. A 4,000-song library is never sent whole: the songs
 * named by the request go first, then a diverse sample fills the remaining slots.
 */
object CatalogSelector {

    const val MAX_PROMPT_TRACKS = 150
    const val MAX_MIX_CANDIDATES = 80
    private const val MAX_KEYWORD_MATCHES = 80
    private const val MAX_PER_ARTIST = 3

    fun forPlaylist(
        library: List<Track>,
        prompt: String,
        limit: Int = MAX_PROMPT_TRACKS
    ): List<Track> {
        val unique = library.distinctBy { it.id }
        if (unique.size <= limit) return unique

        val tokens = promptTokens(prompt)
        val ranked = unique
            .map { track -> track to promptScore(track, tokens) }
            .sortedWith(
                compareByDescending<Pair<Track, Int>> { it.second }
                    .thenBy { it.first.id.hashCode() }
            )
        val matches = ranked
            .filter { it.second > 0 }
            .take(MAX_KEYWORD_MATCHES)
            .map { it.first }
        val matchIds = matches.mapTo(HashSet()) { it.id }
        val rest = ranked.map { it.first }.filter { it.id !in matchIds }
        return (matches + diversifyByArtist(rest, limit))
            .distinctBy { it.id }
            .take(limit)
    }

    fun forMix(
        library: List<Track>,
        base: List<Track>,
        limit: Int = MAX_MIX_CANDIDATES
    ): List<Track> {
        val baseIds = base.mapTo(HashSet()) { it.id }
        val candidates = library.filter { it.id !in baseIds }.distinctBy { it.id }
        if (candidates.size <= limit) return candidates

        val artistCounts = base.map { it.artist.lowercase() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
        val genres = base.map { it.genre.lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
        val years = base.map { it.year }.filter { it > 0 }

        val ranked = candidates.map { track ->
            var score = (artistCounts[track.artist.lowercase()] ?: 0) * 4
            if (track.genre.isNotBlank() && track.genre.lowercase() in genres) score += 3
            if (years.isNotEmpty() && track.year > 0) {
                val distance = years.minOf { abs(it - track.year) }
                score += when {
                    distance <= 1 -> 2
                    distance <= 3 -> 1
                    else -> 0
                }
            }
            track to score
        }.sortedWith(
            compareByDescending<Pair<Track, Int>> { it.second }
                .thenBy { it.first.id.hashCode() }
        )

        return diversifyByArtist(ranked.map { it.first }, limit)
    }

    private fun diversifyByArtist(tracks: List<Track>, limit: Int): List<Track> {
        val picked = ArrayList<Track>(limit)
        val overflow = ArrayList<Track>()
        val perArtist = HashMap<String, Int>()
        for (track in tracks) {
            if (picked.size >= limit) return picked
            val artist = track.artist.lowercase()
            val count = perArtist[artist] ?: 0
            if (count < MAX_PER_ARTIST) {
                picked.add(track)
                perArtist[artist] = count + 1
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

    private fun promptTokens(prompt: String): Set<String> =
        prompt.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()

    private fun promptScore(track: Track, tokens: Set<String>): Int {
        if (tokens.isEmpty()) return 0
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.lowercase()
        val genre = track.genre.lowercase()
        return tokens.fold(0) { total, token ->
            total + when {
                artist.contains(token) -> 5
                title.contains(token) -> 4
                genre.contains(token) -> 3
                album.contains(token) -> 1
                else -> 0
            }
        }
    }
}
