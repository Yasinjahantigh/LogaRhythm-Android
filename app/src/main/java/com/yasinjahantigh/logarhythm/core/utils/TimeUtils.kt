package com.yasinjahantigh.logarhythm.core.utils

object TimeUtils {
    /**
     * Formats duration in milliseconds to mm:ss or hh:mm:ss without Formatter/Regex overhead.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val seconds = (totalSeconds % 60L).toInt()
        val totalMinutes = totalSeconds / 60L
        val hours = totalMinutes / 60L
        val minutes = (totalMinutes % 60L).toInt()

        return if (hours > 0L) {
            buildString(8) {
                append(hours)
                append(':')
                if (minutes < 10) append('0')
                append(minutes)
                append(':')
                if (seconds < 10) append('0')
                append(seconds)
            }
        } else {
            buildString(5) {
                append(minutes)
                append(':')
                if (seconds < 10) append('0')
                append(seconds)
            }
        }
    }

    /**
     * Formats duration into a concise human-readable string like '2h 15m' or '45m' or '30s'.
     */
    fun formatListeningTimeSummary(durationMs: Long): String {
        if (durationMs <= 0L) return "0m"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
