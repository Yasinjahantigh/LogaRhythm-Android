package com.example.musicplayer.data.tagger

/**
 * Minimal ID3v2.3 USLT payload codec. The ID3 frame header is intentionally handled by
 * AudioFileTagWriter; this object only encodes/decodes the frame body and is JVM-testable.
 */
internal object Id3UsltCodec {
    fun encodePayload(content: String): ByteArray {
        val text = content.replace("\r\n", "\n")
        return java.io.ByteArrayOutputStream().apply {
            write(1) // ID3v2.3 encoding: UTF-16 with BOM
            write(byteArrayOf('e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte()))
            write(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) // BOM for descriptor string
            write(byteArrayOf(0, 0)) // empty descriptor terminator
            write(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) // BOM for lyrics string
            write(text.toByteArray(Charsets.UTF_16LE))
        }.toByteArray()
    }

    /** Returns null for malformed/unsupported USLT payloads instead of guessing at bytes. */
    fun decodePayload(payload: ByteArray): String? {
        if (payload.size < 6) return null
        val encoding = payload[0].toInt() and 0xff
        if (encoding !in 0..3) return null
        var textStart = 4 // encoding byte + 3-byte language
        if (encoding == 1 || encoding == 2) {
            // Encoding 1 strings may begin with a BOM; the descriptor terminator is still
            // aligned to 16-bit boundaries relative to the descriptor's first code unit.
            if (encoding == 1 && textStart + 1 < payload.size &&
                ((payload[textStart] == 0xFF.toByte() && payload[textStart + 1] == 0xFE.toByte()) ||
                    (payload[textStart] == 0xFE.toByte() && payload[textStart + 1] == 0xFF.toByte()))
            ) textStart += 2
            var terminatorFound = false
            while (textStart + 1 < payload.size) {
                if (payload[textStart] == 0.toByte() && payload[textStart + 1] == 0.toByte()) {
                    textStart += 2
                    terminatorFound = true
                    break
                }
                textStart += 2
            }
            if (!terminatorFound) return null
        } else {
            while (textStart < payload.size && payload[textStart] != 0.toByte()) textStart++
            if (textStart >= payload.size) return null
            textStart++
        }
        if (textStart > payload.size) return null
        var bytes = payload.copyOfRange(textStart, payload.size)
        val decoded = when (encoding) {
            0 -> String(bytes, Charsets.ISO_8859_1)
            1 -> {
                if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                    bytes = bytes.copyOfRange(2, bytes.size)
                    String(bytes, Charsets.UTF_16LE)
                } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                    bytes = bytes.copyOfRange(2, bytes.size)
                    String(bytes, Charsets.UTF_16BE)
                } else return null
            }
            2 -> String(bytes, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }
        return decoded.trimEnd('\u0000', '\uFEFF').takeIf { it.isNotBlank() }
    }
}
