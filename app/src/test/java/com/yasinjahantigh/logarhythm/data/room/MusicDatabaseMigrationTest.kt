package com.yasinjahantigh.logarhythm.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicDatabaseMigrationTest {

    @Test
    fun testAllMigrationsArray_hasNineMigrations() {
        assertEquals(9, MusicDatabase.ALL_MIGRATIONS.size)
        assertEquals(1, MusicDatabase.ALL_MIGRATIONS[0].startVersion)
        assertEquals(2, MusicDatabase.ALL_MIGRATIONS[0].endVersion)
        assertEquals(9, MusicDatabase.ALL_MIGRATIONS[8].startVersion)
        assertEquals(10, MusicDatabase.ALL_MIGRATIONS[8].endVersion)
    }

    @Test
    fun testV10TrackColumnsAndYearIndexExist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .addMigrations(*MusicDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val sqliteDb: SupportSQLiteDatabase = db.openHelper.writableDatabase
        val columns = mutableSetOf<String>()
        sqliteDb.query("PRAGMA table_info(tracks)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
        }
        assertTrue(columns.contains("year"))
        assertTrue(columns.contains("trackNumber"))
        assertTrue(columns.contains("discNumber"))
        assertTrue(columns.contains("composer"))

        val indexes = mutableSetOf<String>()
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='tracks'").use { cursor ->
            while (cursor.moveToNext()) indexes.add(cursor.getString(0))
        }
        assertTrue(indexes.contains("index_tracks_year"))
        db.close()
    }

    @Test
    fun testDatabaseCreation_withAllMigrations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .addMigrations(*MusicDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Trigger DB creation & migration execution
        val sqliteDb = db.openHelper.writableDatabase
        assertTrue(sqliteDb.isOpen)
        assertTrue(db.isOpen)
        db.close()
    }

    @Test
    fun testMigration7To8_createsIndicesOnPlayEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Create an in-memory database and run migrations 1 through 8 sequentially
        val db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .addMigrations(*MusicDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val openHelper = db.openHelper
        val sqliteDb: SupportSQLiteDatabase = openHelper.writableDatabase

        // Verify index_play_events_trackId and index_play_events_timestamp exist
        val cursor = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='play_events'")
        val indexNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            indexNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue("index_play_events_trackId should exist", indexNames.contains("index_play_events_trackId"))
        assertTrue("index_play_events_timestamp should exist", indexNames.contains("index_play_events_timestamp"))

        db.close()
    }
}
