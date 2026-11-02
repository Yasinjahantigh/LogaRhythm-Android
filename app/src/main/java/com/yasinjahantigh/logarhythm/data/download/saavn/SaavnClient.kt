package com.yasinjahantigh.logarhythm.data.download.saavn

import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.CancellationException
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
 * High-speed direct client for JioSaavn API.
 * Provides direct CDN download links for 320kbps / 160kbps audio files with full metadata and cover art.
 */
@Singleton
class SaavnClient @Inject constructor(
    baseClient: OkHttpClient
) {
    private val client = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 20): List<DownloadSearchResult> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(term, Charsets.UTF_8.name())

        val primaryUrl = "https://saavn.dev/api/search/songs?query=$encoded&limit=${limit.coerceIn(1, 40)}"
        val fallbackUrl = "https://saavn.me/search/songs?query=$encoded&limit=${limit.coerceIn(1, 40)}"

        val json = fetchJson(primaryUrl) ?: fetchJson(fallbackUrl) ?: return@withContext emptyList()
        parseResults(json)
    }

    private fun parseResults(root: JSONObject): List<DownloadSearchResult> {
        val data = root.optJSONObject("data") ?: root
        val results = data.optJSONArray("results") ?: data.optJSONArray("songs") ?: return emptyList()
        val list = mutableListOf<DownloadSearchResult>()

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = item.optString("name").takeIf { it.isNotBlank() }
                ?: item.optString("title").takeIf { it.isNotBlank() }
                ?: continue

            val artist = listOf(
                item.optString("primaryArtists"),
                item.optString("artist"),
                item.optString("singers")
            ).firstOrNull { it.isNotBlank() } ?: ""

            val durationSeconds = item.optDouble("duration", 0.0)
            val durationMs = if (durationSeconds > 0) (durationSeconds * 1000).toLong() else 0L

            // Extract best quality cover image (500x500 if available)
            val imageUrl = extractBestImage(item)

            // Extract direct CDN audio download link (prefer 320kbps)
            val audioUrl = extractBestDownloadUrl(item) ?: continue

            list.add(
                DownloadSearchResult(
                    id = id,
                    title = cleanText(title),
                    artist = cleanText(artist),
                    source = DownloadSource.SAAVN,
                    url = audioUrl,
                    durationMs = durationMs,
                    thumbnailUrl = imageUrl
                )
            )
        }
        return list
    }

    private fun extractBestImage(item: JSONObject): String? {
        val images = item.optJSONArray("image")
        if (images != null && images.length() > 0) {
            for (idx in images.length() - 1 downTo 0) {
                val img = images.optJSONObject(idx) ?: continue
                val url = img.optString("url")
                if (url.startsWith("http")) return url
            }
        }
        val direct = item.optString("image").takeIf { it.startsWith("http") }
        return direct?.replace("150x150", "500x500")
    }

    private fun extractBestDownloadUrl(item: JSONObject): String? {
        val urls = item.optJSONArray("downloadUrl")
        if (urls != null && urls.length() > 0) {
            var bestUrl: String? = null
            var bestQuality = -1
            for (idx in 0 until urls.length()) {
                val entry = urls.optJSONObject(idx) ?: continue
                val url = entry.optString("url").takeIf { it.startsWith("http") } ?: continue
                val qualityStr = entry.optString("quality").filter { it.isDigit() }
                val quality = qualityStr.toIntOrNull() ?: 128
                if (quality >= bestQuality) {
                    bestQuality = quality
                    bestUrl = url
                }
            }
            if (bestUrl != null) return bestUrl
        }
        val mediaUrl = item.optString("media_url").takeIf { it.startsWith("http") }
        return mediaUrl
    }

    private fun cleanText(text: String): String =
        text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .trim()

    private fun fetchJson(urlStr: String): JSONObject? {
        val url = urlStr.toHttpUrlOrNull() ?: return null
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "LogaRhythm/1.7.0")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
            }
        } catch (_: Exception) {
            null
        }
    }
}
