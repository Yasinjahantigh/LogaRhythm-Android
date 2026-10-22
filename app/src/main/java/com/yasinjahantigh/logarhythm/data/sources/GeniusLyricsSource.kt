package com.yasinjahantigh.logarhythm.data.sources

import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.source.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-resort lyrics provider using Genius' public search endpoint and song pages.
 * Genius only offers plain (unsynced) lyrics, so this source is consulted after every
 * synced provider and after lyrics.ovh.
 */
@Singleton
class GeniusLyricsSource @Inject constructor() : LyricsSource {

    override val name: String = "genius"

    companion object {
        private const val SEARCH_URL = "https://genius.com/api/search/multi"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 8000
    }

    override suspend fun getLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val title = sanitizeTitle(track.title)
        val artist = sanitizeArtist(track.artist)
        if (title.isBlank()) return@withContext null

        val songUrl = findSongUrl(title, artist) ?: return@withContext null
        val html = fetchText(songUrl) ?: return@withContext null
        val text = parseLyricsHtml(html) ?: return@withContext null
        Lyrics(plainText = text)
    }

    private suspend fun findSongUrl(title: String, artist: String): String? {
        val queries = buildList {
            add(listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "))
            // File tags often carry features or noisy suffixes; a title-only pass recovers those songs.
            if (artist.isNotBlank()) add(title)
        }.distinct()

        for (query in queries) {
            val url = findSongUrlForQuery(query, title, artist)
            if (url != null) return url
        }
        return null
    }

    private suspend fun findSongUrlForQuery(query: String, title: String, artist: String): String? {
        val body = fetchText("$SEARCH_URL?per_page=5&q=" + URLEncoder.encode(query, "UTF-8")) ?: return null
        val sections = runCatching {
            JSONObject(body).getJSONObject("response").optJSONArray("sections")
        }.getOrNull() ?: return null

        var bestUrl: String? = null
        var bestScore = Int.MIN_VALUE
        val wantedTitle = normalize(title)

        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            val hits = section.optJSONArray("hits") ?: continue
            for (j in 0 until hits.length()) {
                val result = hits.optJSONObject(j)?.optJSONObject("result") ?: continue
                // Articles, videos and album pages also show up in search; only song pages end in "-lyrics".
                val url = result.optString("url").takeIf {
                    it.startsWith("https://genius.com/") && it.substringBefore('?').endsWith("-lyrics")
                } ?: continue
                val resultTitle = normalize(result.optString("title"))
                val resultArtist = normalize(result.optJSONObject("primary_artist")?.optString("name").orEmpty())
                var score = when {
                    resultTitle == wantedTitle -> 3
                    resultTitle.contains(wantedTitle) || wantedTitle.contains(resultTitle) -> 2
                    else -> 0
                }
                if (artist.isNotBlank() && resultArtist == normalize(artist)) score += 2
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = url
                }
            }
        }
        return bestUrl?.takeIf { bestScore > 0 }
    }

    private suspend fun fetchText(url: String): String? {
        val first = fetchOnce(url)
        if (first.body != null) return first.body
        // Genius occasionally answers 422/429 while throttling; one short retry recovers those.
        if (first.code != 422 && first.code !in 429..599) return null
        delay(400)
        return fetchOnce(url).body
    }

    private data class FetchResult(val code: Int, val body: String?)

    private fun fetchOnce(url: String): FetchResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.8")
                setRequestProperty("Referer", "https://genius.com/")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                FetchResult(code, null)
            } else {
                FetchResult(code, connection.inputStream.bufferedReader().use(BufferedReader::readText))
            }
        } catch (_: Exception) {
            FetchResult(-1, null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun sanitizeTitle(title: String): String = title
        .replace(Regex("""^\d+[\s.\-_]+"""), "")
        .replace(Regex("""\.(mp3|m4a|flac|wav|ogg|aac)$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[\(\[](feat|ft)\.?\s+[^\)\]]+[\)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(
            Regex(
                """\s*[\(\[](official\s+(audio|video|music\s+video|lyric\s+video)|remix|remaster(ed)?|prod\.?\s+by\s+[^\)\]]+)[\)\]]""",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .trim()

    private fun sanitizeArtist(artist: String): String {
        if (artist.isBlank() || artist.equals("<unknown>", true) || artist.equals("unknown", true) ||
            artist.equals("unknown artist", true)
        ) return ""
        return artist.split(Regex("""[,;&]|\s+(feat|ft)\.?\s+""", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim().orEmpty()
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
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
