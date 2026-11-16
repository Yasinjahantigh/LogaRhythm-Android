package com.yasinjahantigh.logarhythm.domain.model

import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsReportTest {

    @Test
    fun timeRangeComputesYearlyAndCustomBoundsAccurately() {
        val now = 1_750_000_000_000L
        assertEquals(now - 365L * 24 * 60 * 60 * 1000L, TimeRange.THIS_YEAR.toSinceTimestamp(now))
        val custom = CustomDateRange(startMs = 1_000_000L, endMs = 2_000_000L)
        assertEquals(1_000_000L, TimeRange.CUSTOM.toSinceTimestamp(now, custom))
        assertEquals(2_000_000L, TimeRange.CUSTOM.toUntilTimestamp(custom))
        assertEquals(Long.MAX_VALUE, TimeRange.THIS_WEEK.toUntilTimestamp(custom))
    }

    @Test
    fun lrcParserRoundTripsEditedTimestampedLines() {
        val edited = listOf(
            LyricLine(timestampMs = 5_250L, text = "First line"),
            LyricLine(timestampMs = 62_500L, text = "Second line")
        )
        val serialized = LrcParser.formatLines(edited)
        val reparsed = LrcParser.parse(serialized)
        assertTrue(reparsed.isSynced)
        assertEquals(2, reparsed.lines.size)
        assertEquals(5_250L, reparsed.lines[0].timestampMs)
        assertEquals("First line", reparsed.lines[0].text)
        assertEquals(62_500L, reparsed.lines[1].timestampMs)
    }
}
