package com.yasinjahantigh.logarhythm.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEditsTest {
    @Test fun findsDuplicateOccurrence() {
        assertEquals(2, originalOccurrence(listOf("a", "b", "a"), listOf("a", "a", "b"), 1))
    }
    @Test fun insertsAfterVisibleAnchorInOriginalOrder() {
        assertEquals(listOf("a", "b", "x", "c"), insertOriginal(listOf("a", "b", "c"), listOf("c", "b", "a"), 2, "x"))
    }
    @Test fun insertsBeforeFirstVisibleItem() {
        assertEquals(listOf("a", "x", "b"), insertOriginal(listOf("a", "b"), listOf("b", "a"), 0, "x"))
    }
    @Test fun insertsIntoEmptyQueue() {
        assertEquals(listOf("x"), insertOriginal(emptyList(), emptyList(), 0, "x"))
    }
}
