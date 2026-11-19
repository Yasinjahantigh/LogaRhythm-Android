package com.yasinjahantigh.logarhythm.data.repository

import android.content.Context
import android.net.Uri
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.metadata.ItunesClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarService
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistPhotoCandidate(
    val url: String,
    val label: String,
    val source: String
)

private data class StoredArtistPhoto(
    val uri: String,
    val isCustom: Boolean
)

/**
 * Fetches, caches, and lets users override artist portrait photos.
 *
 * Stored as a lightweight JSON index + local cropped JPEGs in `filesDir`, so changes reflect
 * immediately across Artist pages and the Library Artists grid with zero database migrations.
 */
@Singleton
class ArtistPhotoRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val deezerClient: DeezerClient,
    private val musicManClient: MusicManClient,
    private val itunesClient: ItunesClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeMutex = Mutex()
    private val attemptedInSession = ConcurrentHashMap<String, Long>()
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    private val indexFile: File
        get() = File(context.filesDir, "artist_photos_index.json")

    private val customDir: File
        get() = File(context.filesDir, "artist_photos").apply { mkdirs() }

    private val entries = LinkedHashMap<String, StoredArtistPhoto>()

    private val _photos = MutableStateFlow<Map<String, String>>(emptyMap())
    val photos: StateFlow<Map<String, String>> = _photos.asStateFlow()

    private val _customKeys = MutableStateFlow<Set<String>>(emptySet())
    val customKeys: StateFlow<Set<String>> = _customKeys.asStateFlow()

    init {
        scope.launch { loadFromDisk() }
    }

    fun normalizeKey(artistName: String): String = ReleaseRadarService.normalizeArtistKey(artistName)

    fun photoFor(artistName: String): String? {
        val key = normalizeKey(artistName)
        if (key.isBlank()) return null
        return _photos.value[key]
    }

    suspend fun ensureFetched(artistName: String, force: Boolean = false): String? {
        val clean = artistName.trim()
        if (!isValidArtistName(clean)) return null
        val key = normalizeKey(clean)
        if (key.isBlank()) return null

        val existing = storeMutex.withLock {
            if (entries.isEmpty()) loadFromDiskLocked()
            entries[key]
        }
        if (existing != null) {
            if (existing.isCustom || !force) return existing.uri
        }

        val now = System.currentTimeMillis()
        if (!force) {
            val lastAttempt = attemptedInSession[key]
            if (lastAttempt != null && now - lastAttempt < RETRY_COOLDOWN_MS) return existing?.uri
        }
        if (!inFlightKeys.add(key)) return existing?.uri

        return try {
            attemptedInSession[key] = now
            val fetchedUrl = fetchBestOnlinePhoto(clean)
            if (!fetchedUrl.isNullOrBlank()) {
                storeMutex.withLock {
                    // Never overwrite a custom photo chosen while this network call was in flight
                    if (entries[key]?.isCustom != true) {
                        entries[key] = StoredArtistPhoto(uri = fetchedUrl, isCustom = false)
                        saveToDiskLocked()
                        publishStateLocked()
                    }
                }
                fetchedUrl
            } else {
                existing?.uri
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            existing?.uri
        } finally {
            inFlightKeys.remove(key)
        }
    }

    /** Gently warms up portraits for visible artists in the Library grid without hammering the network. */
    fun prefetchBatch(artistNames: List<String>, limit: Int = 10) {
        scope.launch {
            var fetched = 0
            for (name in artistNames) {
                if (fetched >= limit) break
                val key = normalizeKey(name)
                if (key.isBlank() || !isValidArtistName(name)) continue
                if (_photos.value.containsKey(key) || attemptedInSession.containsKey(key)) continue
                ensureFetched(name, force = false)
                fetched++
            }
        }
    }

    suspend fun searchPhotoCandidates(query: String): List<ArtistPhotoCandidate> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext emptyList()
        val results = mutableListOf<ArtistPhotoCandidate>()

        // 1. Deezer real artist portraits
        runCatching { deezerClient.searchArtists(term, limit = 10) }
            .getOrDefault(emptyList())
            .forEach { hit ->
                val art = hit.thumbnailUrl
                if (isUsableImageUrl(art)) {
                    results += ArtistPhotoCandidate(
                        url = art!!,
                        label = hit.title.ifBlank { term },
                        source = "Deezer"
                    )
                }
            }

        // 2. MusicMan artist portraits (Iran-accessible without VPN)
        runCatching {
            musicManClient.search(term = term, category = DownloadSearchCategory.ALL, limit = 12)
        }.getOrDefault(emptyList())
            .filter { it.kind == DownloadResultKind.ARTIST }
            .forEach { hit ->
                val art = hit.thumbnailUrl
                if (isUsableImageUrl(art)) {
                    results += ArtistPhotoCandidate(
                        url = art!!,
                        label = hit.title.ifBlank { term },
                        source = "MusicMan"
                    )
                }
            }

        // 3. Fallback artwork candidates from iTunes / Deezer if few artist portraits came back
        if (results.size < 4) {
            runCatching { itunesClient.searchCandidates("", term) }
                .getOrDefault(emptyList())
                .take(6)
                .forEach { hit ->
                    if (isUsableImageUrl(hit.url)) {
                        results += ArtistPhotoCandidate(
                            url = hit.url,
                            label = hit.artist.ifBlank { hit.title },
                            source = "iTunes"
                        )
                    }
                }
        }

        results.distinctBy { it.url }.take(15)
    }

    suspend fun setCustomPhotoBytes(artistName: String, jpegBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val key = normalizeKey(artistName)
        if (key.isBlank() || jpegBytes.isEmpty()) return@withContext null
        val hash = sha1Short(key)
        val file = File(customDir, "artist_${hash}_${System.currentTimeMillis()}.jpg")
        runCatching {
            // Remove older custom files for this artist
            customDir.listFiles { f -> f.name.startsWith("artist_${hash}_") }?.forEach { it.delete() }
            file.writeBytes(jpegBytes)
            val fileUri = Uri.fromFile(file).toString()
            storeMutex.withLock {
                if (entries.isEmpty()) loadFromDiskLocked()
                entries[key] = StoredArtistPhoto(uri = fileUri, isCustom = true)
                saveToDiskLocked()
                publishStateLocked()
            }
            fileUri
        }.getOrNull()
    }

    suspend fun setCustomPhotoUrl(artistName: String, url: String) = withContext(Dispatchers.IO) {
        val key = normalizeKey(artistName)
        if (key.isBlank() || !isUsableImageUrl(url)) return@withContext
        val hash = sha1Short(key)
        runCatching {
            customDir.listFiles { f -> f.name.startsWith("artist_${hash}_") }?.forEach { it.delete() }
        }
        storeMutex.withLock {
            if (entries.isEmpty()) loadFromDiskLocked()
            entries[key] = StoredArtistPhoto(uri = url, isCustom = true)
            saveToDiskLocked()
            publishStateLocked()
        }
    }

    suspend fun resetPhoto(artistName: String) = withContext(Dispatchers.IO) {
        val key = normalizeKey(artistName)
        if (key.isBlank()) return@withContext
        val hash = sha1Short(key)
        runCatching {
            customDir.listFiles { f -> f.name.startsWith("artist_${hash}_") }?.forEach { it.delete() }
        }
        attemptedInSession.remove(key)
        storeMutex.withLock {
            if (entries.isEmpty()) loadFromDiskLocked()
            entries.remove(key)
            saveToDiskLocked()
            publishStateLocked()
        }
    }

    private suspend fun fetchBestOnlinePhoto(artistName: String): String? = withContext(Dispatchers.IO) {
        // 1. Deezer artist portrait search (high-res portraits)
        val deezerMatch = runCatching { deezerClient.searchArtists(artistName, limit = 8) }
            .getOrDefault(emptyList())
            .firstOrNull {
                isUsableImageUrl(it.thumbnailUrl) &&
                    ReleaseRadarService.isArtistMatch(it.title, artistName)
            }?.thumbnailUrl
        if (!deezerMatch.isNullOrBlank()) return@withContext deezerMatch

        // 2. MusicMan artist portrait search (works without VPN for Persian & international artists)
        val mmMatch = runCatching {
            musicManClient.search(term = artistName, category = DownloadSearchCategory.ALL, limit = 10)
        }.getOrDefault(emptyList())
            .firstOrNull {
                it.kind == DownloadResultKind.ARTIST &&
                    isUsableImageUrl(it.thumbnailUrl) &&
                    ReleaseRadarService.isArtistMatch(it.title, artistName)
            }?.thumbnailUrl
        if (!mmMatch.isNullOrBlank()) return@withContext mmMatch

        null
    }

    private suspend fun loadFromDisk() {
        storeMutex.withLock {
            loadFromDiskLocked()
            publishStateLocked()
        }
    }

    private fun loadFromDiskLocked() {
        val file = indexFile
        if (!file.isFile) return
        runCatching {
            val root = JSONObject(file.readText())
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.optJSONObject(k) ?: continue
                val uri = obj.optString("uri").trim()
                val isCustom = obj.optBoolean("custom", false)
                if (uri.isBlank()) continue
                if (uri.startsWith("file://")) {
                    val localPath = runCatching { Uri.parse(uri).path }.getOrNull()
                    if (localPath.isNullOrBlank() || !File(localPath).isFile) continue
                }
                entries[k] = StoredArtistPhoto(uri = uri, isCustom = isCustom)
            }
        }
    }

    private fun saveToDiskLocked() {
        runCatching {
            val root = JSONObject()
            for ((k, v) in entries) {
                root.put(k, JSONObject().apply {
                    put("uri", v.uri)
                    put("custom", v.isCustom)
                })
            }
            val tmp = File(context.filesDir, "artist_photos_index.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    private fun publishStateLocked() {
        _photos.value = entries.mapValues { it.value.uri }
        _customKeys.value = entries.filterValues { it.isCustom }.keys.toSet()
    }

    private fun isValidArtistName(name: String): Boolean {
        if (name.isBlank() || name.length < 2) return false
        val lower = name.lowercase()
        return lower != "<unknown>" &&
            lower != "unknown" &&
            lower != "unknown artist" &&
            lower != "various artists" &&
            lower != "null"
    }

    private fun isUsableImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        // Skip Deezer's blank placeholder avatar (empty MD5 hash or double slash)
        if (url.contains("/images/artist//") || url.contains("d41d8cd98f00b204e9800998ecf8427e")) {
            return false
        }
        return true
    }

    private fun sha1Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val RETRY_COOLDOWN_MS = 10L * 60L * 1000L
    }
}
