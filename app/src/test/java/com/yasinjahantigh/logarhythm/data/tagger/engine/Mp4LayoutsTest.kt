package com.yasinjahantigh.logarhythm.data.tagger.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Mp4LayoutsTest {
    private fun box(type: String, body: ByteArray = byteArrayOf(), extended: Boolean = false): ByteArray =
        (if (extended) putBe32(1) + type.toByteArray(Charsets.ISO_8859_1) + putBe64(body.size + 16L)
        else putBe32(body.size + 8L) + type.toByteArray(Charsets.ISO_8859_1)) + body
    private val ftyp = box("ftyp", "M4A ".toByteArray() + ByteArray(8))
    private val audio = ByteArray(100) { it.toByte() }
    private fun file(bytes: ByteArray) = File.createTempFile("layout", ".m4a").apply { writeBytes(bytes); deleteOnExit() }
    private fun rewrite(bytes: ByteArray): ByteArray {
        val source = file(bytes)
        val target = file(byteArrayOf())
        Mp4Engine.rewrite(source, target, TagEdit(title = "Updated"))
        assertEquals("Updated", Mp4Engine.read(target).title)
        assertArrayEquals(bytes, source.readBytes())
        return target.readBytes()
    }
    @Test fun moovAtEndDoesNotShiftEarlierMedia() {
        val prefix = ftyp + box("mdat", audio)
        val result = rewrite(prefix + box("moov"))
        assertArrayEquals(prefix, result.copyOfRange(0, prefix.size))
    }
    @Test fun extendedMoovAndNestedBoxes() {
        rewrite(ftyp + box("moov", box("udta", box("meta", ByteArray(4) + box("ilst"), true), true), true) + box("mdat", audio))
    }
    @Test fun quickTimeMetaWithoutHandler() {
        rewrite(ftyp + box("moov", box("udta", box("meta", box("ilst")))) + box("mdat", audio))
    }
    @Test fun trailingBytesArePreserved() {
        val trailing = byteArrayOf(1, 2, 3)
        val result = rewrite(ftyp + box("moov") + box("mdat", audio) + trailing)
        assertArrayEquals(trailing, result.takeLast(3).toByteArray())
    }
    @Test fun sizeZeroContainerAndExtendedChunkOffsets() {
        fun moov(offset: Long): ByteArray {
            val stco = box("stco", ByteArray(4) + putBe32(1) + putBe32(offset), true)
            return box("moov", putBe32(0) + "trak".toByteArray() + box("mdia", box("minf", box("stbl", stco))))
        }
        val result = rewrite(ftyp + moov((ftyp.size + moov(0).size + 8).toLong()) + box("mdat", audio))
        val text = String(result, Charsets.ISO_8859_1)
        assertEquals((text.indexOf("mdat") + 4).toLong(), be32(result, text.indexOf("stco") + 20))
    }
    @Test fun fragmentedMetadataCanBeReadButNeverRewrittenWithoutRemux() {
        val bytes = ftyp + box("moov") + box("moof") + box("mdat", audio)
        val source = file(bytes)
        assertNull(Mp4Engine.read(source).title)
        try {
            Mp4Engine.rewrite(source, file(byteArrayOf()), TagEdit(title = "x"))
            fail("Fragment offsets require remuxing first")
        } catch (_: UnsupportedTagException) { }
        assertArrayEquals(bytes, source.readBytes())
    }
}
