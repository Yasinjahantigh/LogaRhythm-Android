package com.yasinjahantigh.logarhythm.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class AudioFingerprintTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun id3v2Header(payloadSize: Int): ByteArray {
        val header = ByteArray(10 + payloadSize)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3
        header[4] = 0
        header[5] = 0
        header[6] = ((payloadSize shr 21) and 0x7F).toByte()
        header[7] = ((payloadSize shr 14) and 0x7F).toByte()
        header[8] = ((payloadSize shr 7) and 0x7F).toByte()
        header[9] = (payloadSize and 0x7F).toByte()
        for (i in 10 until header.size) {
            header[i] = (i % 251).toByte()
        }
        return header
    }

    @Test
    fun sameAudioWithDifferentId3TagsProducesSameFingerprint() {
        val audioBody = ByteArray(180_000) { i -> ((i * 31 + 7) and 0xFF).toByte() }
        val file1 = tempFolder.newFile("copy1.mp3")
        val file2 = tempFolder.newFile("copy2.mp3")

        file1.writeBytes(audioBody)

        val out = ByteArrayOutputStream()
        out.write(id3v2Header(64_000)) // 64 KB cover art / ID3v2 tag
        out.write(audioBody)
        val id3v1 = ByteArray(128)
        id3v1[0] = 'T'.code.toByte()
        id3v1[1] = 'A'.code.toByte()
        id3v1[2] = 'G'.code.toByte()
        out.write(id3v1)
        file2.writeBytes(out.toByteArray())

        val fp1 = AudioFingerprint.computeFromFile(file1)
        val fp2 = AudioFingerprint.computeFromFile(file2)
        assertNotNull(fp1)
        assertEquals(fp1, fp2)
    }

    @Test
    fun differentAudioOfExactSameSizeProducesDifferentFingerprint() {
        val body1 = ByteArray(180_000) { i -> ((i * 13 + 3) and 0xFF).toByte() }
        val body2 = ByteArray(180_000) { i -> ((i * 29 + 11) and 0xFF).toByte() }
        val file1 = tempFolder.newFile("songA.mp3").apply { writeBytes(body1) }
        val file2 = tempFolder.newFile("songB.mp3").apply { writeBytes(body2) }

        val fp1 = AudioFingerprint.computeFromFile(file1)
        val fp2 = AudioFingerprint.computeFromFile(file2)
        assertNotNull(fp1)
        assertNotNull(fp2)
        assertNotEquals(fp1, fp2)
    }
}
