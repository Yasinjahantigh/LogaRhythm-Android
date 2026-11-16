package com.yasinjahantigh.logarhythm.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AudioTrimmerTest {

    @Test
    fun sliceMp3StreamSelectsExactFrameRangeAndSkipsId3Header() {
        // MPEG-1 Layer III, 128 kbps (index 9), 44100 Hz (index 0), no padding => 417 bytes/frame (~26.122ms)
        val frameSize = 417
        val singleFrame = ByteArray(frameSize).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xFB.toByte() // MPEG1, Layer III, no CRC
            this[2] = 0x90.toByte() // 128 kbps, 44100 Hz, padding=0
            this[3] = 0x00.toByte()
        }

        // Build 10s (383 frames) prefixed by a 16-byte ID3v2 header
        val streamBytes = ByteArrayOutputStream().apply {
            write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, 6))
            write(ByteArray(6))
            repeat(80) { write(singleFrame) }
        }.toByteArray()

        val sliced = ByteArrayOutputStream()
        val ok = AudioTrimmerEngine.sliceMp3Stream(
            input = ByteArrayInputStream(streamBytes),
            output = sliced,
            startMs = 500L,
            endMs = 1500L
        )
        assertTrue(ok)
        val written = sliced.toByteArray()
        assertEquals(0, written.size % frameSize)
        val frames = written.size / frameSize
        // 1000ms window at ~26.12ms/frame is roughly 38-39 frames
        assertTrue(frames in 35..42)

        val empty = ByteArrayOutputStream()
        assertFalse(
            AudioTrimmerEngine.sliceMp3Stream(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                output = empty,
                startMs = 0L,
                endMs = 1000L
            )
        )
    }
}
