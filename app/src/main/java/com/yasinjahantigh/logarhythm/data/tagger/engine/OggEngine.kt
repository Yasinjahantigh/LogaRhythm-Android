package com.yasinjahantigh.logarhythm.data.tagger.engine

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Ogg Vorbis and Ogg Opus. The comment packet lives in the header pages, so those pages are
 * rebuilt (with fresh CRCs); audio pages are copied verbatim unless the page count changes,
 * in which case only their sequence numbers and CRCs are refreshed.
 */
object OggEngine : TagEngine {
    private class Page(
        val headerType: Int, val granule: Long, val serial: Long, val seq: Long,
        val segments: IntArray, val data: ByteArray, val offset: Long, val totalSize: Int
    )

    private enum class Kind(val headerPackets: Int) { VORBIS(3), OPUS(2) }

    private class Headers(val kind: Kind, val packets: List<ByteArray>, val serial: Long, val endOffset: Long, val pagesUsed: Int)

    private fun readPage(raf: RandomAccessFile, offset: Long): Page? {
        if (offset + 27 > raf.length()) return null
        val h = raf.readAt(offset, 27)
        if (ascii(h, 0, 4) != "OggS" || h[4].toInt() != 0) throw UnsupportedTagException("Corrupt Ogg page")
        val nseg = h[26].toInt() and 0xff
        val table = raf.readAt(offset + 27, nseg)
        val segs = IntArray(nseg) { table[it].toInt() and 0xff }
        val dataLen = segs.sum()
        val data = raf.readAt(offset + 27 + nseg, dataLen)
        return Page(
            h[5].toInt() and 0xff, be64le(h, 6), le32(h, 14), le32(h, 18),
            segs, data, offset, 27 + nseg + dataLen
        )
    }

    private fun be64le(b: ByteArray, o: Int): Long = le32(b, o) or (le32(b, o + 4) shl 32)

    private fun readHeaders(raf: RandomAccessFile): Headers {
        var offset = 0L
        var kind: Kind? = null
        var serial = -1L
        val packets = ArrayList<ByteArray>()
        var current = ByteArrayOutputStream()
        var pages = 0
        while (true) {
            val page = readPage(raf, offset) ?: throw UnsupportedTagException("Ogg headers are incomplete")
            if (serial == -1L) serial = page.serial
            else if (page.serial != serial) throw UnsupportedTagException("Multiplexed Ogg streams are not supported")
            var p = 0
            for (s in page.segments) {
                current.write(page.data, p, s); p += s
                if (s < 255) {
                    packets.add(current.toByteArray()); current = ByteArrayOutputStream()
                    if (kind == null) {
                        val first = packets[0]
                        kind = when {
                            first.size >= 7 && first[0].toInt() == 1 && ascii(first, 1, 6) == "vorbis" -> Kind.VORBIS
                            first.size >= 8 && ascii(first, 0, 8) == "OpusHead" -> Kind.OPUS
                            else -> throw UnsupportedTagException("Only Ogg Vorbis and Opus can be edited")
                        }
                    }
                }
            }
            offset += page.totalSize
            pages++
            if (kind != null && packets.size >= kind.headerPackets) {
                if (current.size() != 0) throw UnsupportedTagException("Unexpected Ogg header layout")
                return Headers(kind, packets.take(kind.headerPackets), serial, offset, pages)
            }
        }
    }

    private fun commentsOf(h: Headers): VorbisComments = when (h.kind) {
        Kind.VORBIS -> {
            val p = h.packets[1]
            if (p.size < 7 || p[0].toInt() != 3 || ascii(p, 1, 6) != "vorbis") throw UnsupportedTagException("Bad Vorbis comment header")
            VorbisComments.parse(p, 7)
        }
        Kind.OPUS -> {
            val p = h.packets[1]
            if (p.size < 8 || ascii(p, 0, 8) != "OpusTags") throw UnsupportedTagException("Bad Opus tags header")
            VorbisComments.parse(p, 8)
        }
    }

    override fun read(file: File): AudioTags = RandomAccessFile(file, "r").use { commentsOf(readHeaders(it)).toTags() }

