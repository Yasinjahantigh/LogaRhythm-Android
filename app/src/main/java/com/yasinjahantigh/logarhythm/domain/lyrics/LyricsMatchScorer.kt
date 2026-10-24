package com.yasinjahantigh.logarhythm.domain.lyrics

import com.yasinjahantigh.logarhythm.core.utils.SearchUtils
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import java.util.Locale

/** What a lyrics lookup is looking for. Album and duration are optional match hints. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L
)

/** How well a candidate's metadata matches the query. Higher is better. */
data class LyricsMatchScore(
    val total: Int,
    val titleScore: Int,
    val artistScore: Int,
    val durationScore: Int,
    val albumScore: Int,
    val confident: Boolean
)

/**
 * Normalizes noisy local tags and provider metadata before comparing them. Persian/Arabic glyph
 * normalization is shared with search through [SearchUtils].
 */
object LyricsTextNormalizer {

    private val BRACKET_NOISE = Regex(
        """[\(\[\{][^\)\]\}]*(official|lyric|music\s*video|video|audio|visuali[sz]er|remix|remaster|live|prod\.?|feat\.?|ft\.?|nightcore|sped\s*up|slowed|bass\s*boost|color\s*coded)[^\)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )
    private val LEADING_TRACK = Regex("""^\d{1,3}[\s.\-_]+""")
    private val FILE_EXTENSION = Regex("""\.(mp3|m4a|flac|wav|ogg|oga|opus|aac|webm)$""", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex("""\s*(?:,|;|&|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
    private val ARTIST_SUFFIX = Regex("""\s*[-–—]\s*topic$""", RegexOption.IGNORE_CASE)

    fun queryTitle(raw: String): String = raw
        .replace('_', ' ')
        .replace(FILE_EXTENSION, "")
        .replace(LEADING_TRACK, "")
        .replace(BRACKET_NOISE, " ")
        .replace(Regex("""\s*[\(\[]\s*[\)\]]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun queryArtist(raw: String): String {
        val cleaned = raw.replace(ARTIST_SUFFIX, "").trim()
        if (cleaned.isBlank() || cleaned.equals("<unknown>", true) ||
            cleaned.equals("unknown", true) || cleaned.equals("unknown artist", true)
        ) return ""
        return cleaned.split(ARTIST_SPLIT).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    fun normalize(raw: String): String =
        SearchUtils.normalize(raw)
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun tokens(raw: String): Set<String> =
        normalize(raw).split(' ').filter { it.isNotBlank() }.toSet()
}

/** Scores provider candidates against the local track without letting an unrelated song win. */
object LyricsMatchScorer {

    fun match(query: LyricsQuery, candidate: LyricsCandidate): LyricsMatchScore {
        val wantedTitle = LyricsTextNormalizer.normalize(LyricsTextNormalizer.queryTitle(query.title))
        val foundTitle = LyricsTextNormalizer.normalize(candidate.trackName)
        val wantedArtist = LyricsTextNormalizer.normalize(LyricsTextNormalizer.queryArtist(query.artist))
        val foundArtist = LyricsTextNormalizer.normalize(candidate.artistName)

        val titleScore = titleScore(wantedTitle, foundTitle)
        if (titleScore == 0) {
            return LyricsMatchScore(0, 0, 0, 0, 0, confident = false)
        }
        val artistScore = artistScore(wantedArtist, foundArtist)
        val durationScore = durationScore(query.durationMs, candidate.durationMs)
        val albumScore = albumScore(query.album, candidate.albumName)
        val total = titleScore + artistScore + durationScore + albumScore

        val artistUsable = wantedArtist.isBlank() || artistScore > 0
        val confident = if (candidate.isSynced) {
            total >= 6 && artistUsable
        } else {
            // Plain lyrics have no timing safety net, so demand a strong title + artist match.
            total >= 8 && titleScore >= 4 && artistUsable
        }
        return LyricsMatchScore(total, titleScore, artistScore, durationScore, albumScore, confident)
    }

    private fun titleScore(wanted: String, found: String): Int {
        if (wanted.isBlank() || found.isBlank()) return 0
        if (wanted == found) return 6
        if (wanted.length >= 4 && found.length >= 4 &&
            (wanted.contains(found) || found.contains(wanted))
        ) return 4
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }.toSet()
        val foundTokens = found.split(' ').filter { it.isNotBlank() }.toSet()
        if (wantedTokens.isEmpty() || foundTokens.isEmpty()) return 0
        val overlap = wantedTokens.intersect(foundTokens).size.toFloat() /
            minOf(wantedTokens.size, foundTokens.size).toFloat()
        return when {
            wantedTokens.all { it in foundTokens } || foundTokens.all { it in wantedTokens } -> 3
            overlap >= 0.6f -> 2
            else -> 0
        }
    }

    private fun artistScore(wanted: String, found: String): Int {
        if (wanted.isBlank() || found.isBlank()) return 0
        if (wanted == found) return 4
        val wantedTokens = wanted.split(' ').filter { it.isNotBlank() }.toSet()
        val foundTokens = found.split(' ').filter { it.isNotBlank() }.toSet()
        if (wantedTokens.any { it.length >= 3 && it in foundTokens } ||
            foundTokens.any { it.length >= 3 && it in wantedTokens }
        ) return 2
        if (wanted.length >= 4 && found.length >= 4 && (wanted.contains(found) || found.contains(wanted))) return 1
        return 0
    }

    private fun durationScore(wantedMs: Long, foundMs: Long): Int {
        if (wantedMs <= 0L || foundMs <= 0L) return 0
        val diff = kotlin.math.abs(wantedMs - foundMs)
        return when {
            diff <= 3_000L -> 3
            diff <= 8_000L -> 2
            diff <= 20_000L -> 1
            else -> 0
        }
    }

    private fun albumScore(wanted: String, found: String): Int {
        val a = LyricsTextNormalizer.normalize(wanted)
        val b = LyricsTextNormalizer.normalize(found)
        if (a.isBlank() || b.isBlank()) return 0
        return if (a == b || a.contains(b) || b.contains(a)) 1 else 0
    }
}
