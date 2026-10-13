package com.example.musicplayer.data.tagger.engine

import java.io.File
import java.io.RandomAccessFile

/** Chooses an engine by sniffing the file header; file extensions are not trusted. */
object TagEngines {

    fun detect(file: File): TagEngine? {
        val head = ByteArray(12)
        val n = RandomAccessFile(file, "r").use { it.read(head) }
        if (n < 4) return null
        return when {
            ascii(head, 0, 3) == "ID3" -> id3OrFlac(file, head)
            ascii(head, 0, 4) == "fLaC" -> FlacEngine
            ascii(head, 0, 4) == "OggS" -> OggEngine
            n >= 8 && ascii(head, 4, 4) == "ftyp" -> Mp4Engine
            (head[0].toInt() and 0xff) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0 -> Id3Engine // bare MPEG audio
            else -> null
        }
    }

    /** An ID3v2 block can precede a FLAC stream; look past it. */
    private fun id3OrFlac(file: File, head: ByteArray): TagEngine {
        val size = ((head[6].toInt() and 0x7f) shl 21) or ((head[7].toInt() and 0x7f) shl 14) or
            ((head[8].toInt() and 0x7f) shl 7) or (head[9].toInt() and 0x7f)
        val after = 10L + size
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (after + 4 <= raf.length() && ascii(raf.readAt(after, 4), 0, 4) == "fLaC") FlacEngine else Id3Engine
            }
        } catch (_: Exception) { Id3Engine }
    }

    val supportedLabels: List<String> = listOf("MP3", "FLAC", "M4A / MP4", "OGG / OPUS")
}
