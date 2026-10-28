package com.yasinjahantigh.logarhythm.data.download.musicman

import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Anonymous MusicMan endpoints used by mm.3rah.ir; results mirror the iTunes catalog. */
@Singleton
class MusicManClient @Inject constructor(
    private val client: OkHttpClient
) {

    data class Audio(
        val quality: Int,
        val url: String,
        val filename: String?
    )

    internal var apiBase: String = API_BASE
    internal var proxyBase: String = PROXY_BASE
    internal var pollIntervalMs: Long = POLL_INTERVAL_MS
    internal var crawlTimeoutMs: Long = CRAWL_TIMEOUT_MS

    suspend fun search(term: String, limit: Int = SEARCH_LIMIT): List<DownloadSearchResult> =
        withContext(Dispatchers.IO) {
            val query = term.trim()
            if (query.isBlank()) return@withContext emptyList()
            val url = "$apiBase/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("term", query)
                ?.addQueryParameter("limit", limit.coerceIn(1, 100).toString())
                ?.addQueryParameter("entity", "musicArtist,album,song")
                ?.build()
                ?.toString()
                ?: throw DownloadPipelineException(
                    DownloadErrorKind.UNKNOWN,
                    "MusicMan search URL is invalid."
                )
            parseSearch(executeJson(Request.Builder().url(url).get().build()))
        }

    /**
     * Returns an audio file the server already has. Tracks missing from the cache are queued on the
     * server and polled for a bounded time; the crawl can legitimately take longer than one attempt.
     */
    suspend fun resolveAudio(
        trackId: String,
        onStatus: (Int, String) -> Unit = { _, _ -> }
    ): Audio {
        audioFromLookup(trackId)?.let { return it }

        val existing = downloadStatus(trackId)
        existing?.let { download ->
            audioOf(download)?.let { return it }
            terminalFailure(download)?.let { throw it }
        }
        if (existing == null) {
            requestDownload(trackId)
            onStatus(0, "musicman_queued")
        }

        val deadline = System.currentTimeMillis() + crawlTimeoutMs
        var lastPercent = -1
        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            val download = downloadStatus(trackId) ?: continue
            audioOf(download)?.let { return it }
            terminalFailure(download)?.let { throw it }
            val percent = download.optInt("percent", 0).coerceIn(0, 99)
            if (percent != lastPercent) {
                lastPercent = percent
                onStatus(percent, "musicman_preparing")
            }
        }
        throw DownloadPipelineException(
            DownloadErrorKind.CONTENT,
            "MusicMan is still preparing this track. Try again later."
        )
    }

    fun playableUrl(audio: Audio): String {
        val host = audio.url.toHttpUrlOrNull()?.host.orEmpty()
        val needsProxy = host.endsWith("telegram.org") ||
            host.endsWith("t.me") ||
            audio.url.contains("<token>")
        return if (needsProxy && proxyBase.isNotBlank()) proxied(audio.url) else audio.url
    }

    fun proxied(url: String): String =
        proxyBase + URLEncoder.encode(url, Charsets.UTF_8.name())

    internal fun parseSearch(body: JSONObject): List<DownloadSearchResult> {
        val results = body.optJSONArray("results") ?: return emptyList()
        val tracks = LinkedHashMap<String, DownloadSearchResult>()
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (!item.optString("wrapperType").equals("track", ignoreCase = true)) continue
            val trackId = item.trackId() ?: continue
            // The server crawl is currently unreliable, so only offer tracks it can serve now.
            item.bestAudio() ?: continue
            val title = item.trackTitle() ?: continue
            tracks.putIfAbsent(
                trackId,
                DownloadSearchResult(
                    id = trackId,
                    title = title,
                    artist = item.optString("artistName").trim(),
                    source = DownloadSource.MUSICMAN,
                    url = TRACK_URL_PREFIX + trackId,
                    durationMs = item.optLong("trackTimeMillis", 0L).coerceAtLeast(0L),
                    thumbnailUrl = item.bestArtwork()
                )
            )
        }
        return tracks.values.toList()
    }

    internal fun audioOf(download: JSONObject): Audio? = download.bestAudio()

    internal fun terminalFailure(download: JSONObject): DownloadPipelineException? {
        val state = download.optString("download_status").lowercase()
        val message = download.optString("error_message").takeIf { it.isNotBlank() }
        return when (state) {
            "failed", "error", "cancelled", "canceled" -> DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                message ?: "MusicMan could not prepare this track."
            )
            "completed", "complete", "done", "saved", "ready", "finished" -> DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                message ?: "MusicMan finished without an audio file."
            )
            else -> null
        }
    }

    private fun JSONObject.trackId(): String? =
        opt("trackId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.trackTitle(): String? = listOf(
        optString("trackName"),
        optString("trackCensoredName")
    ).map { it.trim() }.firstOrNull { it.isNotBlank() }

    private fun JSONObject.bestAudio(): Audio? {
        val audioUrls = optJSONObject("attachments")?.optJSONArray("audioUrls") ?: return null
        return (0 until audioUrls.length())
            .mapNotNull { audioUrls.optJSONObject(it)?.toAudio() }
            .maxByOrNull { it.quality }
    }

    private fun JSONObject.toAudio(): Audio? {
        val url = optString("url").trim()
        if (!url.startsWith("https://")) return null
        return Audio(
            quality = opt("quality")?.toString()?.toIntOrNull() ?: 0,
            url = url,
            filename = optString("telegramFilename").takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.bestArtwork(): String? {
        val artworkUrls = optJSONObject("attachments")?.optJSONArray("artworkUrls") ?: return null
        val best = (0 until artworkUrls.length())
            .mapNotNull { artworkUrls.optJSONObject(it) }
            .maxByOrNull { artworkScore(it.optString("size")) }
            ?.optString("url")
            ?.takeIf { it.startsWith("http") }
            ?: return null
        return upgradeItunesArtwork(best)
    }

    private fun artworkScore(size: String): Int = when {
        size.equals("original", ignoreCase = true) -> Int.MAX_VALUE
        else -> size.substringBefore('x').toIntOrNull() ?: 0
    }

    private fun upgradeItunesArtwork(url: String): String =
        if (url.contains("mzstatic.com")) {
            url.replace(Regex("/\\d+x\\d+bb\\.(jpg|jpeg|png)$"), "/600x600bb.$1")
        } else {
            url
        }

    private suspend fun audioFromLookup(trackId: String): Audio? {
        val url = requestUrl("$apiBase/lookup") {
            addQueryParameter("id", trackId)
            addQueryParameter("entity", "song")
            addQueryParameter("limit", "1")
        }
        val body = executeJson(Request.Builder().url(url).get().build())
        val results = body.optJSONArray("results") ?: return null
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (item.trackId() != trackId) continue
            return item.bestAudio()
        }
        return null
    }

    private suspend fun downloadStatus(trackId: String): JSONObject? {
        val url = requestUrl("$apiBase/download/status") {
            addQueryParameter("trackId", trackId)
        }
        val body = executeJson(Request.Builder().url(url).get().build())
        if (!body.optBoolean("success", true)) {
            val error = body.optString("error")
            if (error.contains("not found", ignoreCase = true)) return null
            throw DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                error.ifBlank { "MusicMan did not return the download status." }
            )
        }
        return body.optJSONObject("download")
    }

    private suspend fun requestDownload(trackId: String) {
        val payload = JSONObject()
            .put("trackId", trackId)
            .put("quality", "320")
            .put("skipExisting", true)
            .toString()
        val url = "$apiBase/download/add".toHttpUrlOrNull()
            ?: throw DownloadPipelineException(DownloadErrorKind.UNKNOWN, "MusicMan URL is invalid.")
        val request = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeJson(request)
        if (!body.optBoolean("success", false)) {
            val error = body.optString("error")
            throw DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                error.ifBlank { "MusicMan could not queue this track." }
            )
        }
        if (body.optInt("failed_count", 0) > 0) {
            val reason = body.optJSONArray("failed")
                ?.optJSONObject(0)
                ?.optString("error_message")
                ?.takeIf { it.isNotBlank() }
            throw DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                reason ?: "MusicMan could not queue this track."
            )
        }
    }

    private fun requestUrl(
        base: String,
        build: okhttp3.HttpUrl.Builder.() -> Unit
    ): String {
        val builder = base.toHttpUrlOrNull()?.newBuilder()
            ?: throw DownloadPipelineException(DownloadErrorKind.UNKNOWN, "MusicMan URL is invalid.")
        return builder.apply(build).build().toString()
    }

    private fun executeJson(request: Request): JSONObject {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val kind = if (response.code >= 500 || response.code == 429) {
                        DownloadErrorKind.NETWORK
                    } else {
                        DownloadErrorKind.CONTENT
                    }
                    throw DownloadPipelineException(
                        kind,
                        "MusicMan request failed (HTTP ${response.code})."
                    )
                }
                return runCatching { JSONObject(text) }.getOrElse { error ->
                    throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "MusicMan returned an invalid response.",
                        error
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pipeline: DownloadPipelineException) {
            throw pipeline
        } catch (error: IOException) {
            throw DownloadPipelineException(
                DownloadErrorKind.NETWORK,
                error.message ?: "MusicMan is unreachable.",
                error
            )
        }
    }

    private companion object {
        const val API_BASE = "https://3rah.ir/mm/api"
        const val PROXY_BASE = "https://abraava.ir/?url="
        const val TRACK_URL_PREFIX = "musicman://track/"
        const val SEARCH_LIMIT = 50
        const val POLL_INTERVAL_MS = 2_500L
        const val CRAWL_TIMEOUT_MS = 60_000L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
