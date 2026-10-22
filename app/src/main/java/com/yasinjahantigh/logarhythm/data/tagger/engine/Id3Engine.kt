package com.yasinjahantigh.logarhythm.data.tagger.engine

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * ID3v2.2 / v2.3 / v2.4 reader and rewriter for MP3 files. Frames we do not edit (artwork,
 * comments, ratings, ...) are carried over byte-for-byte; v2.2 tags are upgraded to v2.3.
 */
object Id3Engine : TagEngine {
    private const val MAX_TAG = 32 * 1024 * 1024
    private const val PADDING = 256

    private class Frame(val id: String, val flags: Int, val payload: ByteArray)

    private class Parsed(
        val major: Int,
        val frames: MutableList<Frame>,
        /** File offset where the audio stream begins. */
        val audioStart: Long
    )

    // ---- reading -------------------------------------------------------------------------

    override fun read(file: File): AudioTags {
        RandomAccessFile(file, "r").use { raf ->
            val parsed = parse(raf)
            fun text(vararg ids: String): String? =
                ids.firstNotNullOfOrNull { id -> parsed.frames.firstOrNull { it.id == id } }
                    ?.let { decodeText(it.payload) }
            val lyrics = parsed.frames.firstOrNull { it.id == "USLT" }?.let { decodeUslt(it.payload) }
            return AudioTags(
                title = text("TIT2"), artist = text("TPE1"), album = text("TALB"),
                genre = text("TCON"), lyrics = lyrics, year = text("TDRC", "TYER"),
                trackNumber = text("TRCK")?.substringBefore('/')?.trim()?.toIntOrNull(),
                discNumber = text("TPOS")?.substringBefore('/')?.trim()?.toIntOrNull(),
                composer = text("TCOM")
            )
        }
    }

    private fun parse(raf: RandomAccessFile): Parsed {
        if (raf.length() < 10) return Parsed(3, ArrayList(), 0)
        val head = raf.readAt(0, 10)
        if (ascii(head, 0, 3) != "ID3") return Parsed(3, ArrayList(), 0)
        val major = head[3].toInt() and 0xff
        if (major !in 2..4) throw UnsupportedTagException("ID3v2.$major is not supported")
        val flags = head[5].toInt() and 0xff
        val size = syncsafe(head, 6)
        if (size < 0) throw UnsupportedTagException("Corrupt ID3 header")
        if (size > MAX_TAG || 10L + size > raf.length()) throw UnsupportedTagException("ID3 tag is truncated or too large")
        val hasFooter = major == 4 && (flags and 0x10) != 0
        val audioStart = 10L + size + if (hasFooter) 10 else 0
        var body = raf.readAt(10, size)
        val tagUnsync = (flags and 0x80) != 0
        if (major == 2 && (flags and 0x40) != 0) throw UnsupportedTagException("Compressed ID3v2.2 tags are not supported")
        if (tagUnsync && major != 4) body = deunsync(body)

        var pos = 0
        if (major >= 3 && (flags and 0x40) != 0) {
            // Extended header: skipped (and dropped on rewrite).
            if (body.size < 4) throw UnsupportedTagException("Corrupt extended header")
            val extSize = if (major == 4) syncsafe(body, 0) else be32(body, 0).toInt() + 4
            if (extSize < 4 || extSize > body.size) throw UnsupportedTagException("Corrupt extended header")
            pos = extSize
        }

        val frames = ArrayList<Frame>()
        val idLen = if (major == 2) 3 else 4
        val hdr = if (major == 2) 6 else 10
        while (pos + hdr <= body.size) {
            if (body[pos].toInt() == 0) break // padding
            val id = ascii(body, pos, idLen)
            if (!validId(id)) break // treat the rest as padding/junk
            var fsize: Int
            var fflags = 0
            when (major) {
                2 -> fsize = be24(body, pos + 3)
                3 -> { fsize = be32(body, pos + 4).toInt(); fflags = be16(body, pos + 8) }
                else -> {
                    val ss = syncsafe(body, pos + 4)
                    val plain = be32(body, pos + 4).toInt()
                    // Some taggers wrote plain (non-syncsafe) sizes into v2.4 tags.
                    fsize = if (ss >= 0 && nextLooksValid(body, pos + 10 + ss, idLen, hdr)) ss
                    else if (plain >= 0 && nextLooksValid(body, pos + 10 + plain, idLen, hdr)) plain
                    else ss
                    fflags = be16(body, pos + 8)
                }
            }
            if (fsize < 0 || pos + hdr + fsize > body.size) break
            var payload = body.copyOfRange(pos + hdr, pos + hdr + fsize)
            var outFlags = fflags
            if (major == 4 && (tagUnsync || (fflags and 0x0002) != 0 || (fflags and 0x0001) != 0)) {
                // Normalise v2.4 per-frame unsync / data-length-indicator so frames stay valid
                // after we clear the tag-level unsynchronisation flag.
                if ((fflags and 0x000C) != 0) {
                    if (tagUnsync) throw UnsupportedTagException("Encrypted or compressed frames in an unsynchronised tag")
                } else {
                    if ((fflags and 0x0001) != 0 && payload.size >= 4) payload = payload.copyOfRange(4, payload.size)
                    if (tagUnsync || (fflags and 0x0002) != 0) payload = deunsync(payload)
                    outFlags = fflags and 0x0003.inv()
                }
            }
            frames.add(Frame(id, outFlags, payload))
            pos += hdr + fsize
        }
        val outMajor = if (major == 2) 3 else major
        val converted = if (major == 2) frames.mapNotNull { upgradeV22(it) }.toMutableList() else frames
        return Parsed(outMajor, converted, audioStart)
    }

