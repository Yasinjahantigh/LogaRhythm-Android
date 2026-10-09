package com.example.musicplayer.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String?,
    val sourceType: String,
    val uri: String,
    val dateModified: Long = 0L,
    @ColumnInfo(defaultValue = "0") val dateAdded: Long = 0L,
    val folder: String = "",
    val genre: String = ""
) {
    fun toDomain(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUri = artworkUri,
        sourceType = if (sourceType == SourceType.LOCAL.prefix) SourceType.LOCAL else SourceType.WEB,
        uri = uri,
        dateModified = dateModified,
        dateAdded = dateAdded,
        folder = folder,
        genre = genre
    )

    companion object {
        fun fromDomain(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkUri = track.artworkUri,
            sourceType = track.sourceType.prefix,
            uri = track.uri,
            dateModified = track.dateModified,
            dateAdded = track.dateAdded,
            folder = track.folder,
            genre = track.genre
        )
    }
}
