package com.yasinjahantigh.logarhythm.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yasinjahantigh.logarhythm.core.utils.SearchUtils
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: MusicDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQueryTracks_returnsInsertedTracksSortedByTitle() = runBlocking {
        val track1 = TrackEntity(
            id = "local:1",
            title = "Zebra Song",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/external/audio/media/1"
        )
        val track2 = TrackEntity(
            id = "local:2",
            title = "Apple Song",
            artist = "Artist B",
            album = "Album B",
            durationMs = 210000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/external/audio/media/2"
        )

        dao.insertTracks(listOf(track1, track2))
        val result = dao.getAllTracks().first()

        assertEquals(2, result.size)
        assertEquals("Apple Song", result[0].title)
        assertEquals("Zebra Song", result[1].title)
    }

    @Test
    fun queryAlbumsAndArtists_derivesAggregationsCorrectly() = runBlocking {
        val track1 = TrackEntity(
            id = "local:1",
            title = "Track 1",
            artist = "Coldplay",
            album = "Parachutes",
            durationMs = 180000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/external/audio/media/1",
            folder = "Music"
        )
        val track2 = TrackEntity(
            id = "local:2",
            title = "Track 2",
            artist = "Coldplay",
            album = "Parachutes",
            durationMs = 200000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/external/audio/media/2",
            folder = "Music"
        )

        dao.insertTracks(listOf(track1, track2))
        val albums = dao.getAllAlbums().first()
        val artists = dao.getAllArtists().first()
        val folders = dao.getAllFolders().first()

        assertEquals(1, albums.size)
        assertEquals("Parachutes", albums[0].album)
        assertEquals(2, albums[0].trackCount)

        assertEquals(1, artists.size)
        assertEquals("Coldplay", artists[0].artist)
        assertEquals(2, artists[0].trackCount)

        assertEquals(1, folders.size)
        assertEquals("Music", folders[0].folder)
        assertEquals(2, folders[0].trackCount)
    }

    private suspend fun insertSameNamedAlbums() {
        val base = TrackEntity("local:21", "One", "Guest A", "Same", 1000, null, SourceType.LOCAL.prefix, "content://media/21", albumArtist = "Group")
        dao.insertTracks(listOf(base, base.copy(id = "local:22", title = "Two", artist = "Guest B"), base.copy(id = "local:23", artist = "Other", albumArtist = "")))
    }

    @Test
    fun albumsGroupByAlbumArtistAndFallbackArtist() = runBlocking {
        insertSameNamedAlbums()
        assertEquals(mapOf("Group" to 2, "Other" to 1), dao.getAllAlbums().first().associate { it.artist to it.trackCount })
    }

    @Test
    fun albumPageUsesBothKeys() = runBlocking {
        insertSameNamedAlbums()
        assertEquals(listOf("local:21", "local:22"), dao.getTracksByAlbum("Same", "Group").first().map { it.id })
        assertEquals(1, dao.getAlbumByName("Same", "Other").first()?.trackCount)
        assertEquals(null, dao.getAlbumByName("Same", "Missing").first())
    }

    @Test
    fun albumSearchMatchesGroupingAndAlbumArtist() = runBlocking {
        insertSameNamedAlbums()
        assertEquals(dao.getAllAlbums().first(), dao.searchAlbums("Same", "Same").first())
        val group = dao.searchAlbums("Group", "Group").first().single()
        assertEquals("Group", group.artist)
        assertEquals(2, group.trackCount)
    }

    @Test
    fun searchTracks_matchesPersianTextWithNormalization() = runBlocking {
        // Track stored with Persian Yeh
        val track = TrackEntity(
            id = "local:10",
            title = "شجریان تصنیف",
            artist = "محمدرضا شجریان",
            album = "دستان",
            durationMs = 300000,
            artworkUri = null,
            sourceType = SourceType.LOCAL.prefix,
            uri = "content://media/external/audio/media/10"
        )
        dao.insertTracks(listOf(track))

        // Search query with Arabic Yeh (\u064A)
        val query = "شجريان"
        val normalized = SearchUtils.normalize(query)

        val results = dao.searchTracks(query, normalized).first()
        assertEquals(1, results.size)
        assertEquals("شجریان تصنیف", results[0].title)
    }
}
