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

/** iTunes Search API: no key, good album data and high-resolution covers. */
@Singleton
class ItunesClient @Inject constructor(
    private val client: OkHttpClient
) {

    data class Result(
        val title: String,
        val artist: String,
        val album: String?,
        val genre: String?,
        val artworkUrl: String?,
        val year: String?,
        val trackNumber: Int?,
        val discNumber: Int?,
        val composer: String?
    )

    suspend fun search(title: String, artist: String, durationMs: Long): Result? =
        withContext(Dispatchers.IO) {
            val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isBlank()) return@withContext null

            val url = "https://itunes.apple.com/search" +
                "?term=${URLEncoder.encode(query, "UTF-8")}&entity=song&limit=10"
            val results = HttpJson.get(client, url)?.optJSONArray("results")
                ?: return@withContext null
            val best = bestMatch(results, title, artist, durationMs) ?: return@withContext null
            Result(
                title = best.optString("trackName").ifBlank { title },
                artist = best.optString("artistName").ifBlank { artist },
                album = best.optString("collectionName").takeIf { it.isNotBlank() },
                genre = best.optString("primaryGenreName").takeIf { it.isNotBlank() },
                artworkUrl = highResArtwork(best.optString("artworkUrl100")),
                year = best.optString("releaseDate").take(4).takeIf { it.length == 4 && it.all(Char::isDigit) },
                trackNumber = best.optInt("trackNumber", 0).takeIf { it > 0 },
                discNumber = best.optInt("discNumber", 0).takeIf { it > 0 },
                composer = best.optString("composerName").takeIf { it.isNotBlank() }
            )
        }

    private fun bestMatch(
        results: JSONArray,
        title: String,
        artist: String,
        durationMs: Long
    ): JSONObject? {
        var best: JSONObject? = null
        var bestScore = 0.0
        for (index in 0 until results.length()) {
            val candidate = results.optJSONObject(index) ?: continue
            val titleScore = DownloadMetadataMerge.similarity(candidate.optString("trackName"), title)
            if (titleScore < MIN_TITLE_SIMILARITY) continue

            val candidateDuration = candidate.optLong("trackTimeMillis", 0L)
            val durationFits = durationMs <= 0L || candidateDuration <= 0L ||
                abs(candidateDuration - durationMs) <= DURATION_TOLERANCE_MS
            if (!durationFits) continue

            val score = titleScore * 0.7 +
                DownloadMetadataMerge.similarity(candidate.optString("artistName"), artist) * 0.3
            if (score > bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    private fun highResArtwork(url: String): String? {
        if (!url.startsWith("http")) return null
        return url.replace(Regex("""/\d+x\d+bb\.(jpg|png)$"""), "/600x600bb.$1")
    }

    private companion object {
        const val MIN_TITLE_SIMILARITY = 0.55
        const val DURATION_TOLERANCE_MS = 15_000L
    }
}
