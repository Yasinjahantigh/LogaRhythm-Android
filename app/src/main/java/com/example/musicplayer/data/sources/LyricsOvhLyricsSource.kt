package com.example.musicplayer.data.sources

import com.example.musicplayer.domain.lyrics.LrcParser
import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.source.LyricsSource
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
 * Online lyrics provider querying Lyrics.ovh API (https://lyrics.ovh).
 * Provides a vast database of international plain/unsynced lyrics.
 */
@Singleton
class LyricsOvhLyricsSource @Inject constructor() : LyricsSource {

    override val name: String = "lyrics_ovh"

    companion object {
        private const val BASE_URL = "https://api.lyrics.ovh/v1"
        private const val USER_AGENT = "MusicPlayer/1.0 (Android; Offline-First Music Player)"
        private const val TIMEOUT_MS = 6000
    }

    override suspend fun getLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        if (track.title.isBlank() || track.artist.isBlank() || track.artist == "Unknown Artist") {
            return@withContext null
        }

        var connection: HttpURLConnection? = null
        try {
            val encodedArtist = URLEncoder.encode(track.artist.trim(), "UTF-8").replace("+", "%20")
            val encodedTitle = URLEncoder.encode(track.title.trim(), "UTF-8").replace("+", "%20")
            val urlString = "$BASE_URL/$encodedArtist/$encodedTitle"

            val url = URL(urlString)
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
                val lyricsText = json.optString("lyrics", "").trim()
                if (lyricsText.isNotBlank()) {
                    val parsed = LrcParser.parse(lyricsText)
                    if (!parsed.isEmpty) {
                        return@withContext parsed
                    }
                }
            }
        } catch (_: Exception) {
            // Not found or network error
        } finally {
            connection?.disconnect()
        }

        null
    }
}
