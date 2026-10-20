package com.yasinjahantigh.logarhythm.domain.model

/**
 * Domain model representing lyrics for an audio track.
 * Supports both timestamped synced lyrics and plain unsynced text.
 */
data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val plainText: String? = null,
    val isSynced: Boolean = lines.isNotEmpty(),
    val offsetMs: Long = 0L,
    val source: String? = null
) {
    val isEmpty: Boolean
        get() = lines.isEmpty() && plainText.isNullOrBlank()
}
