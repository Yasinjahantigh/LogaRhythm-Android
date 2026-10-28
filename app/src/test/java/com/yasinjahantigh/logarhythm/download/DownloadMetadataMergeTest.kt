package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.core.utils.AudioFormatInfo
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadMetadataMergeTest {

    @Test
    fun keepsSourceArtistWhenProvidersPointAtAnotherArtist() {
        val artist = DownloadMetadataMerge.preferSourceArtist(
            "Mehyad x Lenna",
            "TNT RAP تی ان تی رپ",
            null
        )
        assertEquals("Mehyad x Lenna", artist)
    }

    @Test
    fun adoptsProviderArtistWhenItMatchesTheSource() {
        val artist = DownloadMetadataMerge.preferSourceArtist(
            "Mehyad x Lenna",
            "TNT RAP تی ان تی رپ",
            "Mehyad and Lenna"
        )
        assertEquals("Mehyad and Lenna", artist)
    }

    @Test
    fun usesProviderArtistWhenSourceIsBlank() {
        val artist = DownloadMetadataMerge.preferSourceArtist("", "Real Artist")
        assertEquals("Real Artist", artist)
    }

    @Test
    fun keepsFeaturedArtistWhenProviderOnlyKnowsTheLead() {
        val artist = DownloadMetadataMerge.preferSourceArtist(
            "The Weeknd feat. Daft Punk",
            "The Weeknd"
        )
        assertEquals("The Weeknd feat. Daft Punk", artist)
    }

    @Test
    fun acceptsProviderThatKeepsEveryCollaborator() {
        val artist = DownloadMetadataMerge.preferSourceArtist(
            "The Weeknd feat. Daft Punk",
            "The Weeknd ft. Daft Punk"
        )
        assertEquals("The Weeknd ft. Daft Punk", artist)
    }

    @Test
    fun losslessLabelSkipsBitrate() {
        val info = AudioFormatInfo(
            container = "FLAC",
            codec = "FLAC",
            bitrateKbps = 900,
            bitrateEstimated = false,
            sampleRateHz = 44_100,
            lossless = true
        )
        assertEquals("FLAC · Lossless · 44.1 kHz", info.label("Lossless"))
    }

    @Test
    fun lossyLabelShowsEstimatedBitrate() {
        val info = AudioFormatInfo(
            container = "M4A",
            codec = "AAC",
            bitrateKbps = 128,
            bitrateEstimated = true,
            sampleRateHz = 44_100,
            lossless = false
        )
        assertEquals("M4A · AAC · ~128 kbps · 44.1 kHz", info.label("Lossless"))
    }
}
