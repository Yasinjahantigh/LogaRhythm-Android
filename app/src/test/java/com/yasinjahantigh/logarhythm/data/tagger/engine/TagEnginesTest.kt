package com.yasinjahantigh.logarhythm.data.tagger.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class TagEnginesTest {

    private val audio = ByteArray(2048) { (it * 31).toByte().let { b -> if (b == 0.toByte()) 1 else b } }
        .also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte() }

    private fun temp(bytes: ByteArray): File = File.createTempFile("tag", ".bin").apply { writeBytes(bytes); deleteOnExit() }

    private fun id3v23(title: String): ByteArray {
        val payload = byteArrayOf(0) + title.toByteArray(Charsets.ISO_8859_1)
        val frame = "TIT2".toByteArray() + putBe32(payload.size.toLong()) + byteArrayOf(0, 0) + payload
        val size = frame.size
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0) +
            byteArrayOf(((size shr 21) and 0x7f).toByte(), ((size shr 14) and 0x7f).toByte(), ((size shr 7) and 0x7f).toByte(), (size and 0x7f).toByte())
        return header + frame + audio
    }

    @Test
    fun mp3_roundTripKeepsAudioAndSupportsPersian() {
        val src = temp(id3v23("Old"))
        val dst = temp(ByteArray(0))
        val engine = TagEngines.detect(src)!!
        assertSame(Id3Engine, engine)
        engine.rewrite(src, dst, TagEdit(title = "عنوان", artist = "Artist", lyrics = "خط ۱\nline 2"))
        val tags = engine.read(dst)
        assertEquals("عنوان", tags.title)
        assertEquals("Artist", tags.artist)
        assertEquals("خط ۱\nline 2", tags.lyrics)
        val bytes = dst.readBytes()
        assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))
    }

    @Test
    fun mp3_blankEditRemovesFieldAndNullKeepsIt() {
        val src = temp(id3v23("Keep me"))
        val dst = temp(ByteArray(0))
        Id3Engine.rewrite(src, dst, TagEdit(artist = "A"))
        assertEquals("Keep me", Id3Engine.read(dst).title)
        val dst2 = temp(ByteArray(0))
        Id3Engine.rewrite(dst, dst2, TagEdit(title = ""))
        assertNull(Id3Engine.read(dst2).title)
        assertEquals("A", Id3Engine.read(dst2).artist)
    }

    @Test
    fun flac_commentBlockIsReplacedAndAudioUntouched() {
        val streamInfo = ByteArray(34)
        val frames = ByteArray(300) { 0x55 }
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray())
        out.write(0x80); out.write(byteArrayOf(0, 0, 34)); out.write(streamInfo)
        out.write(frames)
        val src = temp(out.toByteArray())
        val dst = temp(ByteArray(0))
        assertSame(FlacEngine, TagEngines.detect(src))
        FlacEngine.rewrite(src, dst, TagEdit(title = "T", genre = "G", lyrics = "L"))
        val tags = FlacEngine.read(dst)
        assertEquals("T", tags.title); assertEquals("G", tags.genre); assertEquals("L", tags.lyrics)
        val bytes = dst.readBytes()
        assertArrayEquals(frames, bytes.copyOfRange(bytes.size - frames.size, bytes.size))
    }

    @Test(expected = UnsupportedTagException::class)
    fun truncatedMp4FailsClosed() {
        val src = temp(byteArrayOf(0, 0, 0, 16, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 'm'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte()))
        Mp4Engine.rewrite(src, temp(ByteArray(0)), TagEdit(title = "x"))
    }
}
