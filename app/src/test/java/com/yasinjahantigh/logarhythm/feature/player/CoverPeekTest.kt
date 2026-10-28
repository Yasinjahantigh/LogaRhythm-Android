package com.yasinjahantigh.logarhythm.feature.player

import org.junit.Assert.*
import org.junit.Test

class CoverPeekTest {
    @Test fun previewsWithoutCommittingBelowThreshold() {
        assertEquals(2, coverPeekTarget(30f, 300f, 1, 3, true, false, false))
        assertNull(coverPeekTarget(30f, 300f, 1, 3, true, false, true))
    }
    @Test fun commitsOnlyOnReleaseBeyondThreshold() {
        assertEquals(2, coverPeekTarget(105f, 300f, 1, 3, true, false, true))
        assertEquals(0, coverPeekTarget(-105f, 300f, 1, 3, true, false, true))
    }
    @Test fun reversesWithPreferenceAndLayoutDirection() {
        assertEquals(0, coverPeekTarget(120f, 300f, 1, 3, false, false, true))
        assertEquals(0, coverPeekTarget(120f, 300f, 1, 3, true, true, true))
        assertEquals(2, coverPeekTarget(120f, 300f, 1, 3, false, true, true))
    }
    @Test fun neverWrapsAtQueueEnds() {
        assertNull(coverPeekTarget(120f, 300f, 2, 3, true, false, true))
        assertNull(coverPeekTarget(-120f, 300f, 0, 3, true, false, false))
        assertNull(coverPeekTarget(120f, 300f, -1, 0, true, false, true))
        assertNull(coverPeekTarget(120f, 0f, 1, 3, true, false, true))
    }
}
