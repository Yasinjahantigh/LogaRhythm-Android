package com.yasinjahantigh.logarhythm.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {
    @Test fun shuffledUpcomingKeepsPlayedPrefix() {
        val visible = listOf("a", "b", "c", "d", "e")
        val original = listOf("a", "b", "c", "d", "e")
        val result = shuffleUpcoming(visible, original, "c", 2, shuffle = true)
        assertEquals(listOf("a", "b", "c"), result.take(3))
        assertEquals(listOf("a", "b", "c", "d", "e"), result.sorted())
    }

    @Test fun unshuffleRestoresCanonicalOrderAfterCurrent() {
        val visible = listOf("a", "c", "d", "b", "e")
        val original = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("a", "c", "b", "d", "e"), shuffleUpcoming(visible, original, "c", 1, false))
    }

    @Test fun duplicatesKeepOneOccurrenceEach() {
        val visible = listOf("a", "a", "b")
        val original = listOf("a", "a", "b")
        assertEquals(listOf("a", "a", "b"), shuffleUpcoming(visible, original, "a", 1, false))
    }

    @Test fun currentMissingFromOriginalIsStillPlaced() {
        val visible = listOf("x", "a")
        val original = listOf("a", "b")
        assertEquals(listOf("x", "a", "b"), shuffleUpcoming(visible, original, "x", 0, false))
    }
}
