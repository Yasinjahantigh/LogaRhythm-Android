package com.yasinjahantigh.logarhythm.data.radar

import com.yasinjahantigh.logarhythm.BuildConfig
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import com.yasinjahantigh.logarhythm.data.room.FollowedArtistEntity
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.model.Track
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
    val recentCatalog: List<ArtistReleaseEntity> = emptyList(),
    val updatedArtist: FollowedArtistEntity,
    val reachable: Boolean,
    val suggestVpn: Boolean
)

/**
 * Fetches verified dated releases from Cloudflare Worker `/releases` (edge-cached iTunes Search),
 * direct iTunes Search, and Deezer fallback so release dates remain accurate.
 */
@Singleton
class ReleaseRadarService @Inject constructor(
    @Suppress("unused") private val musicManClient: MusicManClient,
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
        var resolvedArtwork = artist.artworkUrl
        var anySourceSucceeded = false
        var anyNetworkFailure = false

        // 1. Cloudflare Worker `/releases` relay (fast edge-cached iTunes Search, Iran-friendly).
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

        // 2. Direct iTunes Search API fallback when worker returned few or zero results.
        if (allCandidates.size < 3) {
            try {
                val itunesList = fetchFromItunesDirect(artist.displayName)
                anySourceSucceeded = true
                if (resolvedArtwork.isNullOrBlank()) {
                    resolvedArtwork = itunesList.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                }
                allCandidates += itunesList
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                anyNetworkFailure = true
            }
        }

        // 3. Direct Deezer fallback if earlier sources returned nothing or too few dated tracks.
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
            .sortedWith(
                compareByDescending<RadarCandidate> { it.releaseTimestampMs > 0L }
                    .thenByDescending { it.releaseTimestampMs }
            )
        val knownIds = parseKnownIds(artist.knownTrackIdsJson)

        val isInitialBaseline = artist.lastCheckedAt == 0L || knownIds.isEmpty()
        val updatedKnown = LinkedHashSet<String>(knownIds.size + deduplicated.size)
        updatedKnown.addAll(knownIds)

        fun RadarCandidate.toEntity(seen: Boolean, discoveredAtMs: Long) = ArtistReleaseEntity(
            releaseId = releaseId,
            artistKey = artist.artistKey,
            artistName = artist.displayName,
            title = title,
            album = album,
            artworkUrl = artworkUrl,
            source = source,
            sourceUrl = sourceUrl,
            durationMs = durationMs,
            discoveredAt = discoveredAtMs,
            isSeen = seen
        )

        val minDisplayMs = nowMs - MAX_DISPLAY_AGE_MS
        val maxFutureMs = nowMs + SEVEN_DAYS_MS
        val recentCatalogEntities = deduplicated
            .filter { it.releaseTimestampMs in minDisplayMs..maxFutureMs }
            .take(MAX_SEED_RELEASES)
            .map { candidate ->
                candidate.toEntity(seen = true, discoveredAtMs = candidate.releaseTimestampMs)
            }

        val discoveredEntities = if (isInitialBaseline) {
            deduplicated.forEach {
                updatedKnown.add(it.releaseId)
                updatedKnown.add(titleKey(it.artistName, it.title))
            }
            emptyList()
        } else {
            val followCutoffMs = if (artist.followedAt > 0L) artist.followedAt - THIRTY_DAYS_MS else 0L
            val recencyFloorMs = maxOf(nowMs - THIRTY_DAYS_MS, followCutoffMs)
            val newOnes = diffNewCandidates(knownIds, deduplicated).filter { candidate ->
                candidate.releaseTimestampMs > 0L &&
                    candidate.releaseTimestampMs >= recencyFloorMs &&
                    candidate.releaseTimestampMs <= maxFutureMs
            }
            deduplicated.forEach {
                updatedKnown.add(it.releaseId)
                updatedKnown.add(titleKey(it.artistName, it.title))
            }
            newOnes.take(MAX_NEW_PER_CHECK).map { candidate ->
                candidate.toEntity(seen = false, discoveredAtMs = candidate.releaseTimestampMs)
            }
        }

        val cappedKnown = updatedKnown.toList().takeLast(MAX_KNOWN_IDS)
        val latestTitle = discoveredEntities.firstOrNull()?.title
            ?: recentCatalogEntities.firstOrNull()?.title
            ?: deduplicated.firstOrNull()?.title
            ?: artist.latestTrackTitle

        val updatedArtist = artist.copy(
            artworkUrl = resolvedArtwork,
            knownTrackIdsJson = encodeKnownIds(cappedKnown),
            latestTrackTitle = latestTitle,
            lastCheckedAt = if (anySourceSucceeded) nowMs else artist.lastCheckedAt
        )

        ArtistCheckResult(
            newReleases = discoveredEntities,
            recentCatalog = recentCatalogEntities,
            updatedArtist = updatedArtist,
            reachable = anySourceSucceeded,
            suggestVpn = !anySourceSucceeded && anyNetworkFailure
        )
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

    private fun fetchFromItunesDirect(artistName: String): List<RadarCandidate> {
        val encoded = URLEncoder.encode(artistName.trim(), "UTF-8")
        val req = Request.Builder()
            .url("https://itunes.apple.com/search?term=$encoded&entity=song&limit=15")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("iTunes HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            val out = mutableListOf<RadarCandidate>()
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val rowArtist = obj.optString("artistName").trim()
                if (!isArtistMatch(rowArtist, artistName)) continue
                val trackId = obj.optLong("trackId", 0L)
                val collectionId = obj.optLong("collectionId", 0L)
                val id = when {
                    trackId > 0L -> "it_trk:$trackId"
                    collectionId > 0L -> "it_alb:$collectionId"
                    else -> continue
                }
                val title = obj.optString("trackName").ifBlank { obj.optString("collectionName") }.trim()
                if (title.isBlank()) continue
                val album = obj.optString("collectionName").trim()
                val art = upgradeItunesArt(obj.optString("artworkUrl100").trim())
                val viewUrl = obj.optString("trackViewUrl")
                    .ifBlank { obj.optString("collectionViewUrl") }
                    .ifBlank { "search://$title $rowArtist" }
                val durMs = obj.optLong("trackTimeMillis", 0L).coerceAtLeast(0L)
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
            .filter { isArtistMatch(it.artist, artistName) }
        val candidates = mutableListOf<RadarCandidate>()
        for ((index, item) in albums.withIndex()) {
            var releaseDateStr = item.releaseDate
            if (releaseDateStr.isBlank() && index < 4) {
                val (header, _) = runCatching { deezerClient.lookupAlbumTracks(item.id) }.getOrDefault(null to emptyList())
                if (header != null && header.releaseDate.isNotBlank()) {
                    releaseDateStr = header.releaseDate
                }
            }
            candidates += RadarCandidate(
                releaseId = "dz_alb:${item.id}",
                title = item.title,
                album = item.album.ifBlank { item.title },
                artistName = item.artist.ifBlank { artistName },
                artworkUrl = item.thumbnailUrl,
                source = item.source.raw,
                sourceUrl = item.url,
                durationMs = item.durationMs,
                releaseTimestampMs = parseReleaseTimestampMs(releaseDateStr)
            )
        }
        return candidates
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
        private const val MAX_SEED_RELEASES = 6
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
        private const val MAX_DISPLAY_AGE_MS = 90L * 24L * 60L * 60L * 1000L
        private val COMBINING_MARKS = Regex("""\p{Mn}+""")
        private val WHITESPACE = Regex("""\s+""")
        private val BRACKET_SUFFIX = Regex("""\s*[\(\[](?:feat\.?|ft\.?|official|audio|video|lyric|lyrics|remix|live).*?[\)\]]""", RegexOption.IGNORE_CASE)
        private val ARTIST_DELIMITERS = Regex("""\s*(?:,|&|feat\.?|ft\.?|x|×|و)\s*""", RegexOption.IGNORE_CASE)

        fun titleKey(artist: String, title: String): String =
            "tk:${normalizeArtistKey(artist)}::${normalizeTrackTitle(title)}"

        fun normalizeTrackTitle(title: String): String {
            val cleaned = title.replace(BRACKET_SUFFIX, "")
            return normalizeArtistKey(cleaned).ifBlank { normalizeArtistKey(title) }
        }

        fun ownedTrackKeys(tracks: Collection<Track>): Set<String> = buildSet(tracks.size * 2) {
            for (track in tracks) {
                val tTitle = track.title
                if (tTitle.isBlank()) continue
                if (track.artist.isNotBlank()) {
                    add(titleKey(track.artist, tTitle))
                    track.artist.split(ARTIST_DELIMITERS).forEach { part ->
                        if (part.isNotBlank()) add(titleKey(part, tTitle))
                    }
                }
                if (track.albumArtist.isNotBlank() && !track.albumArtist.equals(track.artist, ignoreCase = true)) {
                    add(titleKey(track.albumArtist, tTitle))
                }
            }
        }

        fun isTrackOwned(artist: String, title: String, ownedKeys: Set<String>): Boolean {
            if (ownedKeys.isEmpty() || title.isBlank()) return false
            if (titleKey(artist, title) in ownedKeys) return true
            return artist.split(ARTIST_DELIMITERS).any { part ->
                part.isNotBlank() && titleKey(part, title) in ownedKeys
            }
        }

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
            val indexByTitleKey = HashMap<String, Int>()
            val result = mutableListOf<RadarCandidate>()
            for (c in candidates) {
                if (!seenIds.add(c.releaseId)) continue
                val titleKey = "${normalizeArtistKey(c.artistName)}::${normalizeArtistKey(c.title)}"
                val existingIdx = indexByTitleKey[titleKey]
                if (existingIdx == null) {
                    indexByTitleKey[titleKey] = result.size
                    result += c
                } else {
                    val existing = result[existingIdx]
                    val mergedTs = if (existing.releaseTimestampMs > 0L) existing.releaseTimestampMs else c.releaseTimestampMs
                    val mergedArt = existing.artworkUrl?.takeIf { it.isNotBlank() } ?: c.artworkUrl
                    val mergedDur = if (existing.durationMs > 0L) existing.durationMs else c.durationMs
                    if (mergedTs != existing.releaseTimestampMs || mergedArt != existing.artworkUrl || mergedDur != existing.durationMs) {
                        result[existingIdx] = existing.copy(
                            releaseTimestampMs = mergedTs,
                            artworkUrl = mergedArt,
                            durationMs = mergedDur
                        )
                    }
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
