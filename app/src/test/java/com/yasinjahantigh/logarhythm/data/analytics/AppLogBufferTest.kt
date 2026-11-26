package com.yasinjahantigh.logarhythm.data.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLogBufferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sanitizesSecretsAndMediaPaths() {
        val buffer = AppLogBuffer()
        buffer.log(
            tag = "Net",
            message = "Authorization: Bearer secret_token_12345 failed on /storage/emulated/0/Music/song.mp3",
            level = AppLogBuffer.Level.WARN
        )
        val snap = buffer.snapshot()
        assertFalse(snap.contains("secret_token_12345"))
        assertFalse(snap.contains("/storage/emulated/0/Music/song.mp3"))
        assertTrue(snap.contains("[REDACTED]"))
        assertTrue(snap.contains("/storage/[MEDIA_PATH]"))
        assertTrue(snap.contains("W/Net"))
    }

    @Test
    fun snapshotTailPreservesNewestErrorLines() {
        val buffer = AppLogBuffer()
        repeat(40) { idx ->
            buffer.log("Sync", "Old step $idx completed with extra diagnostic details padding text")
        }
        buffer.log("Player", "Fatal playback error occurred right now", AppLogBuffer.Level.ERROR)

        val tail = buffer.snapshotTail(maxChars = 350)
        assertTrue(tail.contains("E/Player"))
        assertTrue(tail.contains("Fatal playback error occurred right now"))
    }
}
