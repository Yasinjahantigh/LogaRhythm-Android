package com.yasinjahantigh.logarhythm.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedTest {
    @Test fun preservesPresets() {
        listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach {
            assertEquals(it, roundedPlaybackSpeed(it), 0f)
        }
    }
    @Test fun roundsAndClampsSliderValues() {
        assertEquals(1.05f, roundedPlaybackSpeed(1.037f), 0f)
        assertEquals(.5f, roundedPlaybackSpeed(.1f), 0f)
        assertEquals(2f, roundedPlaybackSpeed(3f), 0f)
    }
}
