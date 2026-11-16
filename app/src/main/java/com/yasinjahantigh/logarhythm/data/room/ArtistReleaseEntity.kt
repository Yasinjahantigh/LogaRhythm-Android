package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artist_releases",
    indices = [Index("artistKey"), Index("discoveredAt"), Index("isSeen")]
)
data class ArtistReleaseEntity(
    @PrimaryKey val releaseId: String,
    val artistKey: String,
    val artistName: String,
    val title: String,
    val album: String,
    val artworkUrl: String?,
    val source: String,
    val sourceUrl: String,
    val durationMs: Long,
    val discoveredAt: Long,
    val isSeen: Boolean
)
