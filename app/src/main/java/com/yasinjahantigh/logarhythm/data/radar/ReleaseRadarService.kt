package com.yasinjahantigh.logarhythm.data.radar

import com.yasinjahantigh.logarhythm.BuildConfig
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import com.yasinjahantigh.logarhythm.data.room.FollowedArtistEntity
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class RadarCandidate(
    val releaseId: String,
    val title: String,
    val album: String,
    val artistName: String,
    val artworkUrl: String?,
    val source: String,
    val sourceUrl: String,
    val durationMs: Long = 0L,
    val releaseTimestampMs: Long = 0L
)

data class ArtistCheckResult(
    val newReleases: List<ArtistReleaseEntity>,
    val updatedArtist: FollowedArtistEntity,
    val reachable: Boolean,
    val suggestVpn: Boolean
)

/**
 * Checks MusicMan first (no VPN needed), falls back to the Cloudflare Worker `/releases`
 * relay (edge-cached iTunes Search), then falls back to direct Deezer/iTunes.
 */
@Singleton
class ReleaseRadarService @Inject constructor(
    private val musicManClient: MusicManClient,
    private val deezerClient: DeezerClient,
    baseClient: OkHttpClient
) {

    private val client = baseClient.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    internal var workerEndpointOverride: String? = null

    private fun workerReleasesBaseUrl(): String {
        workerEndpointOverride?.let { return it }
        val hit = BuildConfig.INSTALL_ENDPOINT.takeIf { it.isNotBlank() }
            ?: DEFAULT_WORKER_HIT_ENDPOINT
        return hit.replace(Regex("""/hit/?$"""), "/releases")
    }

    suspend fun checkArtist(
        artist: FollowedArtistEntity,
        nowMs: Long = System.currentTimeMillis()
    ): ArtistCheckResult = withContext(Dispatchers.IO) {
        val allCandidates = mutableListOf<RadarCandidate>()
        var resolvedMusicManId = artist.musicManArtistId
        var resolvedArtwork = artist.artworkUrl
        var anySourceSucceeded = false
        var anyNetworkFailure = false

        // 1. MusicMan first (Iran-accessible without VPN).
        try {
            val mmResult = fetchFromMusicMan(artist, resolvedMusicManId)
            anySourceSucceeded = true
            if (mmResult.resolvedArtistId != null) {
                resolvedMusicManId = mmResult.resolvedArtistId
            }
            if (resolvedArtwork.isNullOrBlank() && !mmResult.artworkUrl.isNullOrBlank()) {
                resolvedArtwork = mmResult.artworkUrl
            }
            allCandidates += mmResult.candidates
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            anyNetworkFailure = true
        }

        // 2. Cloudflare Worker `/releases` relay (fast edge cache).
        try {
            val workerList = fetchFromWorker(artist.displayName)
            anySourceSucceeded = true
            if (resolvedArtwork.isNullOrBlank()) {
                resolvedArtwork = workerList.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
            }
            allCandidates += workerList
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            anyNetworkFailure = true
        }

        // 3. Direct Deezer fallback if earlier sources returned nothing or failed.
        if (allCandidates.isEmpty()) {
            try {
                val dzList = fetchFromDeezer(artist.displayName)
                anySourceSucceeded = true
                if (resolvedArtwork.isNullOrBlank()) {
                    resolvedArtwork = dzList.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                }
                allCandidates += dzList
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                anyNetworkFailure = true
            }
        }

        val deduplicated = deduplicateCandidates(allCandidates)
        val knownIds = parseKnownIds(artist.knownTrackIdsJson)

        val isInitialBaseline = artist.lastCheckedAt == 0L || knownIds.isEmpty()
        val updatedKnown = LinkedHashSet<String>(knownIds.size + deduplicated.size)
        updatedKnown.addAll(knownIds)

        val discoveredEntities = if (isInitialBaseline) {
            deduplicated.forEach {
                updatedKnown.add(it.releaseId)
                updatedKnown.add(titleKey(it.artistName, it.title))
            }
            emptyList()
        } else {
            val cutoffMs = if (artist.followedAt > 0L) artist.followedAt - THIRTY_DAYS_MS else 0L
            val newOnes = diffNewCandidates(knownIds, deduplicated).filter { candidate ->
                candidate.releaseTimestampMs <= 0L || cutoffMs <= 0L || candidate.releaseTimestampMs >= cutoffMs
            }
            deduplicated.forEach {
                updatedKnown.add(it.releaseId)
                updatedKnown.add(titleKey(it.artistName, it.title))
            }
            newOnes.take(MAX_NEW_PER_CHECK).map { candidate ->
                ArtistReleaseEntity(
                    releaseId = candidate.releaseId,
                    artistKey = artist.artistKey,
                    artistName = artist.displayName,
                    title = candidate.title,
                    album = candidate.album,
                    artworkUrl = candidate.artworkUrl,
                    source = candidate.source,
                    sourceUrl = candidate.sourceUrl,
                    durationMs = candidate.durationMs,
                    discoveredAt = nowMs,
                    isSeen = false
                )
            }
        }

        val cappedKnown = updatedKnown.toList().takeLast(MAX_KNOWN_IDS)
        val latestTitle = discoveredEntities.firstOrNull()?.title
            ?: deduplicated.firstOrNull()?.title
            ?: artist.latestTrackTitle

        val updatedArtist = artist.copy(
            musicManArtistId = resolvedMusicManId,
            artworkUrl = resolvedArtwork,
            knownTrackIdsJson = encodeKnownIds(cappedKnown),
            latestTrackTitle = latestTitle,
            lastCheckedAt = if (anySourceSucceeded) nowMs else artist.lastCheckedAt
        )

        ArtistCheckResult(
            newReleases = discoveredEntities,
            updatedArtist = updatedArtist,
            reachable = anySourceSucceeded,
            suggestVpn = !anySourceSucceeded && anyNetworkFailure
        )
    }

    private data class MusicManFetch(
        val candidates: List<RadarCandidate>,
        val resolvedArtistId: String?,
        val artworkUrl: String?
    )

    private suspend fun fetchFromMusicMan(
        artist: FollowedArtistEntity,
        existingId: String?
    ): MusicManFetch {
        var resolvedId = existingId?.takeIf { it.isNotBlank() }
        var artwork: String? = null
        val collected = mutableListOf<DownloadSearchResult>()

        if (resolvedId != null) {
            val detail = musicManClient.fetchArtistCatalog(resolvedId, page = 1, limit = 30)
            artwork = detail.header?.thumbnailUrl
            collected += detail.tracks
            collected += detail.albums
        } else {
            val results = musicManClient.search(
                term = artist.displayName,
                category = DownloadSearchCategory.ALL,
                limit = 25
            )
            val artistHit = results.firstOrNull {
                it.kind == DownloadResultKind.ARTIST && isArtistMatch(it.title, artist.displayName)
            }
            resolvedId = artistHit?.artistId ?: artistHit?.id
            artwork = artistHit?.thumbnailUrl
            collected += results.filter {
                it.kind != DownloadResultKind.ARTIST && isArtistMatch(it.artist, artist.displayName)
            }
        }

        val candidates = collected.map { item ->
            RadarCandidate(
                releaseId = "mm:${item.kind.name.lowercase()}:${item.id}",
                title = item.title,
                album = item.album.ifBlank { if (item.kind == DownloadResultKind.ALBUM) item.title else "" },
                artistName = item.artist.ifBlank { artist.displayName },
                artworkUrl = item.thumbnailUrl,
                source = item.source.raw,
                sourceUrl = item.url,
                durationMs = item.durationMs
            )
        }
        return MusicManFetch(candidates, resolvedId, artwork)
    }

    private fun fetchFromWorker(artistName: String): List<RadarCandidate> {
        val base = workerReleasesBaseUrl()
        val encoded = URLEncoder.encode(artistName.trim(), "UTF-8")
        val req = Request.Builder()
            .url("$base?artist=$encoded")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Worker HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val results = json.optJSONArray("tracks") ?: json.optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<RadarCandidate>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val rowArtist = obj.optString("artist").ifBlank { obj.optString("artistName") }.trim()
                if (!isArtistMatch(rowArtist, artistName)) continue
                val rawId = obj.optString("id").trim()
                val collectionId = obj.optLong("collectionId", 0L)
                val trackId = obj.optLong("trackId", 0L)
                val id = when {
                    rawId.isNotBlank() -> "it_trk:$rawId"
                    trackId > 0L -> "it_trk:$trackId"
                    collectionId > 0L -> "it_alb:$collectionId"
                    else -> continue
                }
                val title = obj.optString("title")
                    .ifBlank { obj.optString("trackName") }
                    .ifBlank { obj.optString("collectionName") }
                    .trim()
                if (title.isBlank()) continue
                val album = obj.optString("album").ifBlank { obj.optString("collectionName") }.trim()
                val art = upgradeItunesArt(
                    obj.optString("artworkUrl").ifBlank { obj.optString("artworkUrl100") }.trim()
                )
                val viewUrl = obj.optString("trackViewUrl")
                    .ifBlank { obj.optString("collectionViewUrl") }
                    .ifBlank { "search://$title $rowArtist" }
                val durMs = obj.optLong("durationMs", -1L).takeIf { it >= 0L }
                    ?: obj.optLong("trackTimeMillis", 0L).coerceAtLeast(0L)
                val releaseDateMs = parseReleaseTimestampMs(obj.optString("releaseDate"))
                out += RadarCandidate(
                    releaseId = id,
                    title = title,
                    album = album,
                    artistName = rowArtist.ifBlank { artistName },
                    artworkUrl = art,
                    source = "itunes",
                    sourceUrl = viewUrl,
                    durationMs = durMs,
                    releaseTimestampMs = releaseDateMs
                )
            }
            return out
        }
    }

    private suspend fun fetchFromDeezer(artistName: String): List<RadarCandidate> {
        val albums = deezerClient.searchAlbums(artistName, limit = 10)
        return albums.filter { isArtistMatch(it.artist, artistName) }.map { item ->
            RadarCandidate(
                releaseId = "dz_alb:${item.id}",
                title = item.title,
                album = item.album.ifBlank { item.title },
                artistName = item.artist.ifBlank { artistName },
                artworkUrl = item.thumbnailUrl,
                source = item.source.raw,
                sourceUrl = item.url,
                durationMs = item.durationMs
            )
        }
    }

    private fun upgradeItunesArt(url: String): String? {
        if (!url.startsWith("http")) return null
        return if (url.contains("mzstatic.com")) {
            url.replace(Regex("""/\d+x\d+bb\.(jpg|jpeg|png)$"""), "/600x600bb.$1")
        } else url
    }

    companion object {
        private const val DEFAULT_WORKER_HIT_ENDPOINT =
            "https://logarhythm-installs.yasinjahantigh.workers.dev/hit"
        private const val MAX_KNOWN_IDS = 360
        private const val MAX_NEW_PER_CHECK = 6
        private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
        private val COMBINING_MARKS = Regex("""\p{Mn}+""")
        private val WHITESPACE = Regex("""\s+""")

        fun titleKey(artist: String, title: String): String =
            "tk:${normalizeArtistKey(artist)}::${normalizeArtistKey(title)}"

        internal fun parseReleaseTimestampMs(raw: String): Long {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return 0L
            val formats = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd"
            )
            for (pattern in formats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                        isLenient = false
                    }
                    val parsed = sdf.parse(trimmed)
                    if (parsed != null) return parsed.time
                } catch (_: Exception) {
                }
            }
            return 0L
        }

        fun normalizeArtistKey(name: String): String {
            val normalized = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replace(COMBINING_MARKS, "")
                .replace('ي', 'ی')
                .replace('ى', 'ی')
                .replace('ك', 'ک')
                .lowercase()
                .replace(WHITESPACE, " ")
                .trim()
            return normalized
        }

        fun isArtistMatch(candidateArtist: String, targetArtist: String): Boolean {
            val a = normalizeArtistKey(candidateArtist)
            val b = normalizeArtistKey(targetArtist)
            if (a.isBlank() || b.isBlank()) return false
            if (a == b || a.contains(b) || b.contains(a)) return true
            return DownloadMetadataMerge.similarity(a, b) >= 0.78
        }

        internal fun diffNewCandidates(
            knownIds: Set<String>,
            candidates: List<RadarCandidate>
        ): List<RadarCandidate> = candidates.filter {
            it.releaseId !in knownIds && titleKey(it.artistName, it.title) !in knownIds
        }

        internal fun deduplicateCandidates(candidates: List<RadarCandidate>): List<RadarCandidate> {
            val seenIds = HashSet<String>()
            val seenTitleKeys = HashSet<String>()
            val result = mutableListOf<RadarCandidate>()
            for (c in candidates) {
                if (!seenIds.add(c.releaseId)) continue
                val titleKey = "${normalizeArtistKey(c.artistName)}::${normalizeArtistKey(c.title)}"
                if (seenTitleKeys.add(titleKey)) {
                    result += c
                }
            }
            return result
        }

        internal fun parseKnownIds(json: String): Set<String> {
            if (json.isBlank()) return emptySet()
            return try {
                val arr = JSONArray(json)
                buildSet(arr.length()) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            } catch (_: Exception) {
                emptySet()
            }
        }

        internal fun encodeKnownIds(ids: List<String>): String =
            JSONArray(ids).toString()
    }
}
