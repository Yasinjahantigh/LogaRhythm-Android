package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.room.DownloadCatalogDao
import com.yasinjahantigh.logarhythm.data.room.DownloadCatalogEntity
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Room-backed cache for downloader search snapshots and viewed Artist / Album catalogs.
 * Allows reopening any artist or album from search later without re-searching online.
 */
@Singleton
class DownloadCatalogCache @Inject constructor(
    private val dao: DownloadCatalogDao
) {
    data class CachedCollection(
        val cacheKey: String,
        val kind: DownloadResultKind,
        val header: DownloadSearchResult,
        val albums: List<DownloadSearchResult>,
        val tracks: List<DownloadSearchResult>,
        val updatedAt: Long
    )

    data class SavedCollectionSummary(
        val cacheKey: String,
        val kind: DownloadResultKind,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
        val updatedAt: Long
    )

    fun observeSavedCollections(): Flow<List<SavedCollectionSummary>> =
        dao.observeSavedCollections().map { rows ->
            rows.map { row ->
                SavedCollectionSummary(
                    cacheKey = row.cacheKey,
                    kind = if (row.kind == "artist") DownloadResultKind.ARTIST else DownloadResultKind.ALBUM,
                    title = row.title,
                    subtitle = row.subtitle,
                    thumbnailUrl = row.thumbnailUrl,
                    updatedAt = row.updatedAt
                )
            }
        }

    suspend fun saveSearch(
        query: String,
        category: DownloadSearchCategory,
        source: DownloadSource?,
        results: List<DownloadSearchResult>
    ) {
        if (query.isBlank() || results.isEmpty()) return
        val key = searchKey(query, category, source)
        val payload = JSONObject()
            .put("results", results.toJsonArray())
            .toString()
        dao.upsert(
            DownloadCatalogEntity(
                cacheKey = key,
                kind = "search",
                title = query.trim(),
                subtitle = category.name,
                thumbnailUrl = results.firstOrNull()?.thumbnailUrl,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSearch(
        query: String,
        category: DownloadSearchCategory,
        source: DownloadSource?
    ): List<DownloadSearchResult>? {
        if (query.isBlank()) return null
        val entity = dao.getByKey(searchKey(query, category, source)) ?: return null
        val arr = runCatching { JSONObject(entity.payloadJson).optJSONArray("results") }.getOrNull() ?: return null
        return arr.toResults()
    }

    suspend fun saveCollection(
        header: DownloadSearchResult,
        albums: List<DownloadSearchResult>,
        tracks: List<DownloadSearchResult>
    ) {
        val key = collectionKey(header)
        val payload = JSONObject()
            .put("header", header.toJson())
            .put("albums", albums.toJsonArray())
            .put("tracks", tracks.toJsonArray())
            .toString()
        dao.upsert(
            DownloadCatalogEntity(
                cacheKey = key,
                kind = if (header.kind == DownloadResultKind.ARTIST) "artist" else "album",
                title = header.title,
                subtitle = header.artist,
                thumbnailUrl = header.thumbnailUrl,
                payloadJson = payload,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getCollection(cacheKey: String): CachedCollection? {
        val entity = dao.getByKey(cacheKey) ?: return null
        return runCatching {
            val root = JSONObject(entity.payloadJson)
            val header = root.optJSONObject("header")?.toResult() ?: return null
            val albums = root.optJSONArray("albums")?.toResults().orEmpty()
            val tracks = root.optJSONArray("tracks")?.toResults().orEmpty()
            CachedCollection(
                cacheKey = entity.cacheKey,
                kind = if (entity.kind == "artist") DownloadResultKind.ARTIST else DownloadResultKind.ALBUM,
                header = header,
                albums = albums,
                tracks = tracks,
                updatedAt = entity.updatedAt
            )
        }.getOrNull()
    }

    suspend fun getCollection(item: DownloadSearchResult): CachedCollection? =
        getCollection(collectionKey(item))

    suspend fun removeCollection(cacheKey: String) {
        dao.deleteByKey(cacheKey)
    }

    fun collectionKey(item: DownloadSearchResult): String {
        val type = if (item.kind == DownloadResultKind.ARTIST) "artist" else "album"
        return "$type:${item.source.raw}:${item.id}"
    }

    private fun searchKey(
        query: String,
        category: DownloadSearchCategory,
        source: DownloadSource?
    ): String = "search:${category.name}:${source?.raw ?: "all"}:${query.trim().lowercase()}"

    private fun List<DownloadSearchResult>.toJsonArray(): JSONArray = JSONArray().apply {
        forEach { put(it.toJson()) }
    }

    private fun DownloadSearchResult.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("source", source.raw)
        .put("url", url)
        .put("durationMs", durationMs)
        .put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
        .put("kind", kind.name)
        .put("album", album)
        .put("albumArtist", albumArtist)
        .put("trackNumber", trackNumber)
        .put("trackCount", trackCount)
        .put("collectionId", collectionId ?: JSONObject.NULL)
        .put("artistId", artistId ?: JSONObject.NULL)
        .put("hasReadyAudio", hasReadyAudio)

    private fun JSONArray.toResults(): List<DownloadSearchResult> =
        (0 until length()).mapNotNull { optJSONObject(it)?.toResult() }

    private fun JSONObject.toResult(): DownloadSearchResult? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = optString("title").takeIf { it.isNotBlank() } ?: return null
        val kind = runCatching { DownloadResultKind.valueOf(optString("kind")) }
            .getOrDefault(DownloadResultKind.TRACK)
        return DownloadSearchResult(
            id = id,
            title = title,
            artist = optString("artist"),
            source = DownloadSource.fromRaw(optString("source")),
            url = optString("url"),
            durationMs = optLong("durationMs", 0L),
            thumbnailUrl = optString("thumbnailUrl").takeIf { it.isNotBlank() && it != "null" },
            kind = kind,
            album = optString("album"),
            albumArtist = optString("albumArtist"),
            trackNumber = optInt("trackNumber", 0),
            trackCount = optInt("trackCount", 0),
            collectionId = optString("collectionId").takeIf { it.isNotBlank() && it != "null" },
            artistId = optString("artistId").takeIf { it.isNotBlank() && it != "null" },
            hasReadyAudio = optBoolean("hasReadyAudio", true)
        )
    }
}
