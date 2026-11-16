package com.yasinjahantigh.logarhythm.data.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseRadarServiceTest {

    @Test
    fun normalizeArtistKeyUnifiesArabicPersianLettersAndWhitespace() {
        assertEquals(
            ReleaseRadarService.normalizeArtistKey("  Shadmehr Aghili "),
            ReleaseRadarService.normalizeArtistKey("shadmehr   aghili")
        )
        assertEquals(
            ReleaseRadarService.normalizeArtistKey("محسن چاوشي"),
            ReleaseRadarService.normalizeArtistKey("محسن   چاوشی")
        )
        assertTrue(ReleaseRadarService.isArtistMatch("Mohsen Chavoshi", "mohsen chavoshi"))
        assertFalse(ReleaseRadarService.isArtistMatch("Reza Bahram", "Shadmehr Aghili"))
    }

    @Test
    fun diffNewCandidatesExcludesBaselineIdsAndDeduplicatesCrossSource() {
        val known = setOf("mm:track:101", "it_trk:202")
        val fetched = ReleaseRadarService.deduplicateCandidates(
            listOf(
                RadarCandidate("mm:track:101", "Old Song", "Single", "Mohsen Yeganeh", null, "musicman", "mm://track/101"),
                RadarCandidate("mm:track:999", "Fresh Track", "Album A", "Mohsen Yeganeh", null, "musicman", "mm://track/999"),
                RadarCandidate("it_trk:888", "Fresh Track", "Album A", "Mohsen Yeganeh", null, "itunes", "https://itunes.apple.com/1"),
                RadarCandidate("it_alb:777", "Brand New EP", "Brand New EP", "Mohsen Yeganeh", null, "itunes", "https://itunes.apple.com/2")
            )
        )

        val diff = ReleaseRadarService.diffNewCandidates(known, fetched)
        assertEquals(listOf("mm:track:999", "it_alb:777"), diff.map { it.releaseId })
    }
}
