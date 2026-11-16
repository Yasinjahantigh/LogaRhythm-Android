package com.yasinjahantigh.logarhythm.domain.taste

import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteProfileTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 100 * day

    private fun sittings(): List<PlayEvent> {
        val events = mutableListOf<PlayEvent>()
        for (k in 0 until 4) {
            val base = now - (k + 1) * 2 * day + 20 * hour
            events += PlayEvent(trackId = "a", timestamp = base, listenedMs = 200_000)
            events += PlayEvent(trackId = "b", timestamp = base + 4 * 60_000, listenedMs = 200_000)
            events += PlayEvent(trackId = "c", timestamp = base + 8 * 60_000, listenedMs = 200_000)
        }
        repeat(3) { events += PlayEvent(trackId = "z", timestamp = now - it * hour, listenedMs = 4_000, skipped = true) }
        return events
    }

    @Test
    fun emptyHistoryGivesEmptyProfile() {
        assertSame(TasteProfile.EMPTY, TasteProfile.build(emptyList(), emptySet(), now, 0))
    }

    @Test
    fun playsRaiseAndSkipsLowerAffinity() {
        val p = TasteProfile.build(sittings(), setOf("fav"), now, 0)
        assertTrue(p.affinity("a") > 2.0)
        assertTrue(p.affinity("z") < -1.0)
        assertTrue(p.affinity("fav") >= 2.0)
    }

    @Test
    fun repeatedSkipsWithoutPlaysMarkATrackDisliked() {
        val p = TasteProfile.build(sittings(), emptySet(), now, 0)
        assertTrue(p.isDisliked("z"))
        assertFalse(p.isDisliked("a"))
    }

    @Test
    fun tracksHeardInTheSameSittingAreLinked() {
        val p = TasteProfile.build(sittings(), emptySet(), now, 0)
        assertTrue(p.coListenStrength("a", "b") > 0.6)
        assertEquals(0.0, p.coListenStrength("c", "a"), 0.0)
        assertEquals(0.0, p.coListenStrength("a", "unknown"), 0.0)
    }

    @Test
    fun hourWeightsFollowLocalTimeAndSmoothNeighbours() {
        val p = TasteProfile.build(sittings(), emptySet(), now, 0)
        assertTrue(p.hourWeight("a", 20) > 3.0)
        assertTrue(p.hourWeight("a", 21) > 1.0)
        assertEquals(0.0, p.hourWeight("a", 8), 0.0)

        val shifted = TasteProfile.build(sittings(), emptySet(), now, utcOffsetMs = (3.5 * hour).toInt())
        assertTrue(shifted.hourWeight("a", 23) > 1.0)
    }

    private fun testTrack(
        id: String,
        artist: String,
        genre: String,
        year: Int = 2020,
        durationMs: Long = 200_000L
    ) = Track(
        id = id,
        title = "Title $id",
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/$id",
        genre = genre,
        year = year
    )

    @Test
    fun transfersTasteAcrossArtistBilingualGenreAndEraToUnplayedTracks() {
        val likedPlayed = testTrack("t1", "Shadmehr", "پاپ", year = 2018)
        val unplayedSameArtistPop = testTrack("t2", "Shadmehr", "Pop", year = 2019)
        val unplayedSameGenre = testTrack("t3", "Another Singer", "Pop", year = 2017)
        val unplayedUnrelated = testTrack("t4", "Heavy Band", "Metal", year = 1985)
        val library = listOf(likedPlayed, unplayedSameArtistPop, unplayedSameGenre, unplayedUnrelated)

        val events = listOf(
            PlayEvent(trackId = "t1", timestamp = now - 3 * day, listenedMs = 195_000L, skipped = false),
            PlayEvent(trackId = "t1", timestamp = now - 2 * day, listenedMs = 195_000L, skipped = false)
        )
        val profile = TasteProfile.build(events, setOf("t1"), now, 0, library = library)

        assertTrue(profile.artistAffinity("Shadmehr") > 1.5)
        assertEquals(profile.genreAffinity("Pop"), profile.genreAffinity("پاپ"), 0.001)
        assertTrue(profile.tasteMatchScore(unplayedSameArtistPop) > profile.tasteMatchScore(unplayedSameGenre))
        assertTrue(profile.tasteMatchScore(unplayedSameGenre) > profile.tasteMatchScore(unplayedUnrelated))
    }

    @Test
    fun recentPlayPenaltyDecaysOverFortyEightHours() {
        val t = testTrack("r1", "Artist", "Pop")
        val events = listOf(PlayEvent(trackId = "r1", timestamp = now - 1 * hour, listenedMs = 180_000L))
        val profile = TasteProfile.build(events, emptySet(), now, 0, library = listOf(t))

        assertTrue(profile.recentPlayPenalty("r1", now) > 0.9)
        assertEquals(0.5, profile.recentPlayPenalty("r1", now + 23 * hour), 0.05)
        assertEquals(0.0, profile.recentPlayPenalty("r1", now + 48 * hour), 0.0)
    }

    @Test
    fun sessionPenaltyDampensRecentlySkippedArtistOrGenreWithinThreeHours() {
        val skipA1 = testTrack("s1", "Repeated Artist", "Rock")
        val skipA2 = testTrack("s2", "Repeated Artist", "Rock")
        val freshSameArtist = testTrack("s3", "Repeated Artist", "Rock")
        val freshOtherArtist = testTrack("s4", "Other Artist", "Jazz")
        val library = listOf(skipA1, skipA2, freshSameArtist, freshOtherArtist)

        val recentSkips = listOf(
            PlayEvent(trackId = "s1", timestamp = now - 40 * 60_000L, listenedMs = 8_000L, skipped = true),
            PlayEvent(trackId = "s2", timestamp = now - 15 * 60_000L, listenedMs = 5_000L, skipped = true)
        )
        val profile = TasteProfile.build(recentSkips, emptySet(), now, 0, library = library)
        assertTrue(profile.sessionPenalty(freshSameArtist) >= 0.7)
        assertEquals(0.0, profile.sessionPenalty(freshOtherArtist), 0.0)

        val expiredProfile = TasteProfile.build(recentSkips, emptySet(), now + 4 * hour, 0, library = library)
        assertEquals(0.0, expiredProfile.sessionPenalty(freshSameArtist), 0.0)
    }
}
