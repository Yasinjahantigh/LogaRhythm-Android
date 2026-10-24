package com.yasinjahantigh.logarhythm.data.sources

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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Public LRCLIB API (https://lrclib.net), returning synced lyrics when the community has them. */
@Singleton
class LrclibLyricsSource @Inject constructor(
    private val http: LyricsHttpClient
) : OnlineLyricsSource {

    override val id: String = "lrclib"
    override val displayName: String = "LRCLIB"

    override suspend fun search(query: LyricsQuery): LyricsSearchResult = withContext(Dispatchers.IO) {
        val title = LyricsTextNormalizer.queryTitle(query.title)
        if (title.isBlank()) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        val artist = LyricsTextNormalizer.queryArtist(query.artist)
        val searchTerm = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val response = http.get(
            "https://lrclib.net/api/search?q=${encode(searchTerm)}",
            mapOf("Accept" to "application/json")
        )
        when {
            response.blocked -> return@withContext LyricsSearchResult(
                status = LyricsSourceStatus.BLOCKED,
                detail = response.error
            )
            response.timedOut -> return@withContext LyricsSearchResult(
                status = LyricsSourceStatus.TIMEOUT,
                detail = response.error
            )
            !response.ok -> return@withContext LyricsSearchResult(
                status = if (response.code == 404) LyricsSourceStatus.EMPTY else LyricsSourceStatus.ERROR,
                detail = response.error
            )
        }

        try {
            val array = JSONArray(response.body)
            val candidates = buildList {
                for (i in 0 until array.length().coerceAtMost(10)) {
                    val item = array.optJSONObject(i) ?: continue
                    val lyrics = parseLyricsFromJson(item) ?: continue
                    val id = LyricsResponseFields.text(item, "id").ifBlank { "$i" }
                    val trackName = LyricsResponseFields.text(item, "trackName").ifBlank { title }
                    val artistName = LyricsResponseFields.text(item, "artistName").ifBlank { artist }
                    val albumName = LyricsResponseFields.text(item, "albumName")
                    val durationMs = (item.optDouble("duration", 0.0) * 1000).toLong()
                    add(
                        LyricsCandidate(
                            id = "lrclib_$id",
                            trackName = trackName,
                            artistName = artistName,
                            albumName = albumName,
                            durationMs = durationMs,
                            isSynced = lyrics.isSynced,
                            sourceName = displayName,
                            providerId = this@LrclibLyricsSource.id,
                            lyrics = lyrics
                        )
                    )
                }
            }
            LyricsSearchResult(
                candidates = candidates,
                status = if (candidates.isEmpty()) LyricsSourceStatus.EMPTY else LyricsSourceStatus.OK
            )
        } catch (error: Exception) {
            LyricsSearchResult(status = LyricsSourceStatus.ERROR, detail = error.message)
        }
    }

    private fun parseLyricsFromJson(json: JSONObject): Lyrics? {
        val synced = LyricsResponseFields.text(json, "syncedLyrics")
        val plain = LyricsResponseFields.text(json, "plainLyrics")
        val textToParse = when {
            synced.isNotBlank() -> synced
            plain.isNotBlank() -> plain
            else -> null
        } ?: return null
        val parsed = LrcParser.parse(textToParse)
        return parsed.takeIf { !it.isEmpty }
    }
}
