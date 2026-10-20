package com.yasinjahantigh.logarhythm.domain.taste

import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
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
}
