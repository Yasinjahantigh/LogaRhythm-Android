package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.data.download.Mp4Remuxer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Mp4RemuxerTest {

    private fun box(type: String, payload: ByteArray = ByteArray(0)): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(
            (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()
        ) + type.toByteArray(Charsets.ISO_8859_1) + payload
    }

    private fun file(bytes: ByteArray): File =
        File.createTempFile("remux", ".m4a").apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun detects_fragmented_and_progressive_mp4() {
        val remuxer = Mp4Remuxer()
        val ftyp = box("ftyp", ByteArray(8))
        val fragmented = file(ftyp + box("moov") + box("moof") + box("mdat", ByteArray(64)))
        val progressive = file(ftyp + box("moov") + box("mdat", ByteArray(64)))
        val truncated = file(ftyp + byteArrayOf(0, 0, 0, 2, 'm'.code.toByte()))
        assertTrue(remuxer.isFragmented(fragmented))
        assertFalse(remuxer.isFragmented(progressive))
        assertFalse(remuxer.isFragmented(truncated))
    }
}
