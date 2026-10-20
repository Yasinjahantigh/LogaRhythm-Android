package com.yasinjahantigh.logarhythm.data.sources

import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.source.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online lyrics provider querying the public LRCLIB API (https://lrclib.net).
 *
 * Implements a dual-phase query:
 * 1. Fast exact matching via /api/get with sanitized title and artist (relaxed duration).
 * 2. Smart fallback via /api/search?q=... to find closest match with synced or plain lyrics.
 */
@Singleton
class LrclibLyricsSource @Inject constructor() : LyricsSource {

    override val name: String = "lrclib"

    companion object {
        private const val BASE_GET_URL = "https://lrclib.net/api/get"
        private const val BASE_SEARCH_URL = "https://lrclib.net/api/search"
        private const val USER_AGENT = "LogaRhythm/1.0 (Android)"
        private const val TIMEOUT_MS = 6000
    }

    override suspend fun getLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = sanitizeTitle(track.title)
        if (cleanTitle.isBlank()) return@withContext null
        val cleanArtist = sanitizeArtist(track.artist)

        // Phase 1: Try /api/get with clean metadata
        val getLyrics = fetchFromGetEndpoint(cleanTitle, cleanArtist)
        if (getLyrics != null && !getLyrics.isEmpty) {
            return@withContext getLyrics
        }

        // Phase 2: Try /api/search with fuzzy query
        val searchLyrics = fetchFromSearchEndpoint(cleanTitle, cleanArtist)
        if (searchLyrics != null && !searchLyrics.isEmpty) {
            return@withContext searchLyrics
        }

        // Phase 3: Fallback query with title only if artist was present but yielded no results
        if (cleanArtist.isNotBlank()) {
            val titleOnlyLyrics = fetchFromSearchEndpoint(cleanTitle, "")
            if (titleOnlyLyrics != null && !titleOnlyLyrics.isEmpty) {
                return@withContext titleOnlyLyrics
            }
        }

        null
    }

    suspend fun searchCandidates(title: String, artist: String): List<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = sanitizeTitle(title)
        val cleanArtist = sanitizeArtist(artist)
        if (cleanTitle.isBlank()) return@withContext emptyList()

        val results = mutableListOf<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate>()
        var connection: HttpURLConnection? = null
        try {
            val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
            val url = URL("$BASE_SEARCH_URL?q=" + URLEncoder.encode(query, "UTF-8"))
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = JSONArray(responseText)
                for (i in 0 until array.length().coerceAtMost(6)) {
                    val item = array.getJSONObject(i)
                    val lyrics = parseLyricsFromJson(item)
                    if (lyrics != null && !lyrics.isEmpty) {
                        // JSONObject.optString can stringify JSON null as the literal "null".
                        // Read optional fields defensively before they reach the picker UI.
                        val id = LyricsResponseFields.text(item, "id").ifBlank { "${results.size}" }
                        val trackName = LyricsResponseFields.text(item, "trackName").ifBlank { cleanTitle }
                        val artistName = LyricsResponseFields.text(item, "artistName").ifBlank { cleanArtist }
                        val albumName = LyricsResponseFields.text(item, "albumName")
                        if (trackName.isBlank() || artistName.equals("null", ignoreCase = true)) continue
                        val duration = (item.optDouble("duration", 0.0) * 1000).toLong()
                        results.add(
                            com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate(
                                id = "lrclib_$id",
                                trackName = trackName,
                                artistName = artistName,
                                albumName = albumName,
                                durationMs = duration,
                                isSynced = lyrics.isSynced,
                                sourceName = "LRCLIB",
                                lyrics = lyrics
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        results
    }

    private fun fetchFromGetEndpoint(cleanTitle: String, cleanArtist: String): Lyrics? {
        var connection: HttpURLConnection? = null
        try {
            val queryParams = mutableListOf<String>()
            queryParams.add("track_name=" + URLEncoder.encode(cleanTitle, "UTF-8"))
            if (cleanArtist.isNotBlank()) {
                queryParams.add("artist_name=" + URLEncoder.encode(cleanArtist, "UTF-8"))
            }

            val url = URL("$BASE_GET_URL?${queryParams.joinToString("&")}")
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
                return parseLyricsFromJson(json)
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun fetchFromSearchEndpoint(cleanTitle: String, cleanArtist: String): Lyrics? {
        var connection: HttpURLConnection? = null
        try {
            val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
            val url = URL("$BASE_SEARCH_URL?q=" + URLEncoder.encode(query, "UTF-8"))
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val array = JSONArray(responseText)
                var fallbackPlain: Lyrics? = null

                for (i in 0 until array.length().coerceAtMost(6)) {
                    val item = array.getJSONObject(i)
                    val lyrics = parseLyricsFromJson(item)
                    if (lyrics != null && !lyrics.isEmpty) {
                        if (lyrics.isSynced) {
                            return lyrics
                        } else if (fallbackPlain == null) {
                            fallbackPlain = lyrics
                        }
                    }
                }
                return fallbackPlain
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
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
        return if (!parsed.isEmpty) parsed else null
    }

    private fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("""^\d+[\s.\-_]+"""), "") // Remove leading track numbers e.g. "01 - " or "1. "
            .replace(Regex("""\.(mp3|m4a|flac|wav|ogg|aac)$""", RegexOption.IGNORE_CASE), "") // Remove file extension
            .replace(Regex("""\s*[\(\[](feat|ft)\.?\s+[^\)\]]+[\)\]]""", RegexOption.IGNORE_CASE), "") // Remove (feat. ...)
            .replace(Regex("""\s*[\(\[](official\s+(audio|video|music\s+video|lyric\s+video)|remix|remaster(ed)?|prod\.?\s+by\s+[^\)\]]+)[\)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun sanitizeArtist(artist: String): String {
        if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true) || artist.equals("unknown", ignoreCase = true) || artist.equals("unknown artist", ignoreCase = true)) {
            return ""
        }
        return artist
            .split(Regex("[,;&]|\\s+(feat|ft)\\.?\\s+", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }
}
