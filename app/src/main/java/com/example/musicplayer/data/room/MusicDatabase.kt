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
        LyricsEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
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
