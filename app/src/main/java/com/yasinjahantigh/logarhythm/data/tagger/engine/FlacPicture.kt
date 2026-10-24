package com.yasinjahantigh.logarhythm.data.tagger.engine

import java.io.ByteArrayOutputStream

/**
 * FLAC PICTURE metadata block (type 6). The same structure is base64-encoded inside an Ogg
 * `METADATA_BLOCK_PICTURE` comment.
 */
object FlacPicture {
    const val TYPE_FRONT_COVER = 3

    class Picture(
        val type: Int,
        val mime: String,
        val description: String,
        val data: ByteArray
    )

    fun detectMime(image: ByteArray): String = when {
        image.size > 8 && (image[0].toInt() and 0xff) == 0x89 && image[1].toInt() == 'P'.code -> "image/png"
        image.size > 3 && (image[0].toInt() and 0xff) == 0xff && (image[1].toInt() and 0xff) == 0xd8 -> "image/jpeg"
        image.size > 12 && ascii(image, 0, 4) == "RIFF" && ascii(image, 8, 4) == "WEBP" -> "image/webp"
        else -> "image/jpeg"
    }

    fun encode(image: ByteArray, mime: String?, type: Int = TYPE_FRONT_COVER): ByteArray {
        val mimeBytes = (mime?.takeIf { it.isNotBlank() } ?: detectMime(image)).toByteArray(Charsets.US_ASCII)
        val (width, height) = dimensions(image)
        val out = ByteArrayOutputStream()
        out.write(putBe32(type.toLong()))
        out.write(putBe32(mimeBytes.size.toLong())); out.write(mimeBytes)
        out.write(putBe32(0)) // empty description
        out.write(putBe32(width.toLong()))
        out.write(putBe32(height.toLong()))
        out.write(putBe32(24))
        out.write(putBe32(0))
        out.write(putBe32(image.size.toLong())); out.write(image)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Picture? {
        try {
            var p = 0
            val type = be32(bytes, p).toInt(); p += 4
            val mimeLen = be32(bytes, p).toInt(); p += 4
            if (mimeLen < 0 || p + mimeLen > bytes.size) return null
            val mime = ascii(bytes, p, mimeLen); p += mimeLen
            val descLen = be32(bytes, p).toInt(); p += 4
            if (descLen < 0 || p + descLen > bytes.size) return null
            val description = String(bytes, p, descLen, Charsets.UTF_8); p += descLen
            p += 16 // width, height, depth, colors
            if (p + 4 > bytes.size) return null
            val dataLen = be32(bytes, p).toInt(); p += 4
            if (dataLen < 0 || p + dataLen > bytes.size) return null
            return Picture(type, mime, description, bytes.copyOfRange(p, p + dataLen))
        } catch (_: Exception) {
            return null
        }
    }

    /** Minimal JPEG/PNG size probe so other players do not have to guess. */
    private fun dimensions(image: ByteArray): Pair<Int, Int> {
        try {
            if (image.size > 24 && (image[0].toInt() and 0xff) == 0x89) {
                return (be32(image, 16).toInt() to be32(image, 20).toInt())
            }
            if (image.size > 4 && (image[0].toInt() and 0xff) == 0xff) {
                var p = 2
                while (p + 9 < image.size) {
                    if ((image[p].toInt() and 0xff) != 0xff) { p++; continue }
                    val marker = image[p + 1].toInt() and 0xff
                    if (marker in setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                        val height = be16(image, p + 5)
                        val width = be16(image, p + 7)
                        return width to height
                    }
                    val length = be16(image, p + 2)
                    if (length < 2) break
                    p += 2 + length
                }
            }
        } catch (_: Exception) {
            // Unknown dimensions are allowed by the FLAC spec.
        }
        return 0 to 0
    }
}
