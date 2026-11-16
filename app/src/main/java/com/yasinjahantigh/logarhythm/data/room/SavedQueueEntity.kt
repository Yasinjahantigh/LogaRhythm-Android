package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_queues",
    indices = [Index("updatedAt")]
)
data class SavedQueueEntity(
    @PrimaryKey val queueId: String,
    val name: String,
    val trackIdsJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val updatedAt: Long
)
