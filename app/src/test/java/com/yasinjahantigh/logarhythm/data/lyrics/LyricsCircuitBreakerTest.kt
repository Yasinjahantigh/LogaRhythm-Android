package com.yasinjahantigh.logarhythm.data.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsCircuitBreakerTest {

    @Test
    fun opensAfterThreeFailuresAndResetsAfterSuccess() {
        val breaker = LyricsCircuitBreaker()
        val now = 1_000_000L
        breaker.recordFailure("genius", now)
        breaker.recordFailure("genius", now)
        assertFalse(breaker.isOpen("genius", now))
        breaker.recordFailure("genius", now)
        assertTrue(breaker.isOpen("genius", now + 1_000L))
        assertFalse(breaker.isOpen("genius", now + LyricsCircuitBreaker.OPEN_MS + 1L))
        breaker.recordSuccess("genius")
        assertFalse(breaker.isOpen("genius", now))
    }

    @Test
    fun providersAreIndependent() {
        val breaker = LyricsCircuitBreaker()
        repeat(3) { breaker.recordFailure("kugou", 0L) }
        assertTrue(breaker.isOpen("kugou", 1L))
        assertFalse(breaker.isOpen("lrclib", 1L))
    }
}
