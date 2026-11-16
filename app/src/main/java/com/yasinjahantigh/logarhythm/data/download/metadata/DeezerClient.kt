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

    suspend fun searchDownload(term: String, limit: Int = 25): List<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult> =
        withContext(Dispatchers.IO) {
            val query = term.trim()
            if (query.isBlank()) return@withContext emptyList()
            val url = "https://api.deezer.com/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=${limit.coerceIn(1, 50)}"
            val data = HttpJson.get(client, url)?.optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", 0L).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty()
                val album = item.optJSONObject("album")
                val art = listOf("cover_xl", "cover_big", "cover_medium")
                    .map { album?.optString(it).orEmpty() }.firstOrNull { it.startsWith("http") }
                val durationMs = item.optLong("duration", 0L) * 1000L
                val link = item.optString("link").ifBlank { "https://www.deezer.com/track/$id" }
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = id,
                    title = title,
                    artist = artist,
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = link,
                    durationMs = durationMs,
                    thumbnailUrl = art,
                    album = album?.optString("title").orEmpty(),
                    albumArtist = artist
                )
            }
        }

    suspend fun searchAlbums(term: String, limit: Int = 20): List<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult> =
        withContext(Dispatchers.IO) {
            val query = term.trim()
            if (query.isBlank()) return@withContext emptyList()
            val url = "https://api.deezer.com/search/album?q=${URLEncoder.encode(query, "UTF-8")}&limit=${limit.coerceIn(1, 30)}"
            val data = HttpJson.get(client, url)?.optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", 0L).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty()
                val art = listOf("cover_xl", "cover_big", "cover_medium")
                    .map { item.optString(it) }.firstOrNull { it.startsWith("http") }
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = id,
                    title = title,
                    artist = artist,
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = "https://www.deezer.com/album/$id",
                    durationMs = 0L,
                    thumbnailUrl = art,
                    kind = com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.ALBUM,
                    album = title,
                    albumArtist = artist,
                    trackCount = item.optInt("nb_tracks", 0).coerceAtLeast(0),
                    collectionId = id
                )
            }
        }

    suspend fun searchArtists(term: String, limit: Int = 15): List<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult> =
        withContext(Dispatchers.IO) {
            val query = term.trim()
            if (query.isBlank()) return@withContext emptyList()
            val url = "https://api.deezer.com/search/artist?q=${URLEncoder.encode(query, "UTF-8")}&limit=${limit.coerceIn(1, 25)}"
            val data = HttpJson.get(client, url)?.optJSONArray("data") ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", 0L).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val art = listOf("picture_xl", "picture_big", "picture_medium")
                    .map { item.optString(it) }.firstOrNull { it.startsWith("http") }
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = id,
                    title = name,
                    artist = "",
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = "https://www.deezer.com/artist/$id",
                    durationMs = 0L,
                    thumbnailUrl = art,
                    kind = com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.ARTIST,
                    artistId = id
                )
            }
        }

    suspend fun lookupAlbumTracks(albumId: String): Pair<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult?, List<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult>> =
        withContext(Dispatchers.IO) {
            val albumJson = HttpJson.get(client, "https://api.deezer.com/album/$albumId") ?: return@withContext null to emptyList()
            val albumTitle = albumJson.optString("title").trim()
            val albumArtist = albumJson.optJSONObject("artist")?.optString("name").orEmpty().trim()
            val cover = listOf("cover_xl", "cover_big", "cover_medium")
                .map { albumJson.optString(it) }.firstOrNull { it.startsWith("http") }
            val header = if (albumTitle.isNotBlank()) {
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = albumId,
                    title = albumTitle,
                    artist = albumArtist,
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = "https://www.deezer.com/album/$albumId",
                    durationMs = 0L,
                    thumbnailUrl = cover,
                    kind = com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.ALBUM,
                    album = albumTitle,
                    albumArtist = albumArtist,
                    trackCount = albumJson.optInt("nb_tracks", 0),
                    collectionId = albumId
                )
            } else null

            val data = albumJson.optJSONObject("tracks")?.optJSONArray("data") ?: JSONArray()
            val tracks = (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val tid = item.optLong("id", 0L).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { albumArtist }
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = tid,
                    title = title,
                    artist = artist,
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = item.optString("link").ifBlank { "https://www.deezer.com/track/$tid" },
                    durationMs = item.optLong("duration", 0L) * 1000L,
                    thumbnailUrl = cover,
                    kind = com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.TRACK,
                    album = albumTitle,
                    albumArtist = albumArtist,
                    trackNumber = item.optInt("track_position", index + 1),
                    collectionId = albumId
                )
            }
            header to tracks
        }

    suspend fun fetchArtistCatalog(artistId: String): Pair<List<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult>, List<com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult>> =
        withContext(Dispatchers.IO) {
            val albumsData = HttpJson.get(client, "https://api.deezer.com/artist/$artistId/albums?limit=30")
                ?.optJSONArray("data") ?: JSONArray()
            val topData = HttpJson.get(client, "https://api.deezer.com/artist/$artistId/top?limit=50")
                ?.optJSONArray("data") ?: JSONArray()
            val albums = (0 until albumsData.length()).mapNotNull { i ->
                val item = albumsData.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optLong("id", 0L).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val art = listOf("cover_xl", "cover_big", "cover_medium")
                    .map { item.optString(it) }.firstOrNull { it.startsWith("http") }
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = id,
                    title = title,
                    artist = "",
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = "https://www.deezer.com/album/$id",
                    durationMs = 0L,
                    thumbnailUrl = art,
                    kind = com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.ALBUM,
                    album = title,
                    collectionId = id,
                    artistId = artistId
                )
            }
            val tracks = (0 until topData.length()).mapNotNull { i ->
                val item = topData.optJSONObject(i) ?: return@mapNotNull null
                val tid = item.optLong("id", 0L).takeIf { it > 0 }?.toString() ?: return@mapNotNull null
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty()
                val album = item.optJSONObject("album")
                val art = listOf("cover_xl", "cover_big", "cover_medium")
                    .map { album?.optString(it).orEmpty() }.firstOrNull { it.startsWith("http") }
                com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult(
                    id = tid,
                    title = title,
                    artist = artist,
                    source = com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER,
                    url = item.optString("link").ifBlank { "https://www.deezer.com/track/$tid" },
                    durationMs = item.optLong("duration", 0L) * 1000L,
                    thumbnailUrl = art,
                    kind = com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.TRACK,
                    album = album?.optString("title").orEmpty(),
                    albumArtist = artist,
                    artistId = artistId
                )
            }
            albums to tracks
        }

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
