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

    @Test
    fun cleanStripsBitrateWatermarksAndTelegramHandles() {
        val cleaned = DownloadTitleParser.clean("01. Shadmehr - Taghdir [320] (128kbps) @RadioJavan [Nex1Music.ir].mp3")
        assertEquals("Shadmehr - Taghdir", cleaned)
    }

    @Test
    fun cleanStripsLeadingTrackNumberButPreservesNumberedTitles() {
        assertEquals("Artist - Song", DownloadTitleParser.clean("03. Artist - Song"))
        assertEquals("21 Guns", DownloadTitleParser.clean("21 Guns (Official Video)"))
        assertEquals("7 Years", DownloadTitleParser.clean("7 Years [HD]"))
    }

    @Test
    fun parseHandlesPersianDownloadPrefixAndArabicScript() {
        val (artist, title) = DownloadTitleParser.parse(
            "دانلود آهنگ جديد شادمهر عقيلي به نام تقدير [320] @Channel",
            uploader = null
        )
        assertEquals("شادمهر عقیلی", artist)
        assertEquals("تقدیر", title)
    }

    @Test
    fun extractFeaturedSplitsCollaborationFromTitle() {
        val (main, featured) = DownloadTitleParser.extractFeatured("Taghdir (feat. Ebi & Googoosh)")
        assertEquals("Taghdir", main)
        assertEquals(listOf("Ebi", "Googoosh"), featured)
    }
}
