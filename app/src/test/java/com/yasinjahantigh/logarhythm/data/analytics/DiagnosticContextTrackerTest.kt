package com.yasinjahantigh.logarhythm.data.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticContextTrackerTest {

    @Test
    fun tracksRoutePlayerAndLyricsContext() {
        AppDiagnosticContext.onRouteChanged("home")
        AppDiagnosticContext.onRouteChanged("library")
        AppDiagnosticContext.onPlayerUiChanged(expanded = true, queueOpen = false)
        AppDiagnosticContext.onLyricsOpenChanged(true)
        AppDiagnosticContext.onLyricsState(
            trackId = "local:42",
            loading = false,
            hasLyrics = true,
            isSynced = true,
            lineCount = 28,
            source = "lrclib",
            hidden = false,
            mismatch = false,
            translatedCount = 28,
            translationFailed = false
        )

        val snap = AppDiagnosticContext.snapshot()
        assertEquals("library", snap.currentRoute)
        assertTrue(snap.playerExpanded)
        assertTrue(snap.lyricsOpen)
        assertEquals("lrclib", snap.lyrics.source)
        assertEquals(28, snap.lyrics.lineCount)
        assertTrue(snap.navTrail.any { it.startsWith("lyrics:open@") })
    }
}
