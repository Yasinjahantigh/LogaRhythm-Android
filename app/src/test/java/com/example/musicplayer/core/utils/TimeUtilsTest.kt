package com.example.musicplayer.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun formatDuration_zeroOrNegative_returnsZeroFormat() {
        assertEquals("0:00", TimeUtils.formatDuration(0))
        assertEquals("0:00", TimeUtils.formatDuration(-500))
    }

    @Test
    fun formatDuration_underOneMinute_returnsSecondsFormat() {
        assertEquals("0:45", TimeUtils.formatDuration(45_000))
    }

    @Test
    fun formatDuration_severalMinutes_returnsMinuteSecondsFormat() {
        assertEquals("3:45", TimeUtils.formatDuration(225_000))
        assertEquals("12:05", TimeUtils.formatDuration(725_000))
    }

    @Test
    fun formatDuration_overOneHour_returnsHourMinuteSecondsFormat() {
        assertEquals("1:01:05", TimeUtils.formatDuration(3665_000))
    }

    @Test
    fun formatListeningTimeSummary_handlesSecondsMinutesHours() {
        assertEquals("0m", TimeUtils.formatListeningTimeSummary(0))
        assertEquals("30s", TimeUtils.formatListeningTimeSummary(30_000))
        assertEquals("45m", TimeUtils.formatListeningTimeSummary(45 * 60 * 1000L))
        assertEquals("2h 15m", TimeUtils.formatListeningTimeSummary((2 * 3600 + 15 * 60) * 1000L))
    }
}
