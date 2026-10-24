package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    /** MediaStore/content URIs of finished downloads, used by the "Downloaded" library filter. */
    @Query("SELECT contentUri FROM download_history WHERE status = 'saved' AND contentUri IS NOT NULL")
    fun observeSavedContentUris(): Flow<List<String>>

    @Query("SELECT * FROM download_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query(
        "SELECT * FROM download_history WHERE sourceUrl = :url " +
            "AND status IN ('queued','downloading','converting','saving') LIMIT 1"
    )
    suspend fun findActiveByUrl(url: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        "UPDATE download_history SET title = :title, artist = :artist, album = :album, " +
            "artworkUrl = COALESCE(:artworkUrl, artworkUrl), updatedAt = :now WHERE id = :id"
    )
    suspend fun updateMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        now: Long
    )

    @Query(
        "UPDATE download_history SET status = :status, progress = :progress, etaSeconds = :eta, " +
            "statusDetail = :detail, updatedAt = :now WHERE id = :id"
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        progress: Int,
        eta: Long,
        detail: String?,
        now: Long
    )

    @Query(
        "UPDATE download_history SET status = 'saved', progress = 100, etaSeconds = 0, " +
            "mediaStoreId = :mediaStoreId, contentUri = :uri, filePath = :path, fileSizeBytes = :size, " +
            "artworkUrl = COALESCE(:artwork, artworkUrl), errorKind = NULL, errorMessage = NULL, " +
            "statusDetail = :detail, updatedAt = :now WHERE id = :id"
    )
    suspend fun markSaved(
        id: String,
        mediaStoreId: Long,
        uri: String,
        path: String?,
        size: Long,
        artwork: String?,
        detail: String?,
        now: Long
    )

    @Query(
        "UPDATE download_history SET status = 'failed', errorKind = :kind, errorMessage = :message, " +
            "statusDetail = :detail, updatedAt = :now WHERE id = :id"
    )
    suspend fun markFailed(id: String, kind: String, message: String?, detail: String?, now: Long)

    @Query("UPDATE download_history SET status = 'cancelled', updatedAt = :now WHERE id = :id")
    suspend fun markCancelled(id: String, now: Long)

    @Query(
        "UPDATE download_history SET status = 'cancelled', updatedAt = :now WHERE id = :id " +
            "AND status IN ('queued','downloading','converting','saving')"
    )
    suspend fun markCancelledIfActive(id: String, now: Long)

    /** Clears rows left active by a process kill or crash; live workers refresh updatedAt often. */
    @Query(
        "UPDATE download_history SET status = 'cancelled', updatedAt = :now " +
            "WHERE status IN ('queued','downloading','converting','saving') AND updatedAt < :cutoff"
    )
    suspend fun markStaleActive(now: Long, cutoff: Long)

    @Query(
        "UPDATE download_history SET status = 'queued', progress = 0, etaSeconds = 0, " +
            "errorKind = NULL, errorMessage = NULL, statusDetail = NULL, updatedAt = :now WHERE id = :id"
    )
    suspend fun markQueued(id: String, now: Long)

    @Query(
        "UPDATE download_history SET usedFallback = :used, statusDetail = :detail, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun markFallback(id: String, used: Boolean, detail: String?, now: Long)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM download_history WHERE status IN ('saved','failed','cancelled')")
    suspend fun deleteFinished()
}
