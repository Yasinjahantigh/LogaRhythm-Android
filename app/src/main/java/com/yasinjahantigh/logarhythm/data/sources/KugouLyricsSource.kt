package com.yasinjahantigh.logarhythm.data.sources

import android.util.Base64
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
 * Online lyrics provider querying Kugou Music API.
 * Provides a massive global catalog of synced .lrc lyrics.
 */
@Singleton
class KugouLyricsSource @Inject constructor() : LyricsSource {

    override val name: String = "kugou"

    companion object {
        private const val SEARCH_URL = "https://krcs.kugou.com/search"
        private const val DOWNLOAD_URL = "https://krcs.kugou.com/download"
        private const val USER_AGENT = "LogaRhythm (Android)"
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
            val (candidateId, accessKey) = searchCandidate(query, track.durationMs) ?: return@withContext null
            val rawLrc = downloadLrc(candidateId, accessKey) ?: return@withContext null
            val parsed = LrcParser.parse(rawLrc)
            if (!parsed.isEmpty) {
                return@withContext parsed
            }
        } catch (_: Exception) {
        }

        null
    }

    private fun searchCandidate(keyword: String, durationMs: Long): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val durationParam = if (durationMs > 0) durationMs.toString() else ""
            val url = URL("$SEARCH_URL?ver=1&man=yes&client=mobi&keyword=$encodedKeyword&duration=$durationParam")

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates") ?: return null
                if (candidates.length() > 0) {
                    val first = candidates.getJSONObject(0)
                    val id = LyricsResponseFields.text(first, "id")
                    val accessKey = LyricsResponseFields.text(first, "accesskey")
                    if (id.isNotBlank() && accessKey.isNotBlank()) {
                        return Pair(id, accessKey)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun downloadLrc(id: String, accessKey: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$DOWNLOAD_URL?ver=1&client=mobi&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val base64Content = LyricsResponseFields.text(json, "content")
                if (base64Content.isNotBlank()) {
                    val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                    return String(decodedBytes, Charsets.UTF_8)
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }
}
