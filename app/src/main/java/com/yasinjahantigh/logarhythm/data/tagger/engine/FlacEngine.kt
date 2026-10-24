package com.yasinjahantigh.logarhythm.data.tagger.engine

import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile

/** Native FLAC: edits the VORBIS_COMMENT metadata block and leaves every other block and frame alone. */
object FlacEngine : TagEngine {
    private class Block(val type: Int, val data: ByteArray)

    private class Layout(val prefixEnd: Long, val blocks: List<Block>, val audioStart: Long)

    private fun layout(raf: RandomAccessFile): Layout {
        var pos = 0L
        // Tolerate a leading ID3v2 tag (some taggers add one); it is copied through untouched.
        if (raf.length() >= 10) {
            val h = raf.readAt(0, 10)
            if (ascii(h, 0, 3) == "ID3") {
                val size = ((h[6].toInt() and 0x7f) shl 21) or ((h[7].toInt() and 0x7f) shl 14) or
                    ((h[8].toInt() and 0x7f) shl 7) or (h[9].toInt() and 0x7f)
                pos = 10L + size + if ((h[5].toInt() and 0x10) != 0) 10 else 0
            }
        }
        if (ascii(raf.readAt(pos, 4), 0, 4) != "fLaC") throw UnsupportedTagException("Not a FLAC stream")
        val prefixEnd = pos
        pos += 4
        val blocks = ArrayList<Block>()
        while (true) {
            val h = raf.readAt(pos, 4)
            val last = (h[0].toInt() and 0x80) != 0
            val type = h[0].toInt() and 0x7f
            val len = be24(h, 1)
            if (type == 127) throw UnsupportedTagException("Invalid FLAC block")
            blocks.add(Block(type, raf.readAt(pos + 4, len)))
            pos += 4 + len
            if (last) break
        }
        if (blocks.isEmpty() || blocks[0].type != 0) throw UnsupportedTagException("FLAC STREAMINFO missing")
        return Layout(prefixEnd, blocks, pos)
    }

    private fun frontCover(blocks: List<Block>): FlacPicture.Picture? {
        val pictures = blocks.filter { it.type == 6 }.mapNotNull { FlacPicture.decode(it.data) }
        return pictures.firstOrNull { it.type == FlacPicture.TYPE_FRONT_COVER } ?: pictures.firstOrNull()
    }

    override fun read(file: File): AudioTags = RandomAccessFile(file, "r").use { raf ->
        val l = layout(raf)
        val vc = l.blocks.firstOrNull { it.type == 4 }
        val tags = if (vc == null) AudioTags() else VorbisComments.parse(vc.data).toTags()
        val cover = frontCover(l.blocks)
        tags.copy(artwork = cover?.data, artworkMime = cover?.mime)
    }

    override fun rewrite(source: File, target: File, edit: TagEdit) {
        RandomAccessFile(source, "r").use { raf ->
            val l = layout(raf)
            val existing = l.blocks.firstOrNull { it.type == 4 }
            val comments = if (existing != null) VorbisComments.parse(existing.data) else VorbisComments.empty()
            comments.apply(edit)
            val commentBytes = comments.toBytes()
            if (commentBytes.size >= (1 shl 24)) throw UnsupportedTagException("Tag too large for FLAC")

            val existingPictures = l.blocks.filter { it.type == 6 }
                .mapNotNull { FlacPicture.decode(it.data) }
            val newPictures: List<FlacPicture.Picture> = when {
                edit.artwork == null -> existingPictures
                edit.artwork.isEmpty() -> emptyList()
                else -> listOf(
                    FlacPicture.Picture(
                        FlacPicture.TYPE_FRONT_COVER,
                        edit.artworkMime ?: FlacPicture.detectMime(edit.artwork),
                        "",
                        edit.artwork
                    )
                ) + existingPictures.filter { it.type != FlacPicture.TYPE_FRONT_COVER }
            }

            val ordered = ArrayList<Block>()
            ordered.add(l.blocks[0])
            ordered.add(Block(4, commentBytes))
            l.blocks.drop(1).filter { it.type != 4 && it.type != 1 && it.type != 6 }.forEach { ordered.add(it) }
            for (picture in newPictures) {
                val encoded = FlacPicture.encode(picture.data, picture.mime, picture.type)
                if (encoded.size >= (1 shl 24)) throw UnsupportedTagException("Cover too large for FLAC")
                ordered.add(Block(6, encoded))
            }
            ordered.add(Block(1, ByteArray(1024))) // padding keeps future edits cheap for other tools

            BufferedOutputStream(target.outputStream(), COPY_BUFFER).use { out ->
                copyRange(raf, 0, l.prefixEnd + 4, out)
                ordered.forEachIndexed { i, b ->
                    val isLast = i == ordered.lastIndex
                    out.write((if (isLast) 0x80 else 0) or b.type)
                    out.write(byteArrayOf((b.data.size shr 16).toByte(), (b.data.size shr 8).toByte(), b.data.size.toByte()))
                    out.write(b.data)
                }
                copyRange(raf, l.audioStart, raf.length(), out)
            }
        }
    }
}
