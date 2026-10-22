package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus

@Entity(
    tableName = "download_history",
    indices = [Index("createdAt"), Index("status")]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val source: String,
    val sourceUrl: String,
    val videoId: String,
    val quality: String,
    val status: String,
    val progress: Int,
    val etaSeconds: Long,
    val artworkUrl: String?,
    val mediaStoreId: Long?,
    val contentUri: String?,
    val filePath: String?,
    val fileSizeBytes: Long,
    val errorKind: String?,
    val errorMessage: String?,
    val statusDetail: String?,
    val usedFallback: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): DownloadItem = DownloadItem(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        source = DownloadSource.fromRaw(source),
        sourceUrl = sourceUrl,
        videoId = videoId,
        quality = DownloadQuality.fromPref(quality),
        status = DownloadStatus.fromRaw(status),
        progress = progress,
        etaSeconds = etaSeconds,
        artworkUrl = artworkUrl,
        mediaStoreId = mediaStoreId,
        contentUri = contentUri,
        filePath = filePath,
        fileSizeBytes = fileSizeBytes,
        errorKind = DownloadErrorKind.fromRaw(errorKind),
        errorMessage = errorMessage,
        statusDetail = statusDetail,
        usedFallback = usedFallback,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
