package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yasinjahantigh.logarhythm.domain.model.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false
) {
    fun toDomain(trackCount: Int = 0): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackCount = trackCount,
            createdAt = createdAt,
            isBuiltIn = isBuiltIn
        )
    }
}
