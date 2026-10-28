package com.yasinjahantigh.logarhythm.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTest {
    @Test fun keepsCurrentIndexAndOtherOrder() {
        assertEquals(listOf("a", "c", "b", "d"), pinQueueItem(listOf("a", "b", "c", "d"), "b", 2))
    }
    @Test fun retainsDuplicateOccurrences() {
        assertEquals(listOf("a", "b", "a"), pinQueueItem(listOf("a", "a", "b"), "a", 2))
    }
    @Test fun handlesSingleItem() {
        assertEquals(listOf("a"), pinQueueItem(listOf("a"), "a", 0))
    }
}
