package com.yasinjahantigh.logarhythm.domain.lyrics

import com.yasinjahantigh.logarhythm.domain.model.LyricLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMismatchTest {
    private fun lines(vararg ms: Long) = ms.map { LyricLine(it, "x") }

    @Test fun flagsLyricsLongerThanSong() {
        assertTrue(LyricsMismatch.isLikely(lines(1000, 100_000, 400_000), 200_000))
    }
    @Test fun acceptsNormalLyrics() {
        assertFalse(LyricsMismatch.isLikely(lines(10_000, 60_000, 190_000), 200_000))
    }
    @Test fun flagsLyricsThatEndTooEarly() {
        assertTrue(LyricsMismatch.isLikely(lines(1000, 2000, 3000, 4000, 5000, 6000), 240_000))
    }
    @Test fun shortListsAreNotJudged() {
        assertFalse(LyricsMismatch.isLikely(lines(1000, 900_000), 200_000))
    }
}
