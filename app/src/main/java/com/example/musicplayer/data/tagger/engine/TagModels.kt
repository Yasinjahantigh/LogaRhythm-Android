package com.example.musicplayer.data.tagger.engine

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Pure-JVM embedded tag engine. Nothing in this package touches Android APIs, so every format
 * is unit-testable on a desktop JVM and the same code path runs on the device.
 */

/** What a caller wants changed. `null` means "leave as it is"; a blank string means "remove". */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val lyrics: String? = null
) {
    val isEmpty: Boolean
        get() = title == null && artist == null && album == null && genre == null && lyrics == null
}

/** Values read back from a file. A field is null when the file has no such tag. */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val lyrics: String? = null
)

class UnsupportedTagException(message: String) : Exception(message)

interface TagEngine {
    /** Human-readable container name, e.g. "MP3 (ID3v2)". */
    val label: String

    fun read(file: File): AudioTags

    /**
     * Writes a complete copy of [source] to [target] with [edit] applied. Audio payload bytes are
     * copied untouched. Throws [UnsupportedTagException] when the file cannot be rewritten safely.
     */
    fun rewrite(source: File, target: File, edit: TagEdit)
}

/** Applies a [TagEdit] value to an existing one: null keeps, blank removes, anything else replaces. */
internal fun resolve(edit: String?, existing: String?): String? = when {
    edit == null -> existing
    edit.isBlank() -> null
    else -> edit
}

internal const val COPY_BUFFER = 64 * 1024

internal fun RandomAccessFile.readAt(pos: Long, length: Int): ByteArray {
    if (length < 0 || pos < 0 || pos + length > length()) throw UnsupportedTagException("Truncated file")
    val out = ByteArray(length)
    seek(pos)
    readFully(out)
    return out
}

internal fun copyRange(src: RandomAccessFile, from: Long, to: Long, out: OutputStream) {
    if (to <= from) return
    src.seek(from)
    val buf = ByteArray(COPY_BUFFER)
    var remaining = to - from
    while (remaining > 0) {
        val n = src.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
        if (n < 0) throw UnsupportedTagException("Unexpected end of file")
        out.write(buf, 0, n)
        remaining -= n
    }
}

internal fun InputStream.skipFully(count: Long) {
    var left = count
    while (left > 0) {
        val s = skip(left)
        if (s <= 0) { if (read() < 0) return else left-- } else left -= s
    }
}

internal fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)
internal fun be24(b: ByteArray, o: Int) = (be16(b, o) shl 8) or (b[o + 2].toInt() and 0xff)
internal fun be32(b: ByteArray, o: Int): Long =
    ((b[o].toLong() and 0xff) shl 24) or ((b[o + 1].toLong() and 0xff) shl 16) or
        ((b[o + 2].toLong() and 0xff) shl 8) or (b[o + 3].toLong() and 0xff)
internal fun be64(b: ByteArray, o: Int): Long = (be32(b, o) shl 32) or be32(b, o + 4)
internal fun le32(b: ByteArray, o: Int): Long =
    (b[o].toLong() and 0xff) or ((b[o + 1].toLong() and 0xff) shl 8) or
        ((b[o + 2].toLong() and 0xff) shl 16) or ((b[o + 3].toLong() and 0xff) shl 24)

internal fun putBe32(v: Long): ByteArray =
    byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
internal fun putBe64(v: Long): ByteArray = putBe32(v ushr 32) + putBe32(v and 0xffffffffL)
internal fun putLe32(v: Long): ByteArray =
    byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

internal fun ascii(b: ByteArray, o: Int, n: Int): String = String(b, o, n, Charsets.ISO_8859_1)

/** Vorbis-comment payload shared by FLAC and Ogg (Vorbis/Opus). */
internal class VorbisComments(var vendor: ByteArray, val entries: MutableList<Pair<String, String>>) {

    fun first(vararg keys: String): String? {
        for (k in keys) {
            entries.firstOrNull { it.first.equals(k, ignoreCase = true) }?.let { return it.second }
        }
        return null
    }

    fun removeKeys(vararg keys: String) {
        entries.removeAll { e -> keys.any { it.equals(e.first, ignoreCase = true) } }
    }

    fun toBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(putLe32(vendor.size.toLong())); out.write(vendor)
        out.write(putLe32(entries.size.toLong()))
        for ((k, v) in entries) {
            val bytes = "$k=$v".toByteArray(Charsets.UTF_8)
            out.write(putLe32(bytes.size.toLong())); out.write(bytes)
        }
        return out.toByteArray()
    }

    fun apply(edit: TagEdit) {
        fun set(keys: Array<String>, value: String?) {
            if (value == null) return
            removeKeys(*keys)
            if (value.isNotBlank()) entries.add(keys[0] to value)
        }
        set(arrayOf("TITLE"), edit.title)
        set(arrayOf("ARTIST"), edit.artist)
        set(arrayOf("ALBUM"), edit.album)
        set(arrayOf("GENRE"), edit.genre)
        set(arrayOf("LYRICS", "UNSYNCEDLYRICS"), edit.lyrics)
    }

    fun toTags() = AudioTags(
        title = first("TITLE"), artist = first("ARTIST"), album = first("ALBUM"),
        genre = first("GENRE"), lyrics = first("LYRICS", "UNSYNCEDLYRICS")
    )

    companion object {
        fun parse(data: ByteArray, start: Int = 0): VorbisComments {
            try {
                var p = start
                val vlen = le32(data, p).toInt(); p += 4
                if (vlen < 0 || p + vlen > data.size) throw UnsupportedTagException("Bad vendor string")
                val vendor = data.copyOfRange(p, p + vlen); p += vlen
                val count = le32(data, p).toInt(); p += 4
                val entries = ArrayList<Pair<String, String>>()
                repeat(count) {
                    val len = le32(data, p).toInt(); p += 4
                    if (len < 0 || p + len > data.size) throw UnsupportedTagException("Bad comment length")
                    val s = String(data, p, len, Charsets.UTF_8); p += len
                    val eq = s.indexOf('=')
                    if (eq > 0) entries.add(s.substring(0, eq) to s.substring(eq + 1))
                }
                return VorbisComments(vendor, entries)
            } catch (e: IndexOutOfBoundsException) {
                throw UnsupportedTagException("Malformed comment block")
            }
        }

        fun empty() = VorbisComments("LogaRhythm".toByteArray(), ArrayList())
    }
}
