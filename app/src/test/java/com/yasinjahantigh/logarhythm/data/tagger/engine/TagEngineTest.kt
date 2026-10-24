package com.yasinjahantigh.logarhythm.data.tagger.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun mp3_artwork_replaces_existing_apic_and_keeps_audio() {
        val oldArt = byteArrayOf(0, 'i'.code.toByte(), 0, 3, 0, 1, 2, 3)
        val src = temp("a", id3(3, "TIT2" to latin("Old"), "APIC" to oldArt) + audio)
        val dst = out()
        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 8, 7)
        Id3Engine.rewrite(
            src,
            dst,
            TagEdit(
                title = "T", artwork = cover, year = "2020",
                trackNumber = 3, discNumber = 1, composer = "Comp"
            )
        )
        val bytes = dst.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertEquals(1, Regex("APIC").findAll(text).count())
        assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))
        assertTrue(text.contains(String(cover, Charsets.ISO_8859_1)))
        val tags = Id3Engine.read(dst)
        assertEquals("T", tags.title)
        assertEquals("2020", tags.year)
        assertEquals(3, tags.trackNumber)
        assertEquals(1, tags.discNumber)
        assertEquals("Comp", tags.composer)
        assertArrayEquals(cover, tags.artwork)
        assertEquals("image/jpeg", tags.artworkMime)
    }

    @Test
    fun mp3_album_artist_round_trips() {
        val src = temp("a", audio)
        val dst = out()
        Id3Engine.rewrite(src, dst, TagEdit(albumArtist = "Various Artists"))
        assertEquals("Various Artists", Id3Engine.read(dst).albumArtist)
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
        engine.rewrite(
            src,
            dst,
            TagEdit(
                title = "New", album = "Alb", lyrics = "la", year = "1999",
                trackNumber = 4, discNumber = 2, composer = "Comp"
            )
        )
        val t = engine.read(dst)
        assertEquals("New", t.title); assertEquals("Alb", t.album); assertEquals("la", t.lyrics)
        assertEquals("1999", t.year); assertEquals(4, t.trackNumber)
        assertEquals(2, t.discNumber); assertEquals("Comp", t.composer)
        val bytes = dst.readBytes()
        assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("ALBUMARTIST=Keep"))
        assertEquals("Keep", t.albumArtist)
    }

    @Test
    fun flac_artwork_round_trips_and_can_be_removed() {
        val src = temp("f", flac())
        val withCover = out()
        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        FlacEngine.rewrite(
            src,
            withCover,
            TagEdit(artwork = cover, artworkMime = "image/jpeg", albumArtist = "AA")
        )
        val tags = FlacEngine.read(withCover)
        assertArrayEquals(cover, tags.artwork)
        assertEquals("image/jpeg", tags.artworkMime)
        assertEquals("AA", tags.albumArtist)
        val bytes = withCover.readBytes()
        assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))

        val removed = out()
        FlacEngine.rewrite(withCover, removed, TagEdit(artwork = ByteArray(0)))
        assertNull(FlacEngine.read(removed).artwork)
        assertArrayEquals(audio, removed.readBytes().copyOfRange(removed.length().toInt() - audio.size, removed.length().toInt()))
    }

    @Test
    fun flac_picture_block_round_trips() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 8, 7, 6)
        val encoded = FlacPicture.encode(image, "image/jpeg")
        val picture = FlacPicture.decode(encoded)!!
        assertEquals(FlacPicture.TYPE_FRONT_COVER, picture.type)
        assertEquals("image/jpeg", picture.mime)
        assertArrayEquals(image, picture.data)
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
    fun mp4_artwork_is_added_without_losing_chunk_offsets() {
        val ftyp = box("ftyp", "M4A ".toByteArray() + ByteArray(4) + "M4A ".toByteArray())
        fun build(chunkOffset: Long) =
            box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stco", ByteArray(4) + putBe32(1) + putBe32(chunkOffset)))))))
        val moovLen = build(0).size
        val file = ftyp + build((ftyp.size + moovLen + 8).toLong()) + box("mdat", audio)
        val src = temp("m", file)
        val dst = out()
        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 4, 5, 6)
        Mp4Engine.rewrite(
            src,
            dst,
            TagEdit(
                title = "T", artwork = cover, year = "2021", albumArtist = "Various Artists",
                trackNumber = 7, discNumber = 2, composer = "Comp"
            )
        )
        val bytes = dst.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("covr"))
        assertArrayEquals(audio, bytes.copyOfRange(bytes.size - audio.size, bytes.size))
        val mdatPos = text.indexOf("mdat") - 4
        val stcoPos = text.indexOf("stco")
        assertEquals((mdatPos + 8).toLong(), be32(bytes, stcoPos + 12))
        val tags = Mp4Engine.read(dst)
        assertEquals("T", tags.title)
        assertEquals("2021", tags.year)
        assertEquals(7, tags.trackNumber)
        assertEquals(2, tags.discNumber)
        assertEquals("Comp", tags.composer)
        assertEquals("Various Artists", tags.albumArtist)
        assertArrayEquals(cover, tags.artwork)
    }

    @Test
    fun unknown_and_truncated_inputs_fail_closed() {
        assertNull(TagEngines.detect(temp("x", ByteArray(64))))
        val bad = temp("m", box("ftyp", ByteArray(12)) + putBe32(9999) + "moov".toByteArray())
        try { Mp4Engine.rewrite(bad, out(), TagEdit(title = "x")); fail() } catch (_: UnsupportedTagException) {}
        assertTrue(TagEdit().isEmpty)
        assertTrue(!TagEdit(artwork = byteArrayOf(1)).isEmpty)
    }
}