    override fun rewrite(source: File, target: File, edit: TagEdit) {
        RandomAccessFile(source, "r").use { raf ->
            val h = readHeaders(raf)
            val comments = commentsOf(h)
            comments.apply(edit)
            val body = comments.toBytes()
            val commentPacket = when (h.kind) {
                Kind.VORBIS -> byteArrayOf(3) + "vorbis".toByteArray() + body + byteArrayOf(1)
                Kind.OPUS -> "OpusTags".toByteArray() + body
            }
            val newPackets = ArrayList<ByteArray>()
            newPackets.add(h.packets[0]); newPackets.add(commentPacket)
            if (h.kind == Kind.VORBIS) newPackets.add(h.packets[2])

            BufferedOutputStream(target.outputStream(), COPY_BUFFER).use { out ->
                val written = writePackets(out, newPackets, h.serial)
                val delta = written - h.pagesUsed
                var offset = h.endOffset
                if (delta == 0) {
                    copyRange(raf, offset, raf.length(), out)
                } else {
                    while (true) {
                        val page = readPage(raf, offset) ?: break
                        if (page.serial != h.serial) throw UnsupportedTagException("Chained Ogg streams are not supported")
                        writePage(out, page.headerType, page.granule, page.serial, page.seq + delta, page.segments, page.data)
                        offset += page.totalSize
                    }
                }
            }
        }
    }

    /** Lays packets out on pages (first packet alone on the BOS page). Returns the page count. */
    private fun writePackets(out: OutputStream, packets: List<ByteArray>, serial: Long): Int {
        var seq = 0L
        // Page 0: identification packet only.
        val first = packets[0]
        val firstSegs = segmentsFor(first.size)
        writePage(out, 0x02, 0, serial, seq++, firstSegs, first)

        val segs = ArrayList<Int>()
        val data = ByteArrayOutputStream()
        var continued = false
        var finishedOnPage = false
        var lastSeg = 0
        fun flush() {
            val granule = if (finishedOnPage) 0L else -1L
            writePage(out, if (continued) 0x01 else 0x00, granule, serial, seq++, segs.toIntArray(), data.toByteArray())
            segs.clear(); data.reset(); finishedOnPage = false
            continued = lastSeg == 255
        }
        for (i in 1 until packets.size) {
            val pkt = packets[i]
            val ps = segmentsFor(pkt.size)
            var consumed = 0
            for ((idx, s) in ps.withIndex()) {
                if (segs.size == 255) flush()
                segs.add(s)
                lastSeg = s
                data.write(pkt, consumed, s)
                consumed += s
                if (idx == ps.lastIndex) finishedOnPage = true
            }
        }
        // Audio must start on a fresh page, so close the final header page.
        flush()
        return seq.toInt()
    }

    private fun segmentsFor(length: Int): IntArray {
        val n = length / 255
        return IntArray(n + 1) { if (it < n) 255 else length % 255 }
    }

    private fun writePage(out: OutputStream, type: Int, granule: Long, serial: Long, seq: Long, segs: IntArray, data: ByteArray) {
        val header = ByteArrayOutputStream(27 + segs.size)
        header.write("OggS".toByteArray()); header.write(0); header.write(type)
        header.write(putLe32(granule and 0xffffffffL)); header.write(putLe32((granule ushr 32) and 0xffffffffL))
        header.write(putLe32(serial)); header.write(putLe32(seq and 0xffffffffL))
        header.write(byteArrayOf(0, 0, 0, 0)) // CRC placeholder
        header.write(segs.size)
        segs.forEach { header.write(it) }
        val bytes = header.toByteArray()
        var crc = 0L
        crc = crcUpdate(crc, bytes); crc = crcUpdate(crc, data)
        val crcBytes = putLe32(crc)
        System.arraycopy(crcBytes, 0, bytes, 22, 4)
        out.write(bytes); out.write(data)
    }

    private val CRC_TABLE = LongArray(256).also { t ->
        for (i in 0 until 256) {
            var r = i.toLong() shl 24
            repeat(8) { r = if ((r and 0x80000000L) != 0L) ((r shl 1) xor 0x04C11DB7L) else (r shl 1) }
            t[i] = r and 0xffffffffL
        }
    }

    private fun crcUpdate(start: Long, b: ByteArray): Long {
        var crc = start
        for (x in b) crc = ((crc shl 8) and 0xffffffffL) xor CRC_TABLE[(((crc ushr 24) and 0xff).toInt()) xor (x.toInt() and 0xff)]
        return crc
    }
}
