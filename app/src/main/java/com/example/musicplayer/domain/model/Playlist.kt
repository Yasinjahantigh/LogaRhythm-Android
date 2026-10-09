package com.example.musicplayer.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isBuiltIn: Boolean = false
) {
    companion object {
        const val FAVORITES_ID = 1L
        const val FAVORITES_NAME = "Favorites"
    }
}
