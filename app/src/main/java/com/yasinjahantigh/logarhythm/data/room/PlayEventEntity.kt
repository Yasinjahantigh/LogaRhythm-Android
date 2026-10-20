package com.yasinjahantigh.logarhythm.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_events",
    indices = [Index("trackId"), Index("timestamp")]
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trackId: String,
    val timestamp: Long,
    val listenedMs: Long,
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false
)
