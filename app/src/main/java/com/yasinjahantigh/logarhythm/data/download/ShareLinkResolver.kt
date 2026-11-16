package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ResolvedShareAction {
    data class EnqueueDirect(val result: DownloadSearchResult, val queryFallback: String) : ResolvedShareAction()
    data class SearchQuery(val query: String, val sourceFilter: DownloadSource? = null) : ResolvedShareAction()
}

data class ParsedShareLink(
    val url: String,
    val host: String,
    val kind: Kind,
    val slugOrId: String
) {
    enum class Kind { SPOTIFY, YOUTUBE, RADIOJAVAN, MUSICMAN, GENERIC_URL }
}

/**
 * Receives shared text/URLs from external apps (ACTION_SEND / ACTION_VIEW) and
 * resolves them into direct downloads or clean search queries.
 */
@Singleton
class ShareLinkResolver @Inject constructor(
    baseClient: OkHttpClient
) {

    private val client = baseClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _pendingShareText = MutableStateFlow<String?>(null)
    val pendingShareText: StateFlow<String?> = _pendingShareText.asStateFlow()

    fun submitIncomingText(text: String?) {
        val clean = text?.trim()?.takeIf { it.isNotBlank() } ?: return
        _pendingShareText.value = clean
    }

    fun consumePending(): String? {
        val value = _pendingShareText.value
        _pendingShareText.value = null
        return value
    }

    suspend fun resolve(sharedText: String): ResolvedShareAction? = withContext(Dispatchers.IO) {
        val parsed = parseShareText(sharedText)
        if (parsed == null) {
            val plain = sharedText.trim()
            return@withContext if (plain.isNotBlank()) ResolvedShareAction.SearchQuery(plain) else null
        }

        when (parsed.kind) {
            ParsedShareLink.Kind.SPOTIFY -> {
                val query = fetchSpotifyOEmbedQuery(parsed.url)
                    ?: parsed.slugOrId.ifBlank { parsed.url }
                ResolvedShareAction.SearchQuery(query = query, sourceFilter = null)
            }
            ParsedShareLink.Kind.YOUTUBE -> {
                val videoId = parsed.slugOrId.ifBlank { parsed.url }
                val direct = DownloadSearchResult(
                    id = videoId,
                    title = videoId,
                    artist = "",
                    source = DownloadSource.YOUTUBE,
                    url = parsed.url,
                    durationMs = 0L,
                    thumbnailUrl = null,
                    kind = DownloadResultKind.TRACK
                )
                ResolvedShareAction.EnqueueDirect(direct, parsed.url)
            }
            ParsedShareLink.Kind.RADIOJAVAN -> {
                val q = parsed.slugOrId.ifBlank { parsed.url }
                ResolvedShareAction.SearchQuery(query = q, sourceFilter = DownloadSource.RADIOJAVAN)
            }
            ParsedShareLink.Kind.MUSICMAN -> {
                val q = parsed.slugOrId.ifBlank { parsed.url }
                ResolvedShareAction.SearchQuery(query = q, sourceFilter = DownloadSource.MUSICMAN)
            }
            ParsedShareLink.Kind.GENERIC_URL -> {
                ResolvedShareAction.SearchQuery(query = parsed.slugOrId.ifBlank { parsed.url })
            }
        }
    }

    private fun fetchSpotifyOEmbedQuery(spotifyUrl: String): String? {
        val encoded = URLEncoder.encode(spotifyUrl, "UTF-8")
        val req = Request.Builder()
            .url("https://open.spotify.com/oembed?url=$encoded")
            .header("Accept", "application/json")
            .get()
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                val title = json.optString("title").trim()
                val author = json.optString("author_name").trim()
                listOf(title, author).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

        fun parseShareText(text: String): ParsedShareLink? {
            val url = URL_REGEX.find(text)?.value
                ?.trimEnd('.', ',', ')', ']', ';')
                ?: return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()
            val segments = path.split('/').filter { it.isNotBlank() }

            return when {
                host.contains("spotify.com") || host == "spotify.link" -> {
                    val last = segments.lastOrNull().orEmpty().substringBefore('?')
                    ParsedShareLink(url, host, ParsedShareLink.Kind.SPOTIFY, last)
                }
                host.contains("youtu.be") -> {
                    val id = segments.firstOrNull().orEmpty()
                    ParsedShareLink(url, host, ParsedShareLink.Kind.YOUTUBE, id)
                }
                host.contains("youtube.com") -> {
                    val v = extractQueryParam(uri.rawQuery, "v")
                        ?: segments.lastOrNull().orEmpty()
                    ParsedShareLink(url, host, ParsedShareLink.Kind.YOUTUBE, v)
                }
                host.contains("radiojavan.com") -> {
                    val slug = segments.lastOrNull().orEmpty().replace('-', ' ').trim()
                    ParsedShareLink(url, host, ParsedShareLink.Kind.RADIOJAVAN, slug)
                }
                host.contains("mm.3rah.ir") -> {
                    val slug = segments.lastOrNull().orEmpty()
                    ParsedShareLink(url, host, ParsedShareLink.Kind.MUSICMAN, slug)
                }
                else -> ParsedShareLink(url, host, ParsedShareLink.Kind.GENERIC_URL, "")
            }
        }

        private fun extractQueryParam(rawQuery: String?, key: String): String? {
            if (rawQuery.isNullOrBlank()) return null
            return rawQuery.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.firstOrNull() == key }
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
        }
    }
}
