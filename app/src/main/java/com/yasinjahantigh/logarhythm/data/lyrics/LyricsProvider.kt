package com.yasinjahantigh.logarhythm.data.lyrics

import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** One provider fetch. [status] is separate from "zero candidates" so the UI can explain failures. */
data class LyricsSearchResult(
    val candidates: List<com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate> = emptyList(),
    val status: LyricsSourceStatus = LyricsSourceStatus.OK,
    val detail: String? = null
)

/** A provider that can return multiple metadata-rich lyric matches. */
interface OnlineLyricsSource {
    val id: String
    val displayName: String
    suspend fun search(query: LyricsQuery): LyricsSearchResult
}

data class LyricsHttpResponse(
    val code: Int,
    val body: String?,
    val blocked: Boolean = false,
    val timedOut: Boolean = false,
    val error: String? = null
) {
    val ok: Boolean get() = code in 200..299 && !body.isNullOrBlank()
}

/**
 * One OkHttp client for every lyrics provider. The previous implementation opened a raw
 * HttpURLConnection per request, with no shared pool, no gzip and a 6 s wait for each blocked host.
 */
@Singleton
class LyricsHttpClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): LyricsHttpResponse {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "application/json, text/plain;q=0.9, text/html;q=0.8, */*;q=0.5")
                .apply { headers.forEach { (key, value) -> header(key, value) } }
                .build()
        } catch (_: IllegalArgumentException) {
            return LyricsHttpResponse(0, null, error = "Invalid URL")
        }

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isActive) return
                    continuation.resume(
                        if (error is SocketTimeoutException) {
                            LyricsHttpResponse(0, null, timedOut = true, error = "Timeout")
                        } else {
                            LyricsHttpResponse(0, null, error = error.message ?: "Network error")
                        }
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { result ->
                        val body = runCatching {
                            result.body?.string()?.takeIf { it.length <= MAX_BODY_CHARS }
                        }.getOrNull()
                        val blocked = result.code in BLOCKED_CODES ||
                            body?.let(::looksLikeChallenge) == true
                        if (continuation.isActive) {
                            continuation.resume(
                                LyricsHttpResponse(
                                    code = result.code,
                                    body = body,
                                    blocked = blocked,
                                    error = if (result.code !in 200..299) "HTTP ${result.code}" else null
                                )
                            )
                        }
                    }
                }
            })
        }
    }

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 400_000) return false
        val sample = body.take(120_000).lowercase()
        return "just a moment" in sample ||
            "cf-chl" in sample ||
            "challenge-platform" in sample && "enable javascript" in sample ||
            "captcha" in sample && "lyrics" !in sample
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private const val MAX_BODY_CHARS = 2_000_000
        private val BLOCKED_CODES = setOf(401, 403, 429, 451, 503)
    }
}

/** URL-encodes a value for a query string or path segment. */
internal fun encode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
