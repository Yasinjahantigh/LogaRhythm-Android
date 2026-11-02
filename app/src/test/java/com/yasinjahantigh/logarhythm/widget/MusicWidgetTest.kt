package com.yasinjahantigh.logarhythm.widget

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicWidgetTest {

    private fun createTrack(
        id: String,
        title: String,
        artist: String,
        album: String = "Test Album",
        year: Int = 2020,
        dateAdded: Long = 1000L,
        durationMs: Long = 180000L
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/audio/$id",
        year = year,
        dateAdded = dateAdded
    )

    @Test
    fun sortTracks_byTitle_ascending() {
        val tracks = listOf(
            createTrack("1", "Phantom Liberty", "Dawid Podsiadło"),
            createTrack("2", "505", "Arctic Monkeys"),
            createTrack("3", "Old Yellow Bricks", "Arctic Monkeys")
        )

        val sorted = MusicWidgetUpdater.sortTracks(tracks, "TITLE", "ASCENDING")

        assertEquals("505", sorted[0].title)
        assertEquals("Old Yellow Bricks", sorted[1].title)
        assertEquals("Phantom Liberty", sorted[2].title)
    }

    @Test
    fun sortTracks_byTitle_descending() {
        val tracks = listOf(
            createTrack("1", "505", "Arctic Monkeys"),
            createTrack("2", "Phantom Liberty", "Dawid Podsiadło"),
            createTrack("3", "Old Yellow Bricks", "Arctic Monkeys")
        )

        val sorted = MusicWidgetUpdater.sortTracks(tracks, "TITLE", "DESCENDING")

        assertEquals("Phantom Liberty", sorted[0].title)
        assertEquals("Old Yellow Bricks", sorted[1].title)
        assertEquals("505", sorted[2].title)
    }

    @Test
    fun sortTracks_byArtist_ascending() {
        val tracks = listOf(
            createTrack("1", "Phantom Liberty", "Dawid Podsiadło"),
            createTrack("2", "505", "Arctic Monkeys"),
            createTrack("3", "Farda Soraghe Man Bia", "Ali Azimi")
        )

        val sorted = MusicWidgetUpdater.sortTracks(tracks, "ARTIST", "ASCENDING")

        assertEquals("Ali Azimi", sorted[0].artist)
        assertEquals("Arctic Monkeys", sorted[1].artist)
        assertEquals("Dawid Podsiadło", sorted[2].artist)
    }

    @Test
    fun sortTracks_byYear_descending() {
        val tracks = listOf(
            createTrack("1", "Track 2010", "Artist", year = 2010),
            createTrack("2", "Track 2024", "Artist", year = 2024),
            createTrack("3", "Track 2018", "Artist", year = 2018)
        )

        val sorted = MusicWidgetUpdater.sortTracks(tracks, "YEAR", "DESCENDING")

        assertEquals(2024, sorted[0].year)
        assertEquals(2018, sorted[1].year)
        assertEquals(2010, sorted[2].year)
    }

    @Test
    fun sortTracks_byDuration_ascending() {
        val tracks = listOf(
            createTrack("1", "Long", "Artist", durationMs = 300000L),
            createTrack("2", "Short", "Artist", durationMs = 60000L),
            createTrack("3", "Medium", "Artist", durationMs = 180000L)
        )

        val sorted = MusicWidgetUpdater.sortTracks(tracks, "DURATION", "ASCENDING")

        assertEquals(60000L, sorted[0].durationMs)
        assertEquals(180000L, sorted[1].durationMs)
        assertEquals(300000L, sorted[2].durationMs)
    }

    @Test
    fun playTrackWidgetAction_parameterKey_isCorrect() {
        assertEquals("track_id", PlayTrackWidgetAction.trackIdKey.name)
    }

    @Test
    fun playQueueWidgetAction_parameterKeys_areCorrect() {
        assertEquals("queue_index", PlayQueueWidgetAction.indexKey.name)
        assertEquals("queue_track_id", PlayQueueWidgetAction.trackIdKey.name)
    }

    @Test
    fun musicWidgetSnapshot_defaults_areValid() {
        val snapshot = MusicWidgetUpdater.currentState()

        assertEquals(null, snapshot.track)
        assertFalse(snapshot.isPlaying)
        assertEquals(0L, snapshot.position)
        assertEquals(0L, snapshot.duration)
        assertFalse(snapshot.shuffle)
        assertEquals(0, snapshot.repeatMode)
        assertFalse(snapshot.favorite)
        assertTrue(snapshot.upcoming.isEmpty())
        assertNotNull(snapshot.sortedTracks)
    }
}
