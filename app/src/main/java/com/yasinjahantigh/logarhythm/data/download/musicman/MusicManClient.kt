package com.yasinjahantigh.logarhythm.data.download.musicman

import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MusicMan API client targeting https://mm.3rah.ir/api with Bale mirror prioritization.
 * Bale mirrors stream directly via /api/proxy?message_id=<id> without needing a VPN.
 */
@Singleton
class MusicManClient @Inject constructor(
    private val client: OkHttpClient
) {

    data class Audio(
        val quality: Int,
        val url: String,
        val filename: String?,
        val isBale: Boolean = false,
        val baleMessageId: String? = null,
        val isCached: Boolean = false
    )

    data class CollectionDetail(
        val header: DownloadSearchResult?,
        val albums: List<DownloadSearchResult>,
        val tracks: List<DownloadSearchResult>
    )

    internal var apiBase: String = API_BASE
    internal var proxyBase: String = PROXY_BASE
    internal var pollIntervalMs: Long = POLL_INTERVAL_MS
    internal var crawlTimeoutMs: Long = CRAWL_TIMEOUT_MS

    suspend fun search(
        term: String,
        category: DownloadSearchCategory = DownloadSearchCategory.ALL,
        limit: Int = SEARCH_LIMIT
    ): List<DownloadSearchResult> = withContext(Dispatchers.IO) {
        val query = term.trim()
        if (query.isBlank()) return@withContext emptyList()
        val entity = when (category) {
            DownloadSearchCategory.TRACK -> "song"
            DownloadSearchCategory.ALBUM -> "album"
            DownloadSearchCategory.ARTIST -> "musicArtist"
            DownloadSearchCategory.ALL -> "musicArtist,album,song"
        }
        val url = "$apiBase/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("term", query)
            ?.addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            ?.addQueryParameter("entity", entity)
            ?.build()
            ?.toString()
            ?: throw DownloadPipelineException(
                DownloadErrorKind.UNKNOWN,
                "MusicMan search URL is invalid."
            )
        parseSearch(executeJson(Request.Builder().url(url).get().build()), category)
    }

    suspend fun lookupAlbum(albumId: String): CollectionDetail = withContext(Dispatchers.IO) {
        val clean = cleanId(albumId)
        val url = requestUrl("$apiBase/lookup") {
            addQueryParameter("id", clean)
            addQueryParameter("entity", "song")
            addQueryParameter("limit", "200")
        }
        val body = executeJson(Request.Builder().url(url).get().build())
        val results = body.optJSONArray("results") ?: return@withContext CollectionDetail(null, emptyList(), emptyList())
        var header: DownloadSearchResult? = null
        val tracks = LinkedHashMap<String, DownloadSearchResult>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            when (item.optString("wrapperType").lowercase()) {
                "collection" -> header = item.toAlbumResult()
                "track" -> item.toTrackResult(
                    fallbackAlbum = header?.title.orEmpty(),
                    fallbackAlbumArtist = header?.artist.orEmpty()
                )?.let { tracks.putIfAbsent(it.id, it) }
            }
        }
        CollectionDetail(
            header = header,
            albums = emptyList(),
            tracks = tracks.values.sortedWith(compareBy({ it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title }))
        )
    }

    suspend fun fetchArtistCatalog(
        artistId: String,
        page: Int = 1,
        limit: Int = 50
    ): CollectionDetail = withContext(Dispatchers.IO) {
        val clean = cleanId(artistId)
        val tracksUrl = requestUrl("$apiBase/artist/tracks") {
            addQueryParameter("id", clean)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", limit.toString())
            addQueryParameter("sort", "album")
        }
        val body = executeJson(Request.Builder().url(tracksUrl).get().build())
        val artistHeader = body.optJSONObject("artist")?.toArtistResult()
        val rawTracks = body.optJSONArray("tracks") ?: body.optJSONArray("results")
        val tracks = LinkedHashMap<String, DownloadSearchResult>()
        val albums = LinkedHashMap<String, DownloadSearchResult>()
        if (rawTracks != null) {
            for (i in 0 until rawTracks.length()) {
                val item = rawTracks.optJSONObject(i) ?: continue
                when (item.optString("wrapperType").lowercase()) {
                    "artist" -> Unit
                    "collection" -> item.toAlbumResult()?.let { albums.putIfAbsent(it.id, it) }
                    else -> {
                        val tr = item.toTrackResult(
                            fallbackAlbum = "",
                            fallbackAlbumArtist = artistHeader?.title.orEmpty()
                        ) ?: continue
                        tracks.putIfAbsent(tr.id, tr)
                        val cid = tr.collectionId
                        if (!cid.isNullOrBlank() && tr.album.isNotBlank() && !albums.containsKey(cid)) {
                            albums[cid] = DownloadSearchResult(
                                id = cid,
                                title = tr.album,
                                artist = tr.albumArtist.ifBlank { tr.artist },
                                source = DownloadSource.MUSICMAN,
                                url = ALBUM_URL_PREFIX + cid,
                                durationMs = 0L,
                                thumbnailUrl = tr.thumbnailUrl,
                                kind = DownloadResultKind.ALBUM,
                                album = tr.album,
                                albumArtist = tr.albumArtist.ifBlank { tr.artist },
                                collectionId = cid,
                                artistId = tr.artistId ?: clean
                            )
                        }
                    }
                }
            }
        }
        CollectionDetail(
            header = artistHeader,
            albums = albums.values.toList(),
            tracks = tracks.values.toList()
        )
    }

    /**
     * Fetches the artist's full paginated catalog (not capped at a single 50-track page),
     * streaming progressive snapshots via [onProgress] as each page arrives.
     */
    suspend fun fetchFullArtistCatalog(
        artistId: String,
        pageSize: Int = 50,
        maxPages: Int = 20,
        onProgress: suspend (CollectionDetail) -> Unit = {}
    ): CollectionDetail = withContext(Dispatchers.IO) {
        var header: DownloadSearchResult? = null
        val allTracks = LinkedHashMap<String, DownloadSearchResult>()
        val allAlbums = LinkedHashMap<String, DownloadSearchResult>()

        for (page in 1..maxPages) {
            val detail = try {
                fetchArtistCatalog(artistId = artistId, page = page, limit = pageSize)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (page == 1) throw e else break
            }

            if (header == null && detail.header != null) header = detail.header
            var addedThisPage = 0
            for (alb in detail.albums) {
                allAlbums.putIfAbsent(alb.id, alb)
            }
            for (tr in detail.tracks) {
                if (allTracks.putIfAbsent(tr.id, tr) == null) {
                    addedThisPage++
                }
            }

            val snapshot = CollectionDetail(
                header = header,
                albums = allAlbums.values.toList(),
                tracks = allTracks.values.toList()
            )
            onProgress(snapshot)

            if (detail.tracks.isEmpty() || addedThisPage == 0 || detail.tracks.size < pageSize) {
                break
            }
        }

        CollectionDetail(
            header = header,
            albums = allAlbums.values.toList(),
            tracks = allTracks.values.toList()
        )
    }

    suspend fun fetchFresh(limit: Int = 20): List<DownloadSearchResult> = fetchFeed("fresh", limit)

    suspend fun fetchPopular(limit: Int = 20): List<DownloadSearchResult> = fetchFeed("popular", limit)

    private suspend fun fetchFeed(path: String, limit: Int): List<DownloadSearchResult> =
        withContext(Dispatchers.IO) {
            val url = requestUrl("$apiBase/$path") {
                addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            }
            val text = executeRaw(Request.Builder().url(url).get().build())
            val array = runCatching {
                val trimmed = text.trim()
                if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("results")
                        ?: obj.optJSONArray("tracks")
                        ?: obj.optJSONArray("data")
                }
            }.getOrNull() ?: return@withContext emptyList()
            val items = LinkedHashMap<String, DownloadSearchResult>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                obj.toTrackResult()?.let { items.putIfAbsent(it.id, it) }
            }
            items.values.toList()
        }

    /**
     * Resolves ranked audio mirrors for [trackId] (Bale mirrors first, then cached, then HTTPS).
     * If the track is not yet cached on the server, queues a crawl job and polls until ready.
     */
    suspend fun resolveAudioCandidates(
        trackId: String,
        preferredQuality: DownloadQuality = DownloadQuality.BEST,
        onStatus: (Int, String) -> Unit = { _, _ -> }
    ): List<Audio> {
        val clean = cleanId(trackId)
        audiosFromLookup(clean, preferredQuality).takeIf { it.isNotEmpty() }?.let { return it }

        val existing = downloadStatus(clean, preferredQuality)
        existing?.let { download ->
            audiosOf(download, preferredQuality).takeIf { it.isNotEmpty() }?.let { return it }
            terminalFailure(download)?.let { throw it }
        }
        if (existing == null) {
            requestDownload(clean, preferredQuality)
            onStatus(0, "musicman_queued")
        }

        val deadline = System.currentTimeMillis() + crawlTimeoutMs
        var lastPercent = -1
        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            val download = downloadStatus(clean, preferredQuality) ?: continue
            audiosOf(download, preferredQuality).takeIf { it.isNotEmpty() }?.let { return it }
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

    suspend fun resolveAudio(
        trackId: String,
        preferredQuality: DownloadQuality = DownloadQuality.BEST,
        onStatus: (Int, String) -> Unit = { _, _ -> }
    ): Audio = resolveAudioCandidates(trackId, preferredQuality, onStatus).first()

    fun playableUrl(audio: Audio): String {
        val msgId = audio.baleMessageId ?: extractBaleMessageId(audio.url)
        if (audio.isBale && !msgId.isNullOrBlank()) {
            return baleProxyUrl(msgId)
        }
        val parsed = audio.url.toHttpUrlOrNull()
        val host = parsed?.host.orEmpty()
        val isCleartextHttp = parsed != null && !parsed.isHttps
        val needsProxy = isCleartextHttp ||
            host.endsWith("telegram.org") ||
            host.endsWith("t.me") ||
            audio.url.contains("<token>")
        return if (needsProxy && proxyBase.isNotBlank()) proxied(audio.url) else audio.url
    }

    fun baleProxyUrl(messageId: String): String {
        val base = proxyBase.substringBefore('?').trimEnd('/')
        return "$base?message_id=${URLEncoder.encode(messageId, Charsets.UTF_8.name())}"
    }

    fun proxied(url: String): String {
        val msgId = extractBaleMessageId(url)
        if (!msgId.isNullOrBlank()) return baleProxyUrl(msgId)
        val encoded = URLEncoder.encode(url, Charsets.UTF_8.name())
        return if (proxyBase.contains("?url=")) {
            proxyBase + encoded
        } else {
            val base = proxyBase.substringBefore('?').trimEnd('/')
            "$base?url=$encoded"
        }
    }

    internal fun parseSearch(
        body: JSONObject,
        category: DownloadSearchCategory = DownloadSearchCategory.TRACK
    ): List<DownloadSearchResult> {
        val results = body.optJSONArray("results") ?: return emptyList()
        val items = LinkedHashMap<String, DownloadSearchResult>()
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val wrapper = item.optString("wrapperType").lowercase()
            when {
                (category == DownloadSearchCategory.ALL || category == DownloadSearchCategory.ARTIST) &&
                    wrapper == "artist" -> {
                    item.toArtistResult()?.let { items.putIfAbsent("artist:${it.id}", it) }
                }
                (category == DownloadSearchCategory.ALL || category == DownloadSearchCategory.ALBUM) &&
                    wrapper == "collection" -> {
                    item.toAlbumResult()?.let { items.putIfAbsent("album:${it.id}", it) }
                }
                (category == DownloadSearchCategory.ALL || category == DownloadSearchCategory.TRACK) &&
                    wrapper == "track" -> {
                    item.toTrackResult()?.let { items.putIfAbsent("track:${it.id}", it) }
                }
            }
        }
        return items.values.toList()
    }

    internal fun audioOf(download: JSONObject): Audio? = audiosOf(download).firstOrNull()

    internal fun audiosOf(
        download: JSONObject,
        preferredQuality: DownloadQuality = DownloadQuality.BEST
    ): List<Audio> = download.rankedAudios(preferredQuality)

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

    private fun JSONObject.toArtistResult(): DownloadSearchResult? {
        val id = opt("artistId")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val name = optString("artistName").trim().ifBlank { return null }
        return DownloadSearchResult(
            id = id,
            title = name,
            artist = optString("primaryGenreName").trim(),
            source = DownloadSource.MUSICMAN,
            url = ARTIST_URL_PREFIX + id,
            durationMs = 0L,
            thumbnailUrl = bestArtwork() ?: optString("artworkUrl100").takeIf { it.startsWith("http") }?.let(::upgradeItunesArtwork),
            kind = DownloadResultKind.ARTIST,
            artistId = id
        )
    }

    private fun JSONObject.toAlbumResult(): DownloadSearchResult? {
        val id = opt("collectionId")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val title = optString("collectionName").trim().ifBlank { return null }
        val artist = optString("artistName").trim()
        return DownloadSearchResult(
            id = id,
            title = title,
            artist = artist,
            source = DownloadSource.MUSICMAN,
            url = ALBUM_URL_PREFIX + id,
            durationMs = 0L,
            thumbnailUrl = bestArtwork() ?: optString("artworkUrl100").takeIf { it.startsWith("http") }?.let(::upgradeItunesArtwork),
            kind = DownloadResultKind.ALBUM,
            album = title,
            albumArtist = artist,
            trackCount = optInt("trackCount", 0).coerceAtLeast(0),
            collectionId = id,
            artistId = opt("artistId")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            releaseDate = optString("releaseDate").trim().takeIf { it.length >= 4 && !it.equals("null", true) }.orEmpty()
        )
    }

    private fun JSONObject.toTrackResult(
        fallbackAlbum: String = "",
        fallbackAlbumArtist: String = ""
    ): DownloadSearchResult? {
        val trackId = trackId() ?: return null
        val title = trackTitle() ?: return null
        val artist = optString("artistName").trim()
        val albumName = optString("collectionName").trim().ifBlank { fallbackAlbum }
        val albumArtistName = optString("collectionArtistName").trim()
            .ifBlank { fallbackAlbumArtist }
            .ifBlank { artist }
        val artwork = bestArtwork()
            ?: optString("artworkUrl100").takeIf { it.startsWith("http") }?.let(::upgradeItunesArtwork)
        val rawReleaseDate = optString("releaseDate").trim()
            .takeIf { it.length >= 4 && !it.equals("null", ignoreCase = true) }
            .orEmpty()
        return DownloadSearchResult(
            id = trackId,
            title = title,
            artist = artist,
            source = DownloadSource.MUSICMAN,
            url = TRACK_URL_PREFIX + trackId,
            durationMs = optLong("trackTimeMillis", 0L).coerceAtLeast(0L),
            thumbnailUrl = artwork,
            kind = DownloadResultKind.TRACK,
            album = albumName,
            albumArtist = albumArtistName,
            trackNumber = optInt("trackNumber", 0).coerceAtLeast(0),
            collectionId = opt("collectionId")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            artistId = opt("artistId")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
            hasReadyAudio = bestAudio() != null,
            releaseDate = rawReleaseDate
        )
    }

    private fun JSONObject.trackId(): String? =
        opt("trackId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.trackTitle(): String? = listOf(
        optString("trackName"),
        optString("trackCensoredName")
    ).map { it.trim() }.firstOrNull { it.isNotBlank() }

    private fun JSONObject.bestAudio(): Audio? = rankedAudios().firstOrNull()

    private fun JSONObject.rankedAudios(
        preferredQuality: DownloadQuality = DownloadQuality.BEST
    ): List<Audio> {
        val audioUrls = optJSONObject("attachments")?.optJSONArray("audioUrls") ?: return emptyList()
        val targetBitrate = preferredQuality.prefValue.toIntOrNull()?.takeIf { it > 0 }
        return (0 until audioUrls.length())
            .mapNotNull { audioUrls.optJSONObject(it)?.toAudio() }
            .sortedWith(
                compareByDescending<Audio> { it.isBale }
                    .thenByDescending { it.isCached }
                    .thenBy { audio ->
                        if (targetBitrate == null) {
                            -audio.quality
                        } else {
                            // Prefer bitrates closest to requested target without exceeding if equal options exist.
                            kotlin.math.abs((if (audio.quality > 0) audio.quality else 320) - targetBitrate)
                        }
                    }
                    .thenByDescending { it.quality }
            )
    }

    private fun JSONObject.toAudio(): Audio? {
        val source = optString("source").trim().lowercase()
        val baleFileUrl = optString("baleFileUrl").trim()
        val rawUrl = optString("url").trim()
        val explicitBaleId = optString("baleMessageId").trim().takeIf { it.isNotBlank() }
        val effectiveUrl = when {
            baleFileUrl.isNotBlank() -> baleFileUrl
            rawUrl.isNotBlank() -> rawUrl
            explicitBaleId != null -> "bale://message/$explicitBaleId"
            else -> return null
        }
        val baleId = explicitBaleId ?: extractBaleMessageId(effectiveUrl)
        val isBale = source == "bale" ||
            source == "balethon" ||
            baleId != null ||
            effectiveUrl.startsWith("bale://", ignoreCase = true) ||
            effectiveUrl.contains("bale.ai/", ignoreCase = true) ||
            effectiveUrl.contains("ble.ir/", ignoreCase = true)
        val isHttp = effectiveUrl.startsWith("https://", ignoreCase = true) ||
            effectiveUrl.startsWith("http://", ignoreCase = true)
        if (!isBale && !isHttp) return null

        return Audio(
            quality = opt("quality")?.toString()?.toIntOrNull() ?: 0,
            url = if (isBale && !baleId.isNullOrBlank()) "bale://message/$baleId" else effectiveUrl,
            filename = optString("telegramFilename").takeIf { it.isNotBlank() }
                ?: optString("filename").takeIf { it.isNotBlank() },
            isBale = isBale,
            baleMessageId = baleId,
            isCached = source == "cache"
        )
    }

    private fun extractBaleMessageId(url: String): String? {
        val match = BALE_URI_REGEX.find(url.trim()) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { BALE_ID_REGEX.matches(it) }
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

    private fun upgradeItunesArtwork(url: String): String {
        val httpsUrl = if (url.startsWith("http://", ignoreCase = true)) {
            "https://" + url.substring(7)
        } else url
        return if (httpsUrl.contains("mzstatic.com")) {
            httpsUrl.replace(Regex("/\\d+x\\d+bb\\.(jpg|jpeg|png)$"), "/600x600bb.$1")
        } else {
            httpsUrl
        }
    }

    private suspend fun audiosFromLookup(
        trackId: String,
        preferredQuality: DownloadQuality = DownloadQuality.BEST
    ): List<Audio> {
        val url = requestUrl("$apiBase/lookup") {
            addQueryParameter("id", trackId)
            addQueryParameter("entity", "song")
            addQueryParameter("limit", "1")
        }
        val body = executeJson(Request.Builder().url(url).get().build())
        val results = body.optJSONArray("results") ?: return emptyList()
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            if (item.trackId() != trackId) continue
            return item.rankedAudios(preferredQuality)
        }
        return emptyList()
    }

    private suspend fun downloadStatus(
        trackId: String,
        preferredQuality: DownloadQuality = DownloadQuality.BEST
    ): JSONObject? {
        val url = requestUrl("$apiBase/download/status") {
            addQueryParameter("trackId", trackId)
            val q = preferredQuality.prefValue.takeIf { it != "0" }
            if (q != null) addQueryParameter("quality", q)
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

    private suspend fun requestDownload(
        trackId: String,
        preferredQuality: DownloadQuality = DownloadQuality.BEST
    ) {
        val q = preferredQuality.prefValue.takeIf { it != "0" } ?: "320"
        val payload = JSONObject()
            .put("trackId", trackId)
            .put("quality", q)
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

    private fun cleanId(raw: String): String = raw.trim().removePrefix("it_")

    private fun requestUrl(
        base: String,
        build: okhttp3.HttpUrl.Builder.() -> Unit
    ): String {
        val builder = base.toHttpUrlOrNull()?.newBuilder()
            ?: throw DownloadPipelineException(DownloadErrorKind.UNKNOWN, "MusicMan URL is invalid.")
        return builder.apply(build).build().toString()
    }

    private fun executeRaw(request: Request): String {
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
                return text
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

    private fun executeJson(request: Request): JSONObject {
        val text = executeRaw(request)
        return runCatching { JSONObject(text) }.getOrElse { error ->
            throw DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                "MusicMan returned an invalid response.",
                error
            )
        }
    }

    private companion object {
        const val API_BASE = "https://mm.3rah.ir/api"
        const val PROXY_BASE = "https://mm.3rah.ir/api/proxy"
        const val TRACK_URL_PREFIX = "musicman://track/"
        const val ALBUM_URL_PREFIX = "musicman://album/"
        const val ARTIST_URL_PREFIX = "musicman://artist/"
        const val SEARCH_LIMIT = 50
        const val POLL_INTERVAL_MS = 2_000L
        const val CRAWL_TIMEOUT_MS = 60_000L
        val BALE_URI_REGEX = Regex("^bale://(?:message/)?([^/?#]+)", RegexOption.IGNORE_CASE)
        val BALE_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
