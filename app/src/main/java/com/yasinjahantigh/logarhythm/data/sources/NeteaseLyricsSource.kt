package com.yasinjahantigh.logarhythm.data.sources

import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.source.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online lyrics provider querying Netease Cloud Music (163 Cloud Music) API.
 * Netease is one of the world's largest lyrics databases, featuring synced (.lrc)
 * and translated lyrics for global, western, and international songs.
 */
@Singleton
class NeteaseLyricsSource @Inject constructor() : LyricsSource {

    override val name: String = "netease"

    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/search/get/web"
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        private const val TIMEOUT_MS = 6000
    }

    override suspend fun getLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = track.title
            .replace(Regex("""^\d+[\s.\-_]+"""), "")
            .replace(Regex("""\.(mp3|m4a|flac|wav|ogg|aac)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\(\[](feat|ft)\.?\s+[^\)\]]+[\)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanTitle.isBlank()) return@withContext null

        val cleanArtist = track.artist
            .split(Regex("[,;&]|\\s+(feat|ft)\\.?\\s+", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            ?.takeIf { !it.equals("Unknown Artist", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true) }
            .orEmpty()

        try {
            val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle

            val songId = searchSongId(query) ?: return@withContext null
            val lyricsLrc = fetchLyricById(songId) ?: return@withContext null
            val parsed = LrcParser.parse(lyricsLrc)
            if (!parsed.isEmpty) {
                return@withContext parsed
            }
        } catch (_: Exception) {
        }

        null
    }

    private fun searchSongId(query: String): Long? {
        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$SEARCH_URL?csrf_token=&hlpretag=&hlposttag=&s=$encodedQuery&type=1&offset=0&total=true&limit=5")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val result = json.optJSONObject("result") ?: return null
                val songs = result.optJSONArray("songs") ?: return null
                if (songs.length() > 0) {
                    val firstSong = songs.getJSONObject(0)
                    return firstSong.optLong("id")
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun fetchLyricById(songId: Long): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$LYRIC_URL?os=pc&id=$songId&lv=-1&kv=-1&tv=-1")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val lrcObj = json.optJSONObject("lrc")
                val lyricText = lrcObj?.let { LyricsResponseFields.text(it, "lyric") }
                if (!lyricText.isNullOrEmpty() && lyricText != "[00:00.000] 纯音乐，请欣赏") {
                    return lyricText
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }
}
