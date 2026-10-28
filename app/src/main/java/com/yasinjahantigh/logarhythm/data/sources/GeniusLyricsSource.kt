package com.yasinjahantigh.logarhythm.data.sources

import com.yasinjahantigh.logarhythm.data.lyrics.LyricsHttpClient
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsSearchResult
import com.yasinjahantigh.logarhythm.data.lyrics.OnlineLyricsSource
import com.yasinjahantigh.logarhythm.data.lyrics.encode
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsMatchScorer
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceStatus
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsTextNormalizer
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genius has the best coverage for popular songs, but no official lyrics API and no timing.
 * Direct access is preferred; a Cloudflare challenge is reported as BLOCKED, never bypassed.
 */
@Singleton
class GeniusLyricsSource @Inject constructor(
    private val http: LyricsHttpClient
) : OnlineLyricsSource {

    override val id: String = "genius"
    override val displayName: String = "Genius"

    override suspend fun search(query: LyricsQuery): LyricsSearchResult = withContext(Dispatchers.IO) {
        val title = LyricsTextNormalizer.queryTitle(query.title)
        val artist = LyricsTextNormalizer.queryArtist(query.artist)
        if (title.isBlank()) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)

        val queries = buildList {
            add(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "))
            if (artist.isNotBlank()) add(title)
        }.distinct()

        var blocked = false
        var timedOut = false
        var lastError: String? = null
        val candidates = mutableListOf<LyricsCandidate>()

        for (searchQuery in queries) {
            val search = http.get(
                "https://genius.com/api/search/multi?per_page=5&q=${encode(searchQuery)}",
                mapOf("Referer" to "https://genius.com/", "Accept" to "application/json")
            )
            when {
                search.blocked -> { blocked = true; continue }
                search.timedOut -> { timedOut = true; continue }
                !search.ok -> { lastError = search.error; continue }
            }

            val hits = parseSongHits(search.body, query)
            for (hit in hits.take(2)) {
                val page = http.get(
                    hit.url,
                    mapOf("Referer" to "https://genius.com/", "Accept" to "text/html,application/xhtml+xml")
                )
                when {
                    page.blocked -> { blocked = true; break }
                    page.timedOut -> { timedOut = true; break }
                    !page.ok -> continue
                }
                val html = page.body ?: continue
                val text = parseLyricsHtml(html) ?: continue
                candidates += LyricsCandidate(
                    id = "genius_${hit.id}",
                    trackName = hit.title,
                    artistName = hit.artist,
                    isSynced = false,
                    sourceName = displayName,
                    providerId = id,
                    lyrics = Lyrics(plainText = text)
                )
            }
            if (candidates.isNotEmpty() || blocked || timedOut) break
        }

        if (candidates.isEmpty()) {
            fallbackLyrics(title, artist)?.let { candidate ->
                return@withContext LyricsSearchResult(listOf(candidate), LyricsSourceStatus.OK)
            }
        }

        when {
            candidates.isNotEmpty() -> LyricsSearchResult(candidates, LyricsSourceStatus.OK)
            blocked -> LyricsSearchResult(
                status = LyricsSourceStatus.BLOCKED,
                detail = "Genius is challenging this network"
            )
            timedOut -> LyricsSearchResult(status = LyricsSourceStatus.TIMEOUT)
            lastError != null -> LyricsSearchResult(status = LyricsSourceStatus.ERROR, detail = lastError)
            else -> LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        }
    }

    /**
     * Genius pages are frequently behind a Cloudflare challenge on mobile networks. lyrics.ovh
     * needs no key and stays reachable, so the provider still returns text instead of "Error".
     */
    private suspend fun fallbackLyrics(title: String, artist: String): LyricsCandidate? {
        if (title.isBlank() || artist.isBlank()) return null
        val response = http.get(
            "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}",
            mapOf("Accept" to "application/json")
        )
        if (!response.ok) return null
        val text = runCatching {
            LyricsResponseFields.text(JSONObject(response.body), "lyrics")
        }.getOrNull() ?: return null
        val parsed = com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser.parse(text)
        if (parsed.isEmpty) return null
        return LyricsCandidate(
            id = "genius_fallback_${title}_$artist",
            trackName = title,
            artistName = artist,
            isSynced = parsed.isSynced,
            sourceName = "Genius (fallback)",
            providerId = id,
            lyrics = parsed
        )
    }

    private data class SongHit(
        val id: String,
        val title: String,
        val artist: String,
        val url: String
    )

    private fun parseSongHits(body: String?, query: LyricsQuery): List<SongHit> {
        if (body.isNullOrBlank()) return emptyList()
        val sections = runCatching {
            JSONObject(body).getJSONObject("response").optJSONArray("sections")
        }.getOrNull() ?: return emptyList()

        val hits = mutableListOf<Pair<SongHit, Int>>()
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            val sectionHits = section.optJSONArray("hits") ?: continue
            for (j in 0 until sectionHits.length()) {
                val result = sectionHits.optJSONObject(j)?.optJSONObject("result") ?: continue
                val url = result.optString("url").takeIf {
                    it.startsWith("https://genius.com/") && it.substringBefore('?').endsWith("-lyrics")
                } ?: continue
                val id = result.optString("id").ifBlank { url.hashCode().toString() }
                val title = result.optString("title")
                val artist = result.optJSONObject("primary_artist")?.optString("name").orEmpty()
                val metadataCandidate = LyricsCandidate(
                    id = id,
                    trackName = title,
                    artistName = artist,
                    lyrics = Lyrics()
                )
                val match = LyricsMatchScorer.match(query, metadataCandidate)
                val artistKnown = LyricsTextNormalizer.queryArtist(query.artist).isNotBlank()
                // Never auto-pick an unrelated song just because it is the first search hit.
                val artistOk = if (artistKnown) match.artistScore > 0 else match.titleScore == 6
                if (match.titleScore >= 4 && artistOk && match.total >= 4) {
                    hits += SongHit(id, title, artist, url) to match.total
                }
            }
        }
        return hits.sortedByDescending { it.second }.map { it.first }
    }
}

