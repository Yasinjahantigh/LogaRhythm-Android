package com.yasinjahantigh.logarhythm.data.download.radiojavan

import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated client for Radio Javan (رادیو جوان), the primary source for Persian music.
 * Directly searches and fetches 320kbps MP3s and high-res cover art.
 */
@Singleton
class RadioJavanClient @Inject constructor(
    baseClient: OkHttpClient
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val apiHosts = listOf(
        "https://api.radiojavan.com",
        "https://api-2.radiojavan.com",
        "https://rj.app"
    )

    private val proxyBase = "https://abraava.ir/?url="

    suspend fun search(query: String, limit: Int = 25): List<DownloadSearchResult> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(term, Charsets.UTF_8.name())

        for (host in apiHosts) {
            val url = "$host/api2/search?query=$encoded"
            val json = fetchJson(url)
            if (json != null) {
                val results = parseSearchResults(json, limit)
                if (results.isNotEmpty()) return@withContext results
            }
        }
        emptyList()
    }

    suspend fun resolveMp3Url(songId: String, webpageLink: String? = null): String? = withContext(Dispatchers.IO) {
        val cleanId = songId.trim()
        if (cleanId.isNotBlank()) {
            for (host in apiHosts) {
                val url = "$host/api2/mp3?id=$cleanId"
                val json = fetchJson(url)
                if (json != null) {
                    val link = json.optString("link").takeIf { it.startsWith("http") }
                        ?: json.optString("link_hq").takeIf { it.startsWith("http") }
                    if (link != null) return@withContext link
                }
            }
        }

        // If direct API endpoint did not give the link, construct from webpage link or artist/song slug
        if (webpageLink != null && webpageLink.contains("/mp3/")) {
            val slug = webpageLink.substringAfterLast("/mp3/").substringBefore('?').substringBefore('#')
            if (slug.isNotBlank()) {
                return@withContext "https://host2.rjmusicmedia.com/media/mp3/mp3-320/$slug.mp3"
            }
        }
        null
    }

    private fun parseSearchResults(root: JSONObject, limit: Int): List<DownloadSearchResult> {
        val mp3s = root.optJSONArray("mp3s") ?: return emptyList()
        val results = mutableListOf<DownloadSearchResult>()

        for (i in 0 until mp3s.length().coerceAtMost(limit)) {
            val item = mp3s.optJSONObject(i) ?: continue
            val id = item.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: continue
            val song = item.optString("song").trim().takeIf { it.isNotBlank() }
                ?: item.optString("title").trim().takeIf { it.isNotBlank() }
                ?: continue
            val artist = item.optString("artist").trim()
            val photo = item.optString("photo").takeIf { it.startsWith("http") }
                ?: item.optString("photo_player").takeIf { it.startsWith("http") }
            val durationSec = item.optDouble("duration", 0.0)
            val durationMs = if (durationSec > 0) (durationSec * 1000).toLong() else 0L
            val link = item.optString("link").takeIf { it.startsWith("http") }
                ?: "https://www.radiojavan.com/mp3s/mp3/$artist-$song"

            results.add(
                DownloadSearchResult(
                    id = id,
                    title = song,
                    artist = artist,
                    source = DownloadSource.RADIOJAVAN,
                    url = link,
                    durationMs = durationMs,
                    thumbnailUrl = photo
                )
            )
        }
        return results
    }

    private fun fetchJson(urlStr: String): JSONObject? {
        val url = urlStr.toHttpUrlOrNull() ?: return null
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Radio Javan/10.7.6 (Android; Android OS 14; en)")
            .header("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
            }
        } catch (_: Exception) {
            // Try via proxy if direct fails
            try {
                val proxiedUrl = (proxyBase + URLEncoder.encode(urlStr, Charsets.UTF_8.name())).toHttpUrlOrNull() ?: return null
                val proxyReq = Request.Builder().url(proxiedUrl).header("Accept", "application/json").build()
                client.newCall(proxyReq).execute().use { response ->
                    if (!response.isSuccessful) null
                    else response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
