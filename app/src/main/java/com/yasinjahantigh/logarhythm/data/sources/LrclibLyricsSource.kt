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

        val candidates = mutableListOf<LyricsCandidate>()

        // 1. Try exact match with /api/get if artist is known
        if (artist.isNotBlank()) {
            val getUrl = buildString {
                append("https://lrclib.net/api/get?track_name=").append(encode(title))
                append("&artist_name=").append(encode(artist))
                if (query.album.isNotBlank()) append("&album_name=").append(encode(query.album))
                if (query.durationMs > 0L) append("&duration=").append(query.durationMs / 1000)
            }
            val getResp = http.get(getUrl, mapOf("Accept" to "application/json"))
            if (getResp.ok && !getResp.body.isNullOrBlank()) {
                val item = runCatching { JSONObject(getResp.body) }.getOrNull()
                if (item != null) {
                    val lyrics = parseLyricsFromJson(item)
                    if (lyrics != null && !lyrics.isEmpty) {
                        val id = LyricsResponseFields.text(item, "id").ifBlank { "0" }
                        val trackName = LyricsResponseFields.text(item, "trackName").ifBlank { title }
                        val artistName = LyricsResponseFields.text(item, "artistName").ifBlank { artist }
                        val albumName = LyricsResponseFields.text(item, "albumName")
                        val durationMs = (item.optDouble("duration", 0.0) * 1000).toLong()
                        candidates.add(
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
                        // If exact match gave synced lyrics, return it directly!
                        if (lyrics.isSynced) {
                            return@withContext LyricsSearchResult(candidates = candidates, status = LyricsSourceStatus.OK)
                        }
                    }
                }
            }
        }

        // 2. Search by track_name & artist_name or general query
        val searchUrl = if (artist.isNotBlank()) {
            "https://lrclib.net/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        } else {
            "https://lrclib.net/api/search?q=${encode(title)}"
        }
        var response = http.get(searchUrl, mapOf("Accept" to "application/json"))
        if (!response.ok && artist.isNotBlank()) {
            val searchTerm = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            response = http.get("https://lrclib.net/api/search?q=${encode(searchTerm)}", mapOf("Accept" to "application/json"))
        }

        when {
            response.blocked && candidates.isEmpty() -> return@withContext LyricsSearchResult(
                status = LyricsSourceStatus.BLOCKED,
                detail = response.error
            )
            response.timedOut && candidates.isEmpty() -> return@withContext LyricsSearchResult(
                status = LyricsSourceStatus.TIMEOUT,
                detail = response.error
            )
            !response.ok && candidates.isEmpty() -> return@withContext LyricsSearchResult(
                status = if (response.code == 404) LyricsSourceStatus.EMPTY else LyricsSourceStatus.ERROR,
                detail = response.error
            )
        }

        if (response.ok && !response.body.isNullOrBlank()) {
            try {
                val array = JSONArray(response.body)
                for (i in 0 until array.length().coerceAtMost(10)) {
                    val item = array.optJSONObject(i) ?: continue
                    val lyrics = parseLyricsFromJson(item) ?: continue
                    val id = LyricsResponseFields.text(item, "id").ifBlank { "$i" }
                    val trackName = LyricsResponseFields.text(item, "trackName").ifBlank { title }
                    val artistName = LyricsResponseFields.text(item, "artistName").ifBlank { artist }
                    val albumName = LyricsResponseFields.text(item, "albumName")
                    val durationMs = (item.optDouble("duration", 0.0) * 1000).toLong()
                    candidates.add(
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
            } catch (_: Exception) {
            }
        }

        // Sort so that synced candidates always appear first
        val sortedCandidates = candidates.sortedByDescending { if (it.isSynced) 1 else 0 }
        LyricsSearchResult(
            candidates = sortedCandidates,
            status = if (sortedCandidates.isEmpty()) LyricsSourceStatus.EMPTY else LyricsSourceStatus.OK
        )
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