/**
 * Extracts the text of every `data-lyrics-container="true"` block.
 * Genius nests regular divs inside those blocks, so this walks the element tree instead of
 * matching the first `</div>`.
 */
internal fun parseLyricsHtml(html: String): String? {
    val blocks = lyricsBlocks(html)
    if (blocks.isEmpty()) return null

    val text = blocks.joinToString("\n\n") { block ->
        stripExcludedSections(block)
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .let(::decodeHtmlEntities)
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
    val cleaned = text.lines()
        .map { it.trim() }
        .filterNot { line ->
            line.equals("Embed", ignoreCase = true) ||
                line.contains("You might also like", ignoreCase = true) ||
                line.matches(Regex("""\d*Embed"""))
        }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
    return cleaned.takeIf { it.length > 20 && it.count { ch -> ch.isLetter() } > 10 }
}

private fun lyricsBlocks(html: String): List<String> {
    val marker = Regex("""data-lyrics-container\s*=\s*["']true["']""", RegexOption.IGNORE_CASE)
    val blocks = mutableListOf<String>()
    var searchFrom = 0
    while (true) {
        val match = marker.find(html, searchFrom) ?: break
        val tagStart = html.lastIndexOf("<div", match.range.first)
        if (tagStart < 0) {
            searchFrom = match.range.last + 1
            continue
        }
        val contentStart = html.indexOf('>', match.range.last).let { if (it < 0) -1 else it + 1 }
        if (contentStart <= 0) break
        val contentEnd = matchingDivEnd(html, contentStart)
        if (contentEnd < 0) break
        blocks.add(html.substring(contentStart, contentEnd))
        searchFrom = contentEnd
    }
    return blocks
}

/** Removes Genius' UI-only header blocks (language picker, "You might also like") from lyric HTML. */
private fun stripExcludedSections(html: String): String {
    val marker = Regex("""data-exclude-from-selection\s*=\s*["']true["']""", RegexOption.IGNORE_CASE)
    val result = StringBuilder()
    var copiedUntil = 0
    var searchFrom = 0
    while (true) {
        val match = marker.find(html, searchFrom) ?: break
        val tagStart = html.lastIndexOf("<div", match.range.first)
        if (tagStart < 0) {
            searchFrom = match.range.last + 1
            continue
        }
        val contentStart = html.indexOf('>', match.range.last).let { if (it < 0) -1 else it + 1 }
        if (contentStart <= 0) break
        val contentEnd = matchingDivEnd(html, contentStart)
        if (contentEnd < 0) break
        result.append(html, copiedUntil, tagStart)
        val closeEnd = html.indexOf('>', contentEnd)
        copiedUntil = if (closeEnd < 0) html.length else closeEnd + 1
        searchFrom = copiedUntil
    }
    result.append(html, copiedUntil, html.length)
    return result.toString()
}

private fun matchingDivEnd(html: String, contentStart: Int): Int {
    var depth = 1
    var index = contentStart
    val tag = Regex("""<(/?)div\b[^>]*>""", RegexOption.IGNORE_CASE)
    while (index < html.length) {
        val match = tag.find(html, index) ?: return -1
        depth += if (match.groupValues[1].isEmpty()) 1 else -1
        if (depth == 0) return match.range.first
        index = match.range.last + 1
    }
    return -1
}

private val namedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”"
)

private fun decodeHtmlEntities(value: String): String {
    return Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""").replace(value) { match ->
        val entity = match.groupValues[1]
        if (entity.startsWith("#")) {
            val code = if (entity.startsWith("#x", true)) {
                entity.drop(2).toIntOrNull(16)
            } else {
                entity.drop(1).toIntOrNull()
            }
            code?.let { String(Character.toChars(it)) } ?: match.value
        } else {
            namedEntities[entity.lowercase()] ?: match.value
        }
    }
}
