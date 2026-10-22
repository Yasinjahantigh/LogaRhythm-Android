package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadTitleParserTest {

    @Test
    fun splitsArtistAndRemovesOfficialVideoTag() {
        val (artist, title) = DownloadTitleParser.parse(
            "Daft Punk - Get Lucky (Official Video)",
            "DaftPunkVEVO"
        )
        assertEquals("Daft Punk", artist)
        assertEquals("Get Lucky", title)
    }

    @Test
    fun bracketOnlyTitleKeepsUploaderAsArtist() {
        val (artist, title) = DownloadTitleParser.parse(
            "Get Lucky [Official Music Video]",
            "Daft Punk - Topic"
        )
        assertEquals("Daft Punk", artist)
        assertEquals("Get Lucky", title)
    }

    @Test
    fun enDashSeparatorIsSupported() {
        val (artist, title) = DownloadTitleParser.parse("Artist – Song", null)
        assertEquals("Artist", artist)
        assertEquals("Song", title)
    }

    @Test
    fun emptyArtistFallsBackToSeed() {
        val (artist, _) = DownloadTitleParser.parse("Song name", "Some Channel", "Seed Artist")
        assertEquals("Seed Artist", artist)
    }

    @Test
    fun sanitizeFileNameRemovesUnsafeCharacters() {
        val sanitized = DownloadTitleParser.sanitizeFileName("A/B:C*D?\"E\"<F>|G")
        assertFalse(sanitized.contains('/'))
        assertFalse(sanitized.contains(':'))
        assertFalse(sanitized.contains('*'))
        assertFalse(sanitized.contains('?'))
        assertFalse(sanitized.contains('"'))
        assertFalse(sanitized.contains('<'))
        assertFalse(sanitized.contains('>'))
        assertFalse(sanitized.contains('|'))
    }
}
