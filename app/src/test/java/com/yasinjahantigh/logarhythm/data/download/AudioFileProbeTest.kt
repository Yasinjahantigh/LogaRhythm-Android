package com.yasinjahantigh.logarhythm.data.download

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AudioFileProbeTest {

    private fun file(bytes: ByteArray): File =
        File.createTempFile("probe", ".m4a").apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun rejects_tiny_partial_downloads() {
        assertFalse(AudioFileProbe.isUsable(file(ByteArray(4 * 1024))))
    }

    @Test
    fun rejects_garbage_larger_than_threshold() {
        val garbage = ByteArray(64 * 1024) { (it % 251).toByte() }
        assertFalse(AudioFileProbe.isUsable(file(garbage)))
    }
}
