package com.yasinjahantigh.logarhythm.feature.player

import org.junit.Assert.*
import org.junit.Test

class CoverPeekTest {
    @Test fun previewsWithoutCommittingBelowThreshold() {
        assertEquals(2, coverPeekTarget(-30f, 300f, 1, 3, false))
        assertNull(coverPeekTarget(-30f, 300f, 1, 3, true))
    }
    @Test fun commitsOnlyOnReleaseBeyondThreshold() {
        assertEquals(2, coverPeekTarget(-105f, 300f, 1, 3, true))
        assertEquals(0, coverPeekTarget(105f, 300f, 1, 3, true))
    }
    @Test fun swipingLeftAlwaysAdvances() {
        assertEquals(2, coverPeekTarget(-120f, 300f, 1, 3, true))
        assertEquals(0, coverPeekTarget(120f, 300f, 1, 3, true))
    }
    @Test fun neverWrapsAtQueueEnds() {
        assertNull(coverPeekTarget(-120f, 300f, 2, 3, true))
        assertNull(coverPeekTarget(120f, 300f, 0, 3, false))
        assertNull(coverPeekTarget(-120f, 300f, -1, 0, true))
        assertNull(coverPeekTarget(-120f, 0f, 1, 3, true))
    }
}
