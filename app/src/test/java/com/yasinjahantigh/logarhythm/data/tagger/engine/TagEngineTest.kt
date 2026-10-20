package com.yasinjahantigh.logarhythm.data.tagger.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class TagEngineTest {

    private val audio = ByteArray(5000) { ((it * 31 + 7) and 0xff).toByte() }.also {
        it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte() // looks like an MPEG frame header
    }

    private fun temp(name: String, bytes: ByteArray) =
        File.createTempFile("tag_$name", ".bin").apply { writeBytes(bytes); deleteOnExit() }

    private fun out() = File.createTempFile("out", ".bin").apply { deleteOnExit() }

    private fun syncsafe(v: Int) = byteArrayOf((v shr 21 and 0x7f).toByte(), (v shr 14 and 0x7f).toByte(), (v shr 7 and 0x7f).toByte(), (v and 0x7f).toByte())

    private fun id3(version: Int, vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, payload) in frames) {
            body.write(id.toByteArray())
            body.write(if (version == 4) syncsafe(payload.size) else putBe32(payload.size.toLong()))
            body.write(byteArrayOf(0, 0)); body.write(payload)
        }
        val b = body.toByteArray()
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + syncsafe(b.size) + b
    }

    private fun latin(s: String) = byteArrayOf(0) + s.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun mp3_rewrite_keeps_audio_and_unrelated_frames_for_v23_and_v24() {
        for (v in listOf(3, 4)) {
            val art = byteArrayOf(0, 'i'.code.toByte(), 0, 3, 0, 9, 8, 7)
            val src = temp("a", id3(v, "TIT2" to latin("Old"), "APIC" to art) + audio)
            val dst = out()
            val engine = TagEngines.detect(src)!!
            assertSame(Id3Engine, engine)
            engine.rewrite(src, dst, TagEdit(title = "عنوان", artist = "Artist", genre = "Pop", lyrics = "a\nب"))
            val tags = engine.read(dst)
            assertEquals("عنوان", tags.title); assertEquals("Artist", tags.artist)
            assertEquals("Pop", tags.genre); assertEquals("a\nب", tags.lyrics); assertNull(tags.album)
            val bytes = dst.readBytes()
            assertEquals(v, bytes[3].toInt())
            assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))
            assertTrue(String(bytes, Charsets.ISO_8859_1).contains("APIC")) // artwork survived
        }
    }

    @Test
    fun mp3_without_tag_gets_one_and_blank_edit_removes_field() {
        val src = temp("a", audio)
        val dst = out()
        Id3Engine.rewrite(src, dst, TagEdit(title = "T", artist = "A"))
        assertEquals("T", Id3Engine.read(dst).title)
        val dst2 = out()
        Id3Engine.rewrite(dst, dst2, TagEdit(artist = ""))
        assertNull(Id3Engine.read(dst2).artist); assertEquals("T", Id3Engine.read(dst2).title)
    }

    @Test
    fun clearing_lyrics_removes_the_frame() {
        val src = temp("a", audio)
        val a = out(); val b = out()
        Id3Engine.rewrite(src, a, TagEdit(lyrics = "hello"))
        assertEquals("hello", Id3Engine.read(a).lyrics)
        Id3Engine.rewrite(a, b, TagEdit(lyrics = ""))
        assertNull(Id3Engine.read(b).lyrics)
    }

    private fun flac(): ByteArray {
        val streamInfo = ByteArray(34) { 1 }
        val vc = VorbisComments("v".toByteArray(), mutableListOf("TITLE" to "Old", "ALBUMARTIST" to "Keep")).toBytes()
        fun block(last: Boolean, type: Int, d: ByteArray) =
            byteArrayOf(((if (last) 0x80 else 0) or type).toByte(), (d.size shr 16).toByte(), (d.size shr 8).toByte(), d.size.toByte()) + d
        return "fLaC".toByteArray() + block(false, 0, streamInfo) + block(true, 4, vc) + audio
    }

    @Test
    fun flac_edits_comments_keeps_others_and_audio() {
        val src = temp("f", flac()); val dst = out()
        val engine = TagEngines.detect(src)!!
        assertSame(FlacEngine, engine)
        engine.rewrite(src, dst, TagEdit(title = "New", album = "Alb", lyrics = "la"))
        val t = engine.read(dst)
        assertEquals("New", t.title); assertEquals("Alb", t.album); assertEquals("la", t.lyrics)
        val bytes = dst.readBytes()
        assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("ALBUMARTIST=Keep"))
    }

    private fun box(type: String, payload: ByteArray) = putBe32(payload.size + 8L) + type.toByteArray(Charsets.ISO_8859_1) + payload

    @Test
    fun mp4_moov_before_mdat_shifts_chunk_offsets() {
        val ftyp = box("ftyp", "M4A ".toByteArray() + ByteArray(4) + "M4A ".toByteArray())
        fun build(chunkOffset: Long): ByteArray {
            val stco = box("stco", ByteArray(4) + putBe32(1) + putBe32(chunkOffset))
            val moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", stco)))))
            return moov
        }
        val moovLen = build(0).size
        val mdatStart = ftyp.size + moovLen
        val chunk = (mdatStart + 8).toLong()
        val file = ftyp + build(chunk) + box("mdat", audio)
        val src = temp("m", file); val dst = out()
        assertSame(Mp4Engine, TagEngines.detect(src))
        Mp4Engine.rewrite(src, dst, TagEdit(title = "Titel", artist = "Ar", lyrics = "ly"))
        val t = Mp4Engine.read(dst)
        assertEquals("Titel", t.title); assertEquals("ly", t.lyrics)
        val out = dst.readBytes()
        val mdatPos = String(out, Charsets.ISO_8859_1).indexOf("mdat") - 4
        val stcoPos = String(out, Charsets.ISO_8859_1).indexOf("stco")
        assertEquals((mdatPos + 8).toLong(), be32(out, stcoPos + 12))
        assertArrayEquals(audio, out.copyOfRange(mdatPos + 8, out.size))
    }

    @Test
    fun unknown_and_truncated_inputs_fail_closed() {
        assertNull(TagEngines.detect(temp("x", ByteArray(64))))
        val bad = temp("m", box("ftyp", ByteArray(12)) + putBe32(9999) + "moov".toByteArray())
        try { Mp4Engine.rewrite(bad, out(), TagEdit(title = "x")); fail() } catch (_: UnsupportedTagException) {}
        assertNotNull(TagEdit().isEmpty)
    }
}
