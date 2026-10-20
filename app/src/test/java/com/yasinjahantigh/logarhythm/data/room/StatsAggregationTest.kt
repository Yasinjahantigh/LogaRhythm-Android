package com.yasinjahantigh.logarhythm.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class StatsAggregationTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: MusicDao

    private val track1 = TrackEntity(
        id = "local:1",
        title = "Yellow",
        artist = "Coldplay",
        album = "Parachutes",
        durationMs = 269000,
        artworkUri = null,
        sourceType = SourceType.LOCAL.prefix,
        uri = "content://media/1"
    )

    private val track2 = TrackEntity(
        id = "local:2",
        title = "Fix You",
        artist = "Coldplay",
        album = "X&Y",
        durationMs = 295000,
        artworkUri = null,
        sourceType = SourceType.LOCAL.prefix,
        uri = "content://media/2"
    )

    private val track3 = TrackEntity(
        id = "local:3",
        title = "Wonderwall",
        artist = "Oasis",
        album = "Morning Glory",
        durationMs = 258000,
        artworkUri = null,
        sourceType = SourceType.LOCAL.prefix,
        uri = "content://media/3"
    )

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.musicDao()
        dao.insertTracks(listOf(track1, track2, track3))
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun totalListeningTime_sumsDurationsAndRespectsTimeCutoff() = runBlocking {
        val now = 1_700_000_000_000L
        val weekAgo = now - (7L * 24 * 3600 * 1000L)
        val monthAgo = now - (30L * 24 * 3600 * 1000L)

        // Event from 2 days ago (30,000 ms)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = now - 2L * 86400 * 1000L, listenedMs = 30_000L))
        // Event from 10 days ago (45,000 ms)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:2", timestamp = now - 10L * 86400 * 1000L, listenedMs = 45_000L))
        // Event from 40 days ago (60,000 ms)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:3", timestamp = now - 40L * 86400 * 1000L, listenedMs = 60_000L))

        // All time: 30,000 + 45,000 + 60,000 = 135,000 ms
        val allTime = dao.getTotalListenTimeMs(0L).first()
        assertEquals(135_000L, allTime)

        // This month (>= monthAgo): 30,000 + 45,000 = 75,000 ms
        val thisMonth = dao.getTotalListenTimeMs(monthAgo).first()
        assertEquals(75_000L, thisMonth)

        // This week (>= weekAgo): 30,000 ms
        val thisWeek = dao.getTotalListenTimeMs(weekAgo).first()
        assertEquals(30_000L, thisWeek)
    }

    @Test
    fun topTracks_orderedByPlayCountAndListeningTime() = runBlocking {
        // Track 1 played 3 times (90s)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 1000L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 2000L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 3000L, listenedMs = 30_000L))

        // Track 3 played 2 times (60s)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:3", timestamp = 4000L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:3", timestamp = 5000L, listenedMs = 30_000L))

        // Track 2 played 1 time (40s)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:2", timestamp = 6000L, listenedMs = 40_000L))

        val topTracks = dao.getTopTracks(0L).first()
        assertEquals(3, topTracks.size)
        assertEquals("local:1", topTracks[0].id)
        assertEquals(3, topTracks[0].playCount)
        assertEquals(90_000L, topTracks[0].totalListenedMs)

        assertEquals("local:3", topTracks[1].id)
        assertEquals(2, topTracks[1].playCount)

        assertEquals("local:2", topTracks[2].id)
        assertEquals(1, topTracks[2].playCount)
    }

    @Test
    fun topArtistsAndTopAlbums_aggregateAccurately() = runBlocking {
        // Coldplay tracks (local:1 and local:2)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 1000L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 2000L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:2", timestamp = 3000L, listenedMs = 30_000L))

        // Oasis track (local:3)
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:3", timestamp = 4000L, listenedMs = 30_000L))

        val topArtists = dao.getTopArtists(0L).first()
        assertEquals(2, topArtists.size)
        assertEquals("Coldplay", topArtists[0].artist)
        assertEquals(3, topArtists[0].playCount)
        assertEquals(90_000L, topArtists[0].totalListenedMs)
        assertEquals(2, topArtists[0].trackCount)

        assertEquals("Oasis", topArtists[1].artist)
        assertEquals(1, topArtists[1].playCount)

        val topAlbums = dao.getTopAlbums(0L).first()
        assertEquals(3, topAlbums.size)
        assertEquals("Parachutes", topAlbums[0].album)
        assertEquals(2, topAlbums[0].playCount)
    }

    @Test
    fun recentlyPlayed_ordersByMostRecentTimestampWithoutDuplicates() = runBlocking {
        // Play local:1 first
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 100L, listenedMs = 30_000L))
        // Play local:2 next
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:2", timestamp = 200L, listenedMs = 30_000L))
        // Play local:1 again most recently
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 300L, listenedMs = 30_000L))

        val recent = dao.getRecentlyPlayedTracks(10).first()
        assertEquals(2, recent.size)
        // local:1 had timestamp 300L so it should be first
        assertEquals("local:1", recent[0].id)
        // local:2 had timestamp 200L so it should be second
        assertEquals("local:2", recent[1].id)
    }

    @Test
    fun mostPlayed_ordersByPlayFrequency() = runBlocking {
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:3", timestamp = 100L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:3", timestamp = 200L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 300L, listenedMs = 30_000L))

        val mostPlayed = dao.getMostPlayedTracks(10).first()
        assertEquals(2, mostPlayed.size)
        assertEquals("local:3", mostPlayed[0].id)
        assertEquals("local:1", mostPlayed[1].id)
    }

    @Test
    fun skippedEvents_doNotCountAsPlaysButFinalListenTimeIsCorrected() = runBlocking {
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 1000L, listenedMs = 30_000L))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:1", timestamp = 2000L, listenedMs = 4_000L, skipped = true))
        dao.insertPlayEvent(PlayEventEntity(trackId = "local:2", timestamp = 3000L, listenedMs = 3_000L, skipped = true))

        val top = dao.getTopTracks(0L).first()
        assertEquals(1, top.size)
        assertEquals(1, top[0].playCount)
        assertEquals(30_000L, top[0].totalListenedMs)
        assertEquals(listOf("local:1"), dao.getMostPlayedTracks(10).first().map { it.id })
        assertEquals(listOf("local:1"), dao.getRecentlyPlayedTracks(10).first().map { it.id })

        // The scrobbler logs a play at the 30s threshold, then corrects it to the real total.
        dao.updatePlayEventListened("local:1", 1000L, 215_000L)
        assertEquals(215_000L, dao.getTopTracks(0L).first()[0].totalListenedMs)
        // A skip's timestamp must never be rewritten as if it were a play.
        dao.updatePlayEventListened("local:1", 2000L, 999_000L)
        assertEquals(215_000L + 4_000L, dao.getTotalListenTimeMs().first() - 3_000L)
    }
}
