package com.yasinjahantigh.logarhythm.domain.lyrics

/** Result of one provider probe, shown in Settings → Lyrics sources. */
enum class LyricsSourceStatus { OK, EMPTY, BLOCKED, TIMEOUT, ERROR }

data class LyricsSourceTestResult(
    val id: String,
    val displayName: String,
    val status: LyricsSourceStatus,
    val latencyMs: Long,
    val candidateCount: Int,
    val detail: String? = null
)
