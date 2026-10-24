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
            if (size == 1L) { size = be64(raf.readAt(pos + 8, 8), 0); header = 16 }
            else if (size == 0L) size = len - pos
            if (size < header || pos + size > len) throw UnsupportedTagException("Corrupt MP4 box '$type'")
            boxes.add(Box(type, pos, size, header))
            pos += size
        }
        if (boxes.none { it.type == "ftyp" } || boxes.none { it.type == "moov" }) throw UnsupportedTagException("Not an MP4 audio file")
        if (boxes.any { it.type == "moof" }) throw UnsupportedTagException("Fragmented MP4 is not supported")
        return boxes
    }

    private class Child(val type: String, val bytes: ByteArray) // whole box incl. header

    private fun children(b: ByteArray, from: Int, to: Int): MutableList<Child> {
        val out = ArrayList<Child>()
        var p = from
        while (p + 8 <= to) {
            var size = be32(b, p).toInt()
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            if (size == 0) size = to - p
            if (size < 8 || p + size > to) throw UnsupportedTagException("Corrupt MP4 box '$type'")
            out.add(Child(type, b.copyOfRange(p, p + size)))
            p += size
        }
        return out
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        putBe32(payload.size + 8L) + type.toByteArray(Charsets.ISO_8859_1) + payload

    private fun content(c: Child) = c.bytes.copyOfRange(8, c.bytes.size)

    /** Splits a 'meta' payload into (versionAndFlags or null, children). */
    private fun metaChildren(payload: ByteArray): Pair<ByteArray?, MutableList<Child>> {
        val quickTimeStyle = payload.size >= 8 && ascii(payload, 4, 4) == "hdlr"
        return if (quickTimeStyle) null to children(payload, 0, payload.size)
        else payload.copyOfRange(0, 4) to children(payload, 4, payload.size)
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
        val inner = children(item.bytes, 8, item.bytes.size).firstOrNull { it.type == "data" } ?: return null
        if (inner.bytes.size < 16) return null
        val flags = be32(inner.bytes, 8).toInt() and 0xffffff
        val payload = inner.bytes.copyOfRange(16, inner.bytes.size)
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
        val inner = children(item.bytes, 8, item.bytes.size).firstOrNull { it.type == "data" } ?: return null
        val flags = be32(inner.bytes, 8).toInt() and 0xffffff
        if (inner.bytes.size < 16) return null
        val payload = inner.bytes.copyOfRange(16, inner.bytes.size)
        return when (flags) {
            1 -> String(payload, Charsets.UTF_8)
            2 -> String(payload, Charsets.UTF_16BE)
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    /** Reads the numeric value of a `trkn`/`disk` data atom. */
    private fun itemPair(item: Child): Int? {
        val inner = children(item.bytes, 8, item.bytes.size).firstOrNull { it.type == "data" } ?: return null
        if (inner.bytes.size < 20) return null
        return be16(inner.bytes, 18).takeIf { it > 0 }
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
        val meta = children(udta.bytes, 8, udta.bytes.size).firstOrNull { it.type == "meta" } ?: return AudioTags()
        val ilst = metaChildren(content(meta)).second.firstOrNull { it.type == "ilst" } ?: return AudioTags()
        val items = children(ilst.bytes, 8, ilst.bytes.size)
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
            val moovBox = boxes.first { it.type == "moov" }
            val oldMoov = readMoov(raf, moovBox)

            val moovChildren = children(oldMoov, moovBox.headerSize, oldMoov.size)
            val udtaIndex = moovChildren.indexOfFirst { it.type == "udta" }
            val udtaChildren = if (udtaIndex >= 0)
                children(moovChildren[udtaIndex].bytes, 8, moovChildren[udtaIndex].bytes.size) else ArrayList()
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
                children(metaKids[ilstIndex].bytes, 8, metaKids[ilstIndex].bytes.size) else ArrayList()

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
                val size = be32(moov, p).toInt()
                val type = String(moov, p + 4, 4, Charsets.ISO_8859_1)
                if (size < 8 || p + size > to) throw UnsupportedTagException("Corrupt MP4 box '$type'")
                when (type) {
                    in CONTAINERS -> walk(p + 8, p + size)
                    "stco" -> {
                        val n = be32(moov, p + 12).toInt()
                        if (16L + 4L * n > size) throw UnsupportedTagException("Corrupt stco")
                        for (i in 0 until n) {
                            val o = p + 16 + 4 * i
                            val v = be32(moov, o)
                            if (v > moovStart) {
                                val nv = v + delta
                                if (nv < 0 || nv > 0xffffffffL) throw UnsupportedTagException("Chunk offset overflow")
                                System.arraycopy(putBe32(nv), 0, moov, o, 4)
                            }
                        }
                    }
                    "co64" -> {
                        val n = be32(moov, p + 12).toInt()
                        if (16L + 8L * n > size) throw UnsupportedTagException("Corrupt co64")
                        for (i in 0 until n) {
                            val o = p + 16 + 8 * i
                            val v = be64(moov, o)
                            if (v > moovStart) System.arraycopy(putBe64(v + delta), 0, moov, o, 8)
                        }
                    }
                }
                p += size
            }
        }
        walk(8, moov.size)
    }
}
