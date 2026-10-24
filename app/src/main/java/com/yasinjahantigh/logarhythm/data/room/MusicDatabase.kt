package com.yasinjahantigh.logarhythm.data.room

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
        ResumePositionEntity::class,
        DownloadEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS play_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackId TEXT NOT NULL, timestamp INTEGER NOT NULL, durationMs INTEGER NOT NULL)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_tracks (playlistId TEXT NOT NULL, trackId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlistId, trackId))")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS lyrics (trackId TEXT NOT NULL, content TEXT NOT NULL, isSynced INTEGER NOT NULL, PRIMARY KEY(trackId))")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Preserve playlists, lyrics, and listening history when adding the MediaStore date. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tracks SET dateAdded = dateModified WHERE dateAdded = 0")
            }
        }

        /** Adds skip tracking; every existing event was a completed play, so it defaults to 0. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE play_events ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Indices for the group-by / filter queries behind Library, Artist, Album and Stats, the
         * album-artist and file-path columns, per-song lyrics offsets and long-audio resume points.
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

        /** Adds the music-download history; existing player data is untouched. */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS download_history (" +
                        "id TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, " +
                        "durationMs INTEGER NOT NULL, source TEXT NOT NULL, sourceUrl TEXT NOT NULL, " +
                        "videoId TEXT NOT NULL, quality TEXT NOT NULL, status TEXT NOT NULL, " +
                        "progress INTEGER NOT NULL, etaSeconds INTEGER NOT NULL, artworkUrl TEXT, " +
                        "mediaStoreId INTEGER, contentUri TEXT, filePath TEXT, fileSizeBytes INTEGER NOT NULL, " +
                        "errorKind TEXT, errorMessage TEXT, statusDetail TEXT, usedFallback INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_download_history_createdAt " +
                        "ON download_history (createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_download_history_status " +
                        "ON download_history (status)"
                )
            }
        }

        /** Release year, disc/track numbers and composer for sorting and the tag studio. */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN trackNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN composer TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_year ON tracks (year)")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )
    }
}
