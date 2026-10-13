package com.example.musicplayer.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenPolicyTest {
    @Test
    fun thresholdIsThirtySecondsForNormalTracksAndHalfForShortOnes() {
        assertEquals(30_000L, ListenPolicy.playThresholdMs(240_000L))
        assertEquals(10_000L, ListenPolicy.playThresholdMs(20_000L))
        assertEquals(30_000L, ListenPolicy.playThresholdMs(0L))
    }

    @Test
    fun classifiesPlaysSkipsAndNoise() {
        assertEquals(ListenPolicy.Outcome.PLAY, ListenPolicy.classify(31_000L, 240_000L))
        assertEquals(ListenPolicy.Outcome.SKIP, ListenPolicy.classify(5_000L, 240_000L))
        assertEquals(ListenPolicy.Outcome.IGNORE, ListenPolicy.classify(800L, 240_000L))
    }
}
