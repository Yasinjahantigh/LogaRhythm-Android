package com.yasinjahantigh.logarhythm.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track

@Entity(
    tableName = "tracks",
    indices = [
        Index("artist"),
        Index("album"),
        Index("folder"),
        Index("genre"),
        Index("dateAdded"),
        Index("year")
    ]
)
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
    val genre: String = "",
    @ColumnInfo(defaultValue = "''") val albumArtist: String = "",
    @ColumnInfo(defaultValue = "''") val filePath: String = "",
    @ColumnInfo(defaultValue = "0") val year: Int = 0,
    @ColumnInfo(defaultValue = "0") val trackNumber: Int = 0,
    @ColumnInfo(defaultValue = "0") val discNumber: Int = 0,
    @ColumnInfo(defaultValue = "''") val composer: String = ""
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
        genre = genre,
        albumArtist = albumArtist,
        filePath = filePath,
        year = year,
        trackNumber = trackNumber,
        discNumber = discNumber,
        composer = composer
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
            genre = track.genre,
            albumArtist = track.albumArtist,
            filePath = track.filePath,
            year = track.year,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            composer = track.composer
        )
    }
}
