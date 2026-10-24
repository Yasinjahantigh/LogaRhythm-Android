package com.yasinjahantigh.logarhythm.data.sources

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

/** NetEase Cloud Music's public web endpoints; blocked from some networks, hence opt-in. */
@Singleton
class NeteaseLyricsSource @Inject constructor(
    private val http: LyricsHttpClient
) : OnlineLyricsSource {

    override val id: String = "netease"
    override val displayName: String = "NetEase"

    override suspend fun search(query: LyricsQuery): LyricsSearchResult = withContext(Dispatchers.IO) {
        val title = LyricsTextNormalizer.queryTitle(query.title)
        if (title.isBlank()) return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
        val artist = LyricsTextNormalizer.queryArtist(query.artist)
        val keyword = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")

        val search = http.get(
            "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=${encode(keyword)}&type=1&offset=0&total=true&limit=5",
            mapOf("Referer" to "https://music.163.com/", "Accept" to "application/json")
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
            val songs = JSONObject(search.body).optJSONObject("result")?.optJSONArray("songs")
                ?: return@withContext LyricsSearchResult(status = LyricsSourceStatus.EMPTY)
            val candidates = buildList {
                for (i in 0 until songs.length().coerceAtMost(3)) {
                    val song = songs.optJSONObject(i) ?: continue
                    val songId = song.optLong("id", 0L)
                    if (songId <= 0L) continue
                    val lyricText = fetchLyric(songId) ?: continue
                    val parsed = LrcParser.parse(lyricText)
                    if (parsed.isEmpty) continue
                    val artists = song.optJSONArray("artists")
                    val artistName = if (artists != null && artists.length() > 0) {
                        (0 until artists.length()).mapNotNull {
                            artists.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)
                        }.joinToString(", ")
                    } else {
                        artist
                    }
                    add(
                        LyricsCandidate(
                            id = "netease_$songId",
                            trackName = song.optString("name").ifBlank { title },
                            artistName = artistName.ifBlank { artist },
                            albumName = song.optJSONObject("album")?.optString("name").orEmpty(),
                            durationMs = song.optLong("duration", 0L),
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

    private suspend fun fetchLyric(songId: Long): String? {
        val response = http.get(
            "https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1",
            mapOf("Referer" to "https://music.163.com/", "Accept" to "application/json")
        )
        if (!response.ok) return null
        val lyric = runCatching {
            JSONObject(response.body).optJSONObject("lrc")?.let { LyricsResponseFields.text(it, "lyric") }
        }.getOrNull()
        return lyric?.takeIf { it.isNotBlank() && it != "[00:00.000] 纯音乐，请欣赏" }
    }
}
