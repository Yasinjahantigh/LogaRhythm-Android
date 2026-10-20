package com.yasinjahantigh.logarhythm.domain.session

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSessionPlannerTest {
    private fun track(id: String, artist: String = "Artist $id", duration: Long = 180_000L) = Track(
        id = id,
        title = "Track $id",
        artist = artist,
        album = "Album",
        durationMs = duration,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://music/$id"
    )

    @Test
    fun fitsTargetWithoutExceedingAndDoesNotDuplicateTracks() {
        val input = (1..20).map { track("$it", duration = 180_000L) } + track("1", duration = 180_000L)
        val result = ListeningSessionPlanner.buildSession(input, 15 * 60_000L, seed = 17L)

        assertEquals(5, result.size)
        assertTrue(result.sumOf { it.durationMs } <= 15 * 60_000L)
        assertEquals(result.size, result.map { it.id }.distinct().size)
    }

    @Test
    fun rejectsInvalidTargetsAndUnplayableTracks() {
        assertTrue(ListeningSessionPlanner.buildSession(listOf(track("1")), 0L).isEmpty())
        assertTrue(ListeningSessionPlanner.buildSession(listOf(track("bad", duration = 0L)), 15 * 60_000L).isEmpty())
        assertTrue(ListeningSessionPlanner.buildSession(listOf(track("bad-uri").copy(uri = "")), 15 * 60_000L).isEmpty())
    }

    @Test
    fun prefersCurrentMixTracksWhenDurationsAreEquivalent() {
        val tracks = (1..8).map { track("$it", duration = 180_000L) }
        val result = ListeningSessionPlanner.buildSession(
            tracks = tracks,
            targetDurationMs = 180_000L,
            preferredTrackIds = setOf("3", "7"),
            seed = 12L
        )

        assertEquals(1, result.size)
        assertTrue(result.single().id in setOf("3", "7"))
    }

    @Test
    fun returnsOnlyTracksThatFitAvailableTime() {
        val input = listOf(track("long", duration = 900_000L), track("short", duration = 120_000L))
        val result = ListeningSessionPlanner.buildSession(input, 5 * 60_000L, seed = 2L)

        assertEquals(listOf("short"), result.map { it.id })
        assertTrue(result.sumOf { it.durationMs } <= 5 * 60_000L)
    }
}
