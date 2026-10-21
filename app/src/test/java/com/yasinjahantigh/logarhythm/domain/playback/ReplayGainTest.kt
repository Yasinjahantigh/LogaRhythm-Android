package com.yasinjahantigh.logarhythm.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGainTest {
    @Test fun parsesValues() {
        assertEquals(-6.54f, ReplayGain.parseDb("-6.54 dB")!!, 0.001f)
        assertEquals(3.2f, ReplayGain.parseDb("+3,2 dB")!!, 0.001f)
    }
    @Test fun modePicksAvailableGain() {
        assertEquals(-3f, ReplayGain.pick(ReplayGainMode.ALBUM, -3f, null))
        assertEquals(null, ReplayGain.pick(ReplayGainMode.OFF, -3f, -4f))
    }
    @Test fun parsesR128Units() {
        // R128 is 1/256 dB: -256 -> -1 dB, +1280 -> +5 dB.
        assertEquals(-1f, ReplayGain.parseR128("-256")!!, 0.001f)
        assertEquals(5f, ReplayGain.parseR128("+1280")!!, 0.001f)
    }
    @Test fun splitAttenuatesLoudTracks() {
        // -6 dB tag -> volume ~0.5, no DSP boost.
        val (volume, boostMb) = ReplayGain.splitForOutput(-6f, 0f)
        assertEquals(0.5012f, volume, 0.001f)
        assertEquals(0, boostMb)
    }
    @Test fun splitBoostsQuietTracksWithCap() {
        // +4 dB tag -> unity volume + 400 mB DSP boost.
        val (volume, boostMb) = ReplayGain.splitForOutput(4f, 0f)
        assertEquals(1f, volume, 0f)
        assertEquals(400, boostMb)
        // Broken +20 dB tag never exceeds the +6 dB safety cap.
        val (_, cappedMb) = ReplayGain.splitForOutput(20f, 0f)
        assertEquals(600, cappedMb)
    }
    @Test fun splitAppliesPreamp() {
        // No tag + user preamp +3 dB -> unity volume + 300 mB boost.
        val (volume, boostMb) = ReplayGain.splitForOutput(null, 3f)
        assertEquals(1f, volume, 0f)
        assertEquals(300, boostMb)
    }
}
