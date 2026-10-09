package com.example.musicplayer.domain.discovery

import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.taste.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 100 * day

    private fun track(i: Int) = Track(
        id = "local:$i", title = "T$i", artist = "A$i", album = "Al$i", durationMs = 200_000L,
        artworkUri = null, sourceType = SourceType.LOCAL, uri = "uri$i"
    )

    private val library = (1..12).map(::track)

    private val events: List<PlayEvent> = buildList {
        for (k in 0 until 4) {
            val base = now - (k + 1) * 2 * day + 20 * hour
            add(PlayEvent(trackId = "local:1", timestamp = base, listenedMs = 200_000))
            add(PlayEvent(trackId = "local:2", timestamp = base + 4 * 60_000, listenedMs = 200_000))
            add(PlayEvent(trackId = "local:3", timestamp = base + 8 * 60_000, listenedMs = 200_000))
        }
        repeat(3) { add(PlayEvent(trackId = "local:9", timestamp = now - it * hour, listenedMs = 4_000, skipped = true)) }
    }
    private val profile = TasteProfile.build(events, emptySet(), now, 0)

    @Test
    fun daypartsCoverTheWholeClock() {
        assertEquals(Discovery.Daypart.MORNING, Discovery.daypartOf(5))
        assertEquals(Discovery.Daypart.AFTERNOON, Discovery.daypartOf(12))
        assertEquals(Discovery.Daypart.EVENING, Discovery.daypartOf(18))
        assertEquals(Discovery.Daypart.NIGHT, Discovery.daypartOf(23))
        assertEquals(Discovery.Daypart.NIGHT, Discovery.daypartOf(4))
    }

    @Test
    fun rightNowLeadsWithHabitsThenTopsUpWithMood() {
        val result = Discovery.rightNow(library, profile, hour = 20, limit = 8)
        assertEquals(setOf("local:1", "local:2", "local:3"), result.tracks.take(3).map { it.id }.toSet())
        assertEquals(3, result.habitualCount)
        assertEquals(8, result.tracks.size)
        assertEquals(result.tracks.size, result.tracks.map { it.id }.distinct().size)
        assertTrue(result.tracks.none { it.id == "local:9" })
    }

    @Test
    fun rightNowStillWorksForANewListener() {
        val result = Discovery.rightNow(library, TasteProfile.EMPTY, hour = 3, limit = 6)
        assertEquals(6, result.tracks.size)
        assertEquals(0, result.habitualCount)
        assertEquals(Discovery.Daypart.NIGHT, result.daypart)
        assertTrue(Discovery.rightNow(emptyList(), profile, 3).tracks.isEmpty())
    }

    @Test
    fun onRepeatNeedsRecentRepeatedCompletedPlays() {
        assertEquals(setOf("local:1", "local:2", "local:3"), Discovery.onRepeat(library, events, now).map { it.id }.toSet())
        assertTrue(Discovery.onRepeat(library, events, now + 60 * day).isEmpty())
    }

    @Test
    fun becauseYouPlayedSeedsFromTheLatestLikedTrack() {
        val result = Discovery.becauseYouPlayed(library, events, profile)
        assertNotNull(result)
        assertEquals("local:3", result!!.seed.id)
        assertTrue(result.tracks.size >= 3)
        assertTrue(result.tracks.none { it.id == result.seed.id })
        assertNull(Discovery.becauseYouPlayed(library.take(2), events, profile))
    }
}
