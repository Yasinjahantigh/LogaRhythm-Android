package com.example.musicplayer.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlayEventEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        LyricsEntity::class,
        LyricsOffsetEntity::class,
        ResumePositionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        /**
         * Indices for the group-by / filter queries behind Library, Artist, Album and Stats, the
         * album-artist and file-path columns, per-song lyrics offsets and long-audio resume points.
         * Index names follow Room's `index_<table>_<column>` convention or schema validation fails.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN albumArtist TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN filePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_artist ON tracks (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_album ON tracks (album)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_folder ON tracks (folder)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_genre ON tracks (genre)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_dateAdded ON tracks (dateAdded)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_trackId ON play_events (trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_timestamp ON play_events (timestamp)")
                db.execSQL("CREATE TABLE IF NOT EXISTS lyrics_offsets (trackId TEXT NOT NULL, offsetMs INTEGER NOT NULL, PRIMARY KEY(trackId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS resume_positions (trackId TEXT NOT NULL, positionMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(trackId))")
            }
        }

        /** Adds skip tracking; every existing event was a completed play, so it defaults to 0. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE play_events ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Preserve playlists, lyrics, and listening history when adding the MediaStore date. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
                // Older records have no added timestamp; modification time is the safest fallback
                // until the next successful MediaStore scan refreshes them.
                db.execSQL("UPDATE tracks SET dateAdded = dateModified WHERE dateAdded = 0")
            }
        }
    }
}
