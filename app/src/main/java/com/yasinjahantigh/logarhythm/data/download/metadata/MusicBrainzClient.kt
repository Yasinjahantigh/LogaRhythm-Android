package com.yasinjahantigh.logarhythm.data.download.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** MusicBrainz recording lookup used only to fill what Deezer did not provide. */
@Singleton
class MusicBrainzClient @Inject constructor(
    private val client: OkHttpClient
) {

    data class Result(
        val album: String?,
        val genre: String?,
        val releaseId: String?
    )

    private val rateMutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun lookup(title: String, artist: String): Result? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        throttle()

        val query = buildString {
            append("recording:\"").append(title.replace("\"", "")).append('"')
            if (artist.isNotBlank()) {
                append(" AND artist:\"").append(artist.replace("\"", "")).append('"')
            }
        }
        val url = "https://musicbrainz.org/ws/2/recording" +
            "?query=${URLEncoder.encode(query, "UTF-8")}&fmt=json&limit=3"

        val recordings = HttpJson.get(client, url)?.optJSONArray("recordings")
            ?: return@withContext null
        val recording = recordings.optJSONObject(0) ?: return@withContext null
        val releases = recording.optJSONArray("releases")
        val release = if (releases != null && releases.length() > 0) releases.optJSONObject(0) else null

        Result(
            album = release?.optString("title")?.takeIf { it.isNotBlank() },
            genre = genreOf(recording),
            releaseId = release?.optString("id")?.takeIf { it.isNotBlank() }
        )
    }

    private fun genreOf(recording: JSONObject): String? {
        val tags = recording.optJSONArray("tags") ?: return null
        var best: String? = null
        var bestCount = -1
        for (index in 0 until tags.length()) {
            val tag = tags.optJSONObject(index) ?: continue
            val count = tag.optInt("count", 0)
            val name = tag.optString("name").takeIf { it.isNotBlank() } ?: continue
            if (count > bestCount) {
                best = name
                bestCount = count
            }
        }
        return best
    }

    /** MusicBrainz asks for at most one request per second. */
    private suspend fun throttle() {
        rateMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestAt
            val wait = MIN_INTERVAL_MS - elapsed
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1_100L
    }
}
