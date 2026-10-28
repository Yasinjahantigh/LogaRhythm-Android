package com.yasinjahantigh.logarhythm.data.download.metadata

import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

/** Deezer's public search endpoint, no API key required. */
@Singleton
class DeezerClient @Inject constructor(
    private val client: OkHttpClient
) {

    data class Result(
        val title: String,
        val artist: String,
        val album: String?,
        val artworkUrl: String?
    )

    data class ArtworkCandidate(val url: String, val title: String, val artist: String, val album: String?)

    suspend fun searchCandidates(title: String, artist: String): List<ArtworkCandidate> =
        withContext(Dispatchers.IO) {
            val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return@withContext emptyList()
            val url = "https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=12"
            val data = HttpJson.get(client, url)?.optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val album = item.optJSONObject("album")
                val art = listOf("cover_xl", "cover_big", "cover_medium")
                    .map { album?.optString(it).orEmpty() }.firstOrNull { it.startsWith("http") }
                    ?: return@mapNotNull null
                ArtworkCandidate(art, item.optString("title"), item.optJSONObject("artist")?.optString("name").orEmpty(), album.optString("title").takeIf { it.isNotBlank() })
            }.distinctBy { it.url }
        }

    suspend fun search(title: String, artist: String, durationMs: Long): Result? =
        withContext(Dispatchers.IO) {
            val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return@withContext null

            val url = "https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=5"
            val data = HttpJson.get(client, url)?.optJSONArray("data") ?: return@withContext null

            val best = bestMatch(data, title, artist, durationMs) ?: return@withContext null
            val album = best.optJSONObject("album")
            Result(
                title = best.optString("title").ifBlank { title },
                artist = best.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { artist },
                album = album?.optString("title")?.takeIf { it.isNotBlank() },
                artworkUrl = listOf("cover_xl", "cover_big", "cover_medium")
                    .map { album?.optString(it).orEmpty() }
                    .firstOrNull { it.startsWith("http") }
            )
        }

    private fun bestMatch(
        data: JSONArray,
        title: String,
        artist: String,
        durationMs: Long
    ): JSONObject? {
        var best: JSONObject? = null
        var bestScore = 0.0
        for (index in 0 until data.length()) {
            val candidate = data.optJSONObject(index) ?: continue
            val candidateTitle = candidate.optString("title")
            val candidateArtist = candidate.optJSONObject("artist")?.optString("name").orEmpty()
            val titleScore = DownloadMetadataMerge.similarity(candidateTitle, title)
            if (titleScore < MIN_TITLE_SIMILARITY) continue

            val candidateDuration = candidate.optLong("duration", 0L) * 1000L
            val durationFits = durationMs <= 0L || candidateDuration <= 0L ||
                abs(candidateDuration - durationMs) <= DURATION_TOLERANCE_MS
            if (!durationFits) continue

            val score = titleScore * 0.7 + DownloadMetadataMerge.similarity(candidateArtist, artist) * 0.3
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    private companion object {
        const val MIN_TITLE_SIMILARITY = 0.55
        const val DURATION_TOLERANCE_MS = 15_000L
    }
}