    private fun nextLooksValid(body: ByteArray, at: Int, idLen: Int, hdr: Int): Boolean {
        if (at == body.size) return true
        if (at < 0 || at + hdr > body.size) return false
        if (body[at].toInt() == 0) return true
        return validId(ascii(body, at, idLen))
    }

    private fun validId(id: String) = id.isNotEmpty() && id.all { it in 'A'..'Z' || it in '0'..'9' }

    private val V22_TO_V23 = mapOf(
        "TT2" to "TIT2", "TP1" to "TPE1", "TP2" to "TPE2", "TAL" to "TALB", "TCO" to "TCON",
        "TYE" to "TYER", "TRK" to "TRCK", "TPA" to "TPOS", "TCM" to "TCOM", "TT1" to "TIT1",
        "TT3" to "TIT3", "TEN" to "TENC", "TCR" to "TCOP", "TPB" to "TPUB", "TBP" to "TBPM",
        "TXT" to "TEXT", "TLA" to "TLAN", "TKE" to "TKEY", "TLE" to "TLEN", "TSS" to "TSSE",
        "TXX" to "TXXX", "WXX" to "WXXX", "COM" to "COMM", "ULT" to "USLT", "UFI" to "UFID",
        "TDA" to "TDAT", "TIM" to "TIME", "TRD" to "TRDA", "TOA" to "TOPE", "TOT" to "TOAL",
        "TOL" to "TOLY", "TOR" to "TORY", "TOF" to "TOFN", "TSI" to "TSIZ", "TRC" to "TSRC",
        "TMT" to "TMED", "TFT" to "TFLT", "TDY" to "TDLY", "POP" to "POPM", "CNT" to "PCNT",
        "WAR" to "WOAR", "WAF" to "WOAF", "WAS" to "WOAS", "WCM" to "WCOM", "WCP" to "WCOP",
        "WPB" to "WPUB"
    )

    private fun upgradeV22(f: Frame): Frame? {
        if (f.id == "PIC") {
            // PIC: enc, 3-char image format, type, description, data -> APIC
            val p = f.payload
            if (p.size < 6) return null
            val enc = p[0].toInt() and 0xff
            val fmt = ascii(p, 1, 3).uppercase()
            val mime = when (fmt) { "PNG" -> "image/png"; "JPG" -> "image/jpeg"; else -> "image/" + fmt.lowercase() }
            val out = ByteArrayOutputStream()
            out.write(enc); out.write(mime.toByteArray(Charsets.ISO_8859_1)); out.write(0)
            out.write(p, 4, p.size - 4)
            return Frame("APIC", 0, out.toByteArray())
        }
        val newId = V22_TO_V23[f.id] ?: return null
        return Frame(newId, 0, f.payload)
    }

    // ---- rewriting ------------------------------------------------------------------------

    override fun rewrite(source: File, target: File, edit: TagEdit) {
        RandomAccessFile(source, "r").use { raf ->
            val parsed = parse(raf)
            val major = parsed.major
            val frames = parsed.frames

            fun replaceText(id: String, value: String?) {
                if (value == null) return
                frames.removeAll { it.id == id }
                if (value.isNotBlank()) frames.add(0, Frame(id, 0, encodeText(value, major)))
            }
            replaceText("TIT2", edit.title)
            replaceText("TPE1", edit.artist)
            replaceText("TALB", edit.album)
            replaceText("TCON", edit.genre)
            if (edit.lyrics != null) {
                frames.removeAll { it.id == "USLT" }
                if (edit.lyrics.isNotBlank()) frames.add(Frame("USLT", 0, encodeUslt(edit.lyrics, major)))
            }
            if (edit.artwork != null) {
                frames.removeAll { it.id == "APIC" }
                if (edit.artwork.isNotEmpty()) frames.add(0, Frame("APIC", 0, encodeApic(edit.artwork)))
            }
            if (edit.year != null) {
                frames.removeAll { it.id == "TDRC" || it.id == "TYER" }
                if (edit.year.isNotBlank()) {
                    frames.add(0, Frame(if (major == 4) "TDRC" else "TYER", 0, encodeText(edit.year, major)))
                }
            }
            edit.trackNumber?.let { replaceText("TRCK", it.takeIf { number -> number > 0 }?.toString()) }
            edit.discNumber?.let { replaceText("TPOS", it.takeIf { number -> number > 0 }?.toString()) }
            replaceText("TCOM", edit.composer)

            val framesBytes = ByteArrayOutputStream()
            for (f in frames) {
                framesBytes.write(f.id.toByteArray(Charsets.ISO_8859_1))
                framesBytes.write(if (major == 4) putSyncsafe(f.payload.size) else putBe32(f.payload.size.toLong()))
                framesBytes.write(byteArrayOf((f.flags shr 8).toByte(), f.flags.toByte()))
                framesBytes.write(f.payload)
            }
            val tagSize = framesBytes.size() + PADDING
            if (tagSize > 0x0FFFFFFF) throw UnsupportedTagException("Tag too large")

            BufferedOutputStream(target.outputStream(), COPY_BUFFER).use { out ->
                out.write("ID3".toByteArray(Charsets.ISO_8859_1))
                out.write(major); out.write(0); out.write(0)
                out.write(putSyncsafe(tagSize))
                framesBytes.writeTo(out)
                out.write(ByteArray(PADDING))
                copyRange(raf, parsed.audioStart, raf.length(), out)
            }
        }
    }

