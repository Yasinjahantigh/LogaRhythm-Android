package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists downloader search snapshots and viewed Artist / Album catalogs locally
 * so reopening an artist or album later retains its tracklist and download options
 * without requiring another network search.
 */
@Entity(
    tableName = "download_catalog_cache",
    indices = [Index("kind"), Index("updatedAt")]
)
data class DownloadCatalogEntity(
    @PrimaryKey val cacheKey: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val payloadJson: String,
    val updatedAt: Long
)
