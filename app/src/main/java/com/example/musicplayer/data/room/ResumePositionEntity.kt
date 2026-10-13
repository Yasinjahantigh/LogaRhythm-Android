package com.example.musicplayer.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Remembered position for long recordings (audiobooks, podcasts, DJ sets). */
@Entity(tableName = "resume_positions")
data class ResumePositionEntity(
    @PrimaryKey val trackId: String,
    val positionMs: Long,
    val updatedAt: Long
)
