package com.example.musicplayer.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-song lyrics timing correction, in milliseconds. Positive = lyrics appear earlier. */
@Entity(tableName = "lyrics_offsets")
data class LyricsOffsetEntity(
    @PrimaryKey val trackId: String,
    val offsetMs: Long
)
