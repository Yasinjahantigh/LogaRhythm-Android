package com.example.musicplayer.domain.model

/** One word of an enhanced-LRC line, with the time it starts being sung. */
data class LyricWord(val startMs: Long, val text: String)

/**
 * Single line of synced lyrics with its playback timestamp. [words] is empty unless the source
 * carried word-level timing (enhanced LRC `<mm:ss.xx>word`).
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)