    // ---- text helpers -----------------------------------------------------------------------

    internal fun encodeText(text: String, major: Int): ByteArray {
        val out = ByteArrayOutputStream()
        when {
            text.all { it.code <= 0xFF } -> { out.write(0); out.write(text.toByteArray(Charsets.ISO_8859_1)) }
            major == 4 -> { out.write(3); out.write(text.toByteArray(Charsets.UTF_8)) }
            else -> {
                out.write(1); out.write(0xFF); out.write(0xFE)
                out.write(text.toByteArray(Charsets.UTF_16LE))
            }
        }
        return out.toByteArray()
    }

    internal fun encodeUslt(lyrics: String, major: Int): ByteArray {
        val text = lyrics.replace("\r\n", "\n")
        val out = ByteArrayOutputStream()
        val lang = "eng".toByteArray(Charsets.ISO_8859_1)
        if (major == 4) {
            out.write(3); out.write(lang); out.write(0); out.write(text.toByteArray(Charsets.UTF_8))
        } else {
            out.write(1); out.write(lang)
            out.write(0xFF); out.write(0xFE); out.write(0); out.write(0) // empty descriptor
            out.write(0xFF); out.write(0xFE); out.write(text.toByteArray(Charsets.UTF_16LE))
        }
        return out.toByteArray()
    }

    internal fun decodeText(p: ByteArray): String? {
        if (p.isEmpty()) return null
        val s = decodeString(p, 1, p.size, p[0].toInt() and 0xff) ?: return null
        return s.split('\u0000').firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }
    }

    internal fun encodeApic(image: ByteArray): ByteArray {
        val mime = if (image.size > 8 && (image[0].toInt() and 0xff) == 0x89 &&
            image[1].toInt() == 'P'.code
        ) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream()
        out.write(0) // ISO-8859-1 description
        out.write(mime.toByteArray(Charsets.ISO_8859_1)); out.write(0)
        out.write(3) // front cover
        out.write(0) // empty description
        out.write(image)
        return out.toByteArray()
    }

    internal fun decodeUslt(p: ByteArray): String? {
        if (p.size < 5) return null
        val enc = p[0].toInt() and 0xff
        var pos = 4
        val wide = enc == 1 || enc == 2
        if (wide) {
            while (pos + 1 < p.size && !(p[pos].toInt() == 0 && p[pos + 1].toInt() == 0)) pos += 2
            pos += 2
        } else {
            while (pos < p.size && p[pos].toInt() != 0) pos++
            pos += 1
        }
        if (pos > p.size) return null
        return decodeString(p, pos, p.size, enc)?.trimEnd('\u0000')?.takeIf { it.isNotBlank() }
    }

    private fun decodeString(p: ByteArray, from: Int, to: Int, enc: Int): String? {
        if (from > to) return null
        val len = to - from
        return when (enc) {
            0 -> String(p, from, len, Charsets.ISO_8859_1)
            1 -> {
                if (len >= 2 && (p[from].toInt() and 0xff) == 0xFE && (p[from + 1].toInt() and 0xff) == 0xFF)
                    String(p, from + 2, len - 2, Charsets.UTF_16BE)
                else if (len >= 2 && (p[from].toInt() and 0xff) == 0xFF && (p[from + 1].toInt() and 0xff) == 0xFE)
                    String(p, from + 2, len - 2, Charsets.UTF_16LE)
                else String(p, from, len, Charsets.UTF_16LE)
            }
            2 -> String(p, from, len, Charsets.UTF_16BE)
            3 -> String(p, from, len, Charsets.UTF_8)
            else -> null
        }
    }

    // ---- low level ----------------------------------------------------------------------------

    private fun syncsafe(b: ByteArray, o: Int): Int {
        if ((0..3).any { (b[o + it].toInt() and 0x80) != 0 }) return -1
        return ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or
            ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)
    }

    private fun putSyncsafe(v: Int) = byteArrayOf(
        ((v shr 21) and 0x7f).toByte(), ((v shr 14) and 0x7f).toByte(),
        ((v shr 7) and 0x7f).toByte(), (v and 0x7f).toByte()
    )

    private fun deunsync(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if ((b[i].toInt() and 0xff) == 0xFF && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }
}
