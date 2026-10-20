package com.yasinjahantigh.logarhythm.data.room

import com.yasinjahantigh.logarhythm.domain.model.Playlist

data class PlaylistQueryResult(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isBuiltIn: Boolean,
    val trackCount: Int
) {
    fun toDomain(): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackCount = trackCount,
            createdAt = createdAt,
            isBuiltIn = isBuiltIn
        )
    }
}
