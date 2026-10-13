package com.example.musicplayer.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGainTest {
    @Test fun parsesValues() {
        assertEquals(-6.54f, ReplayGain.parseDb("-6.54 dB")!!, 0.001f)
        assertEquals(3.2f, ReplayGain.parseDb("+3,2 dB")!!, 0.001f)
    }
    @Test fun onlyAttenuates() {
        assertEquals(1f, ReplayGain.volumeFor(4f), 0f)
        assertEquals(0.5012f, ReplayGain.volumeFor(-6f), 0.001f)
        assertEquals(1f, ReplayGain.volumeFor(null), 0f)
    }
    @Test fun modePicksAvailableGain() {
        assertEquals(-3f, ReplayGain.pick(ReplayGainMode.ALBUM, -3f, null))
        assertEquals(null, ReplayGain.pick(ReplayGainMode.OFF, -3f, -4f))
    }
}
