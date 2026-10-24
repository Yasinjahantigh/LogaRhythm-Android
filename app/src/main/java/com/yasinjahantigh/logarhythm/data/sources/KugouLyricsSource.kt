package com.yasinjahantigh.logarhythm.data.sources

import android.util.Base64
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsHttpClient
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsSearchResult
import com.yasinjahantigh.logarhythm.data.lyrics.OnlineLyricsSource
import com.yasinjahantigh.logarhythm.data.lyrics.encode
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceStatus
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsTextNormalizer
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Kugou's public lyric search; useful in networks where it is reachable, opt-in elsewhere. */
@Singleton
class KugouLyricsSource @Inject constructor(
    private val http: LyricsHttpClient
) : OnlineLyricsSource {

    override val id: String = "kugou"
    override val displayName: String = "Kugou"

    override suspend fun search(query: LyricsQuery): LyricsSearchResult = withContext(Dispatchers.IO) {
        val title = LyricsTextNormalizer.queryTitle(query.title)
        if (title.isBlank()) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        val artist = LyricsTextNormalizer.queryArtist(query.artist)
        val keyword = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")

        val search = http.get(
            "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${encode(keyword)}" +
                if (query.durationMs > 0) "&duration=${query.durationMs}" else "",
            mapOf("Accept" to "application/json")
        )
        when {
            search.blocked -> return@withContext LyricsSearchResult(status = LyricsSourceStatus.BLOCKED, detail = search.error)
            search.timedOut -> return@withContext LyricsSearchResult(status = LyricsSourceStatus.TIMEOUT, detail = search.error)
            !search.ok -> return@withContext LyricsSearchResult(
                status = if (search.code == 404) LyricsSourceStatus.EMPTY else LyricsSourceStatus.ERROR,
                detail = search.error
            )
        }

        try {
            val candidatesJson = JSONObject(search.body).optJSONArray("candidates")
                ?: return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
            val candidates = buildList {
                for (i in 0 until candidatesJson.length().coerceAtMost(3)) {
                    val item = candidatesJson.optJSONObject(i) ?: continue
                    val songId = LyricsResponseFields.text(item, "id")
                    val accessKey = LyricsResponseFields.text(item, "accesskey")
                    if (songId.isBlank() || accessKey.isBlank()) continue
                    val rawLrc = downloadLrc(songId, accessKey) ?: continue
                    val parsed = LrcParser.parse(rawLrc)
                    if (parsed.isEmpty) continue
                    add(
                        LyricsCandidate(
                            id = "kugou_$songId",
                            trackName = LyricsResponseFields.text(item, "songname").ifBlank { title },
                            artistName = LyricsResponseFields.text(item, "singername").ifBlank { artist },
                            albumName = LyricsResponseFields.text(item, "albumname"),
                            durationMs = LyricsResponseFields.text(item, "duration").toLongOrNull() ?: 0L,
                            isSynced = parsed.isSynced,
                            sourceName = displayName,
                            providerId = id,
                            lyrics = parsed
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

    private suspend fun downloadLrc(id: String, accessKey: String): String? {
        val response = http.get(
            "https://krcs.kugou.com/download?ver=1&client=mobi&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8",
            mapOf("Accept" to "application/json")
        )
        if (!response.ok) return null
        val content = runCatching {
            LyricsResponseFields.text(JSONObject(response.body), "content")
        }.getOrNull()
        if (content.isNullOrBlank()) return null
        return runCatching { String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
    }
}
