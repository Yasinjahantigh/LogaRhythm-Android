package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "followed_artists",
    indices = [Index("followedAt")]
)
data class FollowedArtistEntity(
    @PrimaryKey val artistKey: String,
    val displayName: String,
    val musicManArtistId: String?,
    val artworkUrl: String?,
    val knownTrackIdsJson: String,
    val latestTrackTitle: String?,
    val lastCheckedAt: Long,
    val followedAt: Long
)
