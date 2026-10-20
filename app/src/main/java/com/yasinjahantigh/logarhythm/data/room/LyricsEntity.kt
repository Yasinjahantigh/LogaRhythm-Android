package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_lyrics")
data class LyricsEntity(
    @PrimaryKey
    val trackId: String,
    val content: String,
    val isSynced: Boolean,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis()
)
