package com.yasinjahantigh.logarhythm.domain.model

enum class TimeRange {
    THIS_WEEK,
    THIS_MONTH,
    ALL_TIME;

    fun toSinceTimestamp(now: Long = System.currentTimeMillis()): Long = when (this) {
        THIS_WEEK -> now - 7L * 24 * 60 * 60 * 1000L
        THIS_MONTH -> now - 30L * 24 * 60 * 60 * 1000L
        ALL_TIME -> 0L
    }
}
