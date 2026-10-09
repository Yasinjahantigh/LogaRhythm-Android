package com.example.musicplayer.core.utils

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    /**
     * Formats duration in milliseconds to mm:ss or hh:mm:ss.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
        } else {
            String.format(Locale.US, "%d:%02d", remainingMinutes, remainingSeconds)
        }
    }

    /**
     * Formats duration into a concise human-readable string like '2h 15m' or '45m' or '30s'.
     */
    fun formatListeningTimeSummary(durationMs: Long): String {
        if (durationMs <= 0) return "0m"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
