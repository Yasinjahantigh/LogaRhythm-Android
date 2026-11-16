package com.yasinjahantigh.logarhythm.domain.model

enum class TimeRange {
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    ALL_TIME,
    CUSTOM;

    fun toSinceTimestamp(
        now: Long = System.currentTimeMillis(),
        customRange: CustomDateRange? = null
    ): Long = when (this) {
        THIS_WEEK -> now - 7L * 24 * 60 * 60 * 1000L
        THIS_MONTH -> now - 30L * 24 * 60 * 60 * 1000L
        THIS_YEAR -> now - 365L * 24 * 60 * 60 * 1000L
        ALL_TIME -> 0L
        CUSTOM -> customRange?.startMs ?: (now - 30L * 24 * 60 * 60 * 1000L)
    }

    fun toUntilTimestamp(customRange: CustomDateRange? = null): Long = when (this) {
        CUSTOM -> customRange?.endMs ?: Long.MAX_VALUE
        else -> Long.MAX_VALUE
    }
}

data class CustomDateRange(
    val startMs: Long,
    val endMs: Long
)
