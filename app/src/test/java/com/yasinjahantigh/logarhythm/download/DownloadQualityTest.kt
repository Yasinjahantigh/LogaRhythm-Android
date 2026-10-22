package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQualityTest {

    @Test
    fun knownBitrateMapsToYtDlpArgument() {
        assertEquals(DownloadQuality.Q128, DownloadQuality.fromPref("128"))
        assertEquals("128K", DownloadQuality.Q128.ytdlpValue)
        assertEquals(DownloadQuality.Q320, DownloadQuality.fromPref("320"))
        assertEquals("320K", DownloadQuality.Q320.ytdlpValue)
    }

    @Test
    fun defaultIsBestVbr() {
        assertEquals(DownloadQuality.BEST, DownloadQuality.fromPref(null))
        assertEquals("0", DownloadQuality.BEST.ytdlpValue)
    }

    @Test
    fun unknownValueFallsBackToBest() {
        assertEquals(DownloadQuality.BEST, DownloadQuality.fromPref("999"))
    }
}
