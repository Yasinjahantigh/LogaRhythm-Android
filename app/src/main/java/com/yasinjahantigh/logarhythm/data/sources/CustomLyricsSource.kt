package com.yasinjahantigh.logarhythm.data.sources

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsHttpClient
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsSearchResult
import com.yasinjahantigh.logarhythm.data.lyrics.OnlineLyricsSource
import com.yasinjahantigh.logarhythm.data.lyrics.encode
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceStatus
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsTextNormalizer
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional user-owned HTTPS endpoint. It lets a user point at their own mirror/proxy without the
 * app shipping proxy code. Supported placeholders: {title}, {artist}, {album}, {duration}.
 */
@Singleton
class CustomLyricsSource @Inject constructor(
    private val http: LyricsHttpClient,
    private val preferencesManager: PreferencesManager
) : OnlineLyricsSource {

    override val id: String = "custom"
    override val displayName: String = "Custom endpoint"

    override suspend fun search(query: LyricsQuery): LyricsSearchResult = withContext(Dispatchers.IO) {
        val template = preferencesManager.lyricsSourcePreferences.first().customUrl?.trim().orEmpty()
        if (!template.startsWith("https://")) {
            return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        }
        val title = LyricsTextNormalizer.queryTitle(query.title)
        val artist = LyricsTextNormalizer.queryArtist(query.artist)
        if (title.isBlank()) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)

        val url = template
            .replace("{title}", encode(title))
            .replace("{artist}", encode(artist))
            .replace("{album}", encode(query.album))
            .replace("{duration}", (query.durationMs / 1000L).toString())

        val response = http.get(url, mapOf("Accept" to "application/json, text/plain, */*"))
        when {
            response.blocked -> return@withContext LyricsSearchResult(status = LyricsSourceStatus.BLOCKED, detail = response.error)
            response.timedOut -> return@withContext LyricsSearchResult(status = LyricsSourceStatus.TIMEOUT, detail = response.error)
            !response.ok -> return@withContext LyricsSearchResult(
                status = if (response.code == 404) LyricsSourceStatus.EMPTY else LyricsSourceStatus.ERROR,
                detail = response.error
            )
        }

        val body = response.body.orEmpty()
        val parsed = parseResponse(body)
        if (parsed == null || parsed.isEmpty) {
            return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        }
        LyricsSearchResult(
            candidates = listOf(
                LyricsCandidate(
                    id = "custom_${title.hashCode()}",
                    trackName = title,
                    artistName = artist,
                    albumName = query.album,
                    durationMs = query.durationMs,
                    isSynced = parsed.isSynced,
                    sourceName = displayName,
                    providerId = id,
                    lyrics = parsed
                )
            )
        )
    }

    private fun parseResponse(body: String): Lyrics? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val json = runCatching { JSONObject(trimmed) }.getOrNull()
            if (json != null) {
                val text = LyricsResponseFields.text(json, "syncedLyrics").ifBlank {
                    LyricsResponseFields.text(json, "plainLyrics").ifBlank {
                        LyricsResponseFields.text(json, "lyrics").ifBlank {
                            LyricsResponseFields.text(json, "lrc")
                        }
                    }
                }
                if (text.isNotBlank()) return LrcParser.parse(text)
            }
        }
        if (trimmed.startsWith("<")) return null
        return LrcParser.parse(trimmed)
            .takeIf { !it.isEmpty }
            ?: Lyrics(plainText = trimmed).takeIf { trimmed.length > 20 }
    }
}
