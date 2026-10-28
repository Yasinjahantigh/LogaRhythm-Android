package com.yasinjahantigh.logarhythm.data.tagger.engine

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * MP4 / M4A (AAC, ALAC). Rewrites moov/udta/meta/ilst and, when moov sits in front of the media
 * data, shifts every stco/co64 chunk offset so playback keeps pointing at the right bytes.
 */
object Mp4Engine : TagEngine {
    private const val MAX_MOOV = 64 * 1024 * 1024

    private class Box(val type: String, val offset: Long, val size: Long, val headerSize: Int)

    private fun topLevel(raf: RandomAccessFile): List<Box> {
        val boxes = ArrayList<Box>()
        var pos = 0L
        val len = raf.length()
        while (pos + 8 <= len) {
            val h = raf.readAt(pos, 8)
            var size = be32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (len - pos < 16) throw UnsupportedTagException("Truncated MP4 box '$type'")
                size = be64(raf.readAt(pos + 8, 8), 0); header = 16
            } else if (size == 0L) size = len - pos
            if (size < header || size > len - pos) throw UnsupportedTagException("Corrupt MP4 box '$type'")
            boxes.add(Box(type, pos, size, header))
            pos += size
        }
        if (boxes.none { it.type == "ftyp" } || boxes.none { it.type == "moov" }) throw UnsupportedTagException("Not an MP4 audio file")
        return boxes
    }

    private class Child(val type: String, val bytes: ByteArray) {
        val headerSize: Int get() = if (bytes.size >= 16 && be32(bytes, 0) == 1L) 16 else 8
    }

    private fun layout(bytes: ByteArray, offset: Int, end: Int): Pair<Int, Int> {
        if (end - offset < 8) throw UnsupportedTagException("Truncated MP4 box")
        val shortSize = be32(bytes, offset)
        val header = if (shortSize == 1L) 16 else 8
        if (end - offset < header) throw UnsupportedTagException("Truncated MP4 box")
        val size = when (shortSize) {
            0L -> (end - offset).toLong()
            1L -> be64(bytes, offset + 8)
            else -> shortSize
        }
        if (size < header || size > end - offset) throw UnsupportedTagException("Corrupt MP4 box size")
        return size.toInt() to header
    }

    private fun children(b: ByteArray, from: Int, to: Int): MutableList<Child> {
        val out = ArrayList<Child>()
        var p = from
        while (p + 8 <= to) {
            val (size, _) = layout(b, p, to)
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            val bytes = b.copyOfRange(p, p + size)
            if (be32(bytes, 0) == 0L) System.arraycopy(putBe32(size.toLong()), 0, bytes, 0, 4)
            out.add(Child(type, bytes))
            p += size
        }
        if (p < to) out.add(Child("", b.copyOfRange(p, to)))
        return out
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        putBe32(payload.size + 8L) + type.toByteArray(Charsets.ISO_8859_1) + payload

    private fun content(c: Child) = c.bytes.copyOfRange(c.headerSize, c.bytes.size)

    private fun metaChildren(payload: ByteArray): Pair<ByteArray?, MutableList<Child>> {
        val quickTimeStyle = payload.size >= 8 && payload.sliceArray(4..7).all { it.toInt() in 32..126 }
        if (quickTimeStyle) return null to children(payload, 0, payload.size)
        if (payload.size < 4) throw UnsupportedTagException("Truncated meta flags")
        return payload.copyOfRange(0, 4) to children(payload, 4, payload.size)
    }

    private val KEY_TITLE = "\u00A9nam"
    private val KEY_ARTIST = "\u00A9ART"
    private val KEY_ALBUM = "\u00A9alb"
    private val KEY_ALBUM_ARTIST = "aART"
    private val KEY_GENRE = "\u00A9gen"
    private val KEY_LYRICS = "\u00A9lyr"
    private val KEY_YEAR = "\u00A9day"
    private val KEY_COMPOSER = "\u00A9wrt"

    private fun textItem(key: String, value: String): ByteArray {
        val data = box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + value.toByteArray(Charsets.UTF_8))
        return box(key, data)
    }

    private fun artworkItem(image: ByteArray, mimeHint: String? = null): ByteArray {
        val mime = mimeHint?.takeIf { it.isNotBlank() } ?: FlacPicture.detectMime(image)
        val type = when (mime.lowercase()) {
            "image/png" -> 14
            "image/gif" -> 12
            "image/bmp" -> 27
            else -> 13
        }
        val data = box("data", byteArrayOf(0, 0, 0, type.toByte(), 0, 0, 0, 0) + image)
        return box("covr", data)
    }

    private class Cover(val mime: String, val data: ByteArray)

    private fun itemArtwork(item: Child): Cover? {
        val inner = children(item.bytes, item.headerSize, item.bytes.size).firstOrNull { it.type == "data" } ?: return null
        if (inner.bytes.size < inner.headerSize + 8) return null
        val flags = be32(inner.bytes, inner.headerSize).toInt() and 0xffffff
        val payload = inner.bytes.copyOfRange(inner.headerSize + 8, inner.bytes.size)
        if (payload.isEmpty()) return null
        val mime = when (flags) {
            12 -> "image/gif"
            14 -> "image/png"
            27 -> "image/bmp"
            else -> "image/jpeg"
        }
        return Cover(mime, payload)
    }

    private fun itemText(item: Child): String? {
        val inner = children(item.bytes, item.headerSize, item.bytes.size).firstOrNull { it.type == "data" } ?: return null
        if (inner.bytes.size < inner.headerSize + 8) return null
        val flags = be32(inner.bytes, inner.headerSize).toInt() and 0xffffff
        val payload = inner.bytes.copyOfRange(inner.headerSize + 8, inner.bytes.size)
        return when (flags) {
            1 -> String(payload, Charsets.UTF_8)
            2 -> String(payload, Charsets.UTF_16BE)
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    /** Reads the numeric value of a `trkn`/`disk` data atom. */
    private fun itemPair(item: Child): Int? {
        val inner = children(item.bytes, item.headerSize, item.bytes.size).firstOrNull { it.type == "data" } ?: return null
        if (inner.bytes.size < inner.headerSize + 12) return null
        return be16(inner.bytes, inner.headerSize + 10).takeIf { it > 0 }
    }

    private fun pairItem(key: String, value: Int): ByteArray {
        val payload = ByteArray(8)
        payload[2] = (value shr 8).toByte()
        payload[3] = value.toByte()
        return box(key, box("data", ByteArray(8) + payload))
    }

    private fun latin1Key(s: String) = String(s.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1)

    private fun readMoov(raf: RandomAccessFile, moov: Box): ByteArray {
        if (moov.size > MAX_MOOV) throw UnsupportedTagException("moov atom too large")
        return raf.readAt(moov.offset, moov.size.toInt())
    }

    override fun read(file: File): AudioTags = RandomAccessFile(file, "r").use { raf ->
        val moovBox = topLevel(raf).first { it.type == "moov" }
        val moov = readMoov(raf, moovBox)
        val udta = children(moov, moovBox.headerSize, moov.size).firstOrNull { it.type == "udta" } ?: return AudioTags()
        val meta = children(udta.bytes, udta.headerSize, udta.bytes.size).firstOrNull { it.type == "meta" } ?: return AudioTags()
        val ilst = metaChildren(content(meta)).second.firstOrNull { it.type == "ilst" } ?: return AudioTags()
        val items = children(ilst.bytes, ilst.headerSize, ilst.bytes.size)
        fun get(key: String) = items.firstOrNull { it.type == latin1Key(key) }?.let { itemText(it) }
        fun pair(key: String) = items.firstOrNull { it.type == key }?.let { itemPair(it) }
        val cover = items.firstOrNull { it.type == "covr" }?.let { itemArtwork(it) }
        AudioTags(
            title = get(KEY_TITLE),
            artist = get(KEY_ARTIST),
            album = get(KEY_ALBUM),
            albumArtist = get(KEY_ALBUM_ARTIST),
            genre = get(KEY_GENRE),
            lyrics = get(KEY_LYRICS),
            year = get(KEY_YEAR),
            trackNumber = pair("trkn"),
            discNumber = pair("disk"),
            composer = get(KEY_COMPOSER),
            artwork = cover?.data,
            artworkMime = cover?.mime
        )
    }

    override fun rewrite(source: File, target: File, edit: TagEdit) {
        RandomAccessFile(source, "r").use { raf ->
            val boxes = topLevel(raf)
            if (boxes.any { it.type == "moof" }) throw UnsupportedTagException("Fragmented MP4 requires remuxing")
            val moovBox = boxes.first { it.type == "moov" }
            val oldMoov = readMoov(raf, moovBox)

            val moovChildren = children(oldMoov, moovBox.headerSize, oldMoov.size)
            val udtaIndex = moovChildren.indexOfFirst { it.type == "udta" }
            val udtaChildren = if (udtaIndex >= 0)
                children(moovChildren[udtaIndex].bytes, moovChildren[udtaIndex].headerSize, moovChildren[udtaIndex].bytes.size) else ArrayList()
            val metaIndex = udtaChildren.indexOfFirst { it.type == "meta" }
            var versionFlags: ByteArray? = byteArrayOf(0, 0, 0, 0)
            var metaKids: MutableList<Child> = ArrayList()
            if (metaIndex >= 0) {
                val m = metaChildren(content(udtaChildren[metaIndex]))
                versionFlags = m.first; metaKids = m.second
            }
            if (metaKids.none { it.type == "hdlr" }) {
                val hdlr = box("hdlr", ByteArray(4) + ByteArray(4) + "mdir".toByteArray() + "appl".toByteArray() + ByteArray(8) + byteArrayOf(0))
                metaKids.add(0, Child("hdlr", hdlr))
            }
            val ilstIndex = metaKids.indexOfFirst { it.type == "ilst" }
            val items: MutableList<Child> = if (ilstIndex >= 0)
                children(metaKids[ilstIndex].bytes, metaKids[ilstIndex].headerSize, metaKids[ilstIndex].bytes.size) else ArrayList()

            fun setText(key: String, value: String?, also: List<String> = emptyList()) {
                if (value == null) return
                val k = latin1Key(key)
                items.removeAll { it.type == k || it.type in also }
                if (value.isNotBlank()) items.add(Child(k, textItem(key, value)))
            }
            setText(KEY_TITLE, edit.title)
            setText(KEY_ARTIST, edit.artist)
            setText(KEY_ALBUM, edit.album)
            setText(KEY_ALBUM_ARTIST, edit.albumArtist)
            setText(KEY_GENRE, edit.genre, also = listOf("gnre"))
            setText(KEY_LYRICS, edit.lyrics)
            setText(KEY_YEAR, edit.year)
            setText(KEY_COMPOSER, edit.composer)
            if (edit.artwork != null) {
                items.removeAll { it.type == "covr" }
                if (edit.artwork.isNotEmpty()) {
                    items.add(Child("covr", artworkItem(edit.artwork, edit.artworkMime)))
                }
            }
            edit.trackNumber?.let { value ->
                items.removeAll { it.type == "trkn" }
                if (value > 0) items.add(Child("trkn", pairItem("trkn", value)))
            }
            edit.discNumber?.let { value ->
                items.removeAll { it.type == "disk" }
                if (value > 0) items.add(Child("disk", pairItem("disk", value)))
            }

            val newIlst = Child("ilst", box("ilst", items.fold(ByteArray(0)) { a, c -> a + c.bytes }))
            if (ilstIndex >= 0) metaKids[ilstIndex] = newIlst else metaKids.add(newIlst)
            // Drop old padding inside meta; stale 'free' boxes only waste space.
            val metaBody = (versionFlags ?: ByteArray(0)) + metaKids.filter { it.type != "free" }.fold(ByteArray(0)) { a, c -> a + c.bytes }
            val newMeta = Child("meta", box("meta", metaBody))
            if (metaIndex >= 0) udtaChildren[metaIndex] = newMeta else udtaChildren.add(newMeta)
            val newUdta = Child("udta", box("udta", udtaChildren.fold(ByteArray(0)) { a, c -> a + c.bytes }))
            if (udtaIndex >= 0) moovChildren[udtaIndex] = newUdta else moovChildren.add(newUdta)

            var newMoov = box("moov", moovChildren.fold(ByteArray(0)) { a, c -> a + c.bytes })
            val delta = newMoov.size.toLong() - moovBox.size
            val moovFirst = boxes.any { it.type == "mdat" && it.offset > moovBox.offset }
            if (moovFirst && delta != 0L) shiftChunkOffsets(newMoov, moovBox.offset, delta)

            BufferedOutputStream(target.outputStream(), COPY_BUFFER).use { out ->
                copyRange(raf, 0, moovBox.offset, out)
                out.write(newMoov)
                copyRange(raf, moovBox.offset + moovBox.size, raf.length(), out)
            }
        }
    }

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")

    /** Adds [delta] to every chunk offset that points past the old moov start. Mutates [moov] in place. */
    private fun shiftChunkOffsets(moov: ByteArray, moovStart: Long, delta: Long) {
        fun walk(from: Int, to: Int) {
            var p = from
            while (p + 8 <= to) {
                val (size, header) = layout(moov, p, to)
                val type = String(moov, p + 4, 4, Charsets.ISO_8859_1)
                when (type) {
                    in CONTAINERS -> walk(p + header, p + size)
                    "stco", "co64" -> {
                        val width = if (type == "stco") 4 else 8
                        if (size < header + 8) throw UnsupportedTagException("Truncated $type")
                        val n = be32(moov, p + header + 4)
                        if (header + 8L + width * n > size) throw UnsupportedTagException("Corrupt $type")
                        for (i in 0 until n.toInt()) {
                            val o = p + header + 8 + width * i
                            val v = if (width == 4) be32(moov, o) else be64(moov, o)
                            if (v > moovStart) {
                                val shifted = Math.addExact(v, delta)
                                if (shifted < 0 || (width == 4 && shifted > 0xffffffffL)) {
                                    throw UnsupportedTagException("Chunk offset overflow")
                                }
                                val bytes = if (width == 4) putBe32(shifted) else putBe64(shifted)
                                System.arraycopy(bytes, 0, moov, o, width)
                            }
                        }
                    }
                }
                p += size
            }
        }
        walk(8, moov.size)
    }
}
