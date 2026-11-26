package com.yasinjahantigh.logarhythm.data.analytics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight, zero-overhead passive tracker of the user's current screen, player/lyrics
 * visibility, and recent navigation trail so bug reports always capture what the user was
 * looking at when they shook their phone — even when no errors were logged.
 */
object AppDiagnosticContext {

    data class LyricsUiSnapshot(
        val trackId: String? = null,
        val loading: Boolean = false,
        val hasLyrics: Boolean = false,
        val isSynced: Boolean = false,
        val lineCount: Int = 0,
        val source: String? = null,
        val hidden: Boolean = false,
        val mismatch: Boolean = false,
        val translatedCount: Int = 0,
        val translationFailed: Boolean = false
    )

    data class Snapshot(
        val currentRoute: String = "unknown",
        val playerExpanded: Boolean = false,
        val queueOpen: Boolean = false,
        val lyricsOpen: Boolean = false,
        val navTrail: List<String> = emptyList(),
        val lyrics: LyricsUiSnapshot = LyricsUiSnapshot()
    )

    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var currentRoute: String = "unknown"
    private var playerExpanded: Boolean = false
    private var queueOpen: Boolean = false
    private var lyricsOpen: Boolean = false
    private val navTrail = ArrayDeque<String>(MAX_TRAIL)
    private var lyricsSnapshot = LyricsUiSnapshot()

    fun onRouteChanged(route: String?) {
        val clean = route?.substringBefore('?')?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: return
        synchronized(lock) {
            if (currentRoute != clean) {
                currentRoute = clean
                pushTrailLocked(clean)
            }
        }
    }

    fun onPlayerUiChanged(expanded: Boolean, queueOpen: Boolean) {
        synchronized(lock) {
            val changed = (playerExpanded != expanded) || (this.queueOpen != queueOpen)
            playerExpanded = expanded
            this.queueOpen = queueOpen
            if (changed) {
                val label = when {
                    expanded && queueOpen -> "player:queue"
                    expanded && lyricsOpen -> "player:lyrics"
                    expanded -> "player:open"
                    else -> "player:mini"
                }
                pushTrailLocked(label)
            }
        }
    }

    fun onLyricsOpenChanged(open: Boolean) {
        synchronized(lock) {
            if (lyricsOpen != open) {
                lyricsOpen = open
                pushTrailLocked(if (open) "lyrics:open" else "lyrics:closed")
            }
        }
    }

    fun onLyricsState(
        trackId: String?,
        loading: Boolean,
        hasLyrics: Boolean,
        isSynced: Boolean,
        lineCount: Int,
        source: String?,
        hidden: Boolean,
        mismatch: Boolean,
        translatedCount: Int,
        translationFailed: Boolean
    ) {
        synchronized(lock) {
            lyricsSnapshot = LyricsUiSnapshot(
                trackId = trackId,
                loading = loading,
                hasLyrics = hasLyrics,
                isSynced = isSynced,
                lineCount = lineCount,
                source = source,
                hidden = hidden,
                mismatch = mismatch,
                translatedCount = translatedCount,
                translationFailed = translationFailed
            )
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            currentRoute = currentRoute,
            playerExpanded = playerExpanded,
            queueOpen = queueOpen,
            lyricsOpen = lyricsOpen,
            navTrail = navTrail.toList(),
            lyrics = lyricsSnapshot
        )
    }

    private fun pushTrailLocked(event: String) {
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        if (navTrail.size >= MAX_TRAIL) navTrail.removeFirst()
        navTrail.addLast("$event@$stamp")
    }

    private const val MAX_TRAIL = 12
}
