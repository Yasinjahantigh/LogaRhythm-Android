package com.example.musicplayer.data.sources

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.example.musicplayer.domain.lyrics.LrcParser
import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.source.LyricsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and embeds lyrics in local audio files:
 * 1. Checks companion .lrc file in the same directory.
 * 2. Checks embedded audio metadata (ID3v2 USLT / SYLT / vorbis / mp4) via MediaMetadataRetriever.
 * 3. Supports embedding / saving lyrics into companion .lrc file and ID3 metadata tag when writable.
 */
@Singleton
class LocalFileLyricsSource @Inject constructor(
    @ApplicationContext private val context: Context
) : LyricsSource {

    override val name: String = "local_file"

    override suspend fun getLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val audioFilePath = resolveAudioFilePath(track)

        // 1. Check companion .lrc file next to audio file
        if (audioFilePath != null) {
            val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
            val lrcFile = File(lrcPath)
            if (lrcFile.exists() && lrcFile.canRead()) {
                try {
                    val content = decodeLrcFileContent(lrcFile)
                    val parsed = LrcParser.parse(content)
                    if (!parsed.isEmpty) return@withContext parsed
                } catch (_: Exception) {
                }
            }
        }

        // 2. Check embedded audio tags via MediaMetadataRetriever
        val embedded = extractEmbeddedLyrics(track, audioFilePath)
        if (embedded != null && !embedded.isEmpty) {
            return@withContext embedded
        }

        null
    }

    private fun extractEmbeddedLyrics(track: Track, filePath: String?): Lyrics? {
        val retriever = MediaMetadataRetriever()
        try {
            if (filePath != null && File(filePath).canRead()) {
                retriever.setDataSource(filePath)
            } else if (track.uri.isNotBlank()) {
                retriever.setDataSource(context, Uri.parse(track.uri))
            } else {
                return null
            }

            // Android MediaMetadataRetriever extracts ID3 USLT / unsynced lyrics via keyCode 37 (METADATA_KEY_LYRICS on API 34+)
            val lyricsText = if (android.os.Build.VERSION.SDK_INT >= 34) {
                try {
                    retriever.extractMetadata(37)
                } catch (_: Exception) {
                    null
                }
            } else null

            if (!lyricsText.isNullOrBlank()) {
                val parsed = LrcParser.parse(lyricsText)
                if (!parsed.isEmpty) return parsed
            }

            // Direct ID3v2 USLT tag scan fallback if file is accessible
            if (filePath != null) {
                val id3Lyrics = extractId3LyricsFromFile(File(filePath))
                if (!id3Lyrics.isNullOrBlank()) {
                    val parsed = LrcParser.parse(id3Lyrics)
                    if (!parsed.isEmpty) return parsed
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Inspects ID3v2 header and USLT (Unsynchronized lyrics/text transcription) or SYLT frame directly
     */
    private fun extractId3LyricsFromFile(file: File): String? {
        if (!file.exists() || !file.canRead() || file.length() < 10) return null
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }
                val version = header[3].toInt()
                if (version < 3 || version > 4) return null // ID3v2.3 or ID3v2.4

                val tagSize = (header[6].toInt() and 0x7F shl 21) or
                        (header[7].toInt() and 0x7F shl 14) or
                        (header[8].toInt() and 0x7F shl 7) or
                        (header[9].toInt() and 0x7F)

                val tagData = ByteArray(tagSize.coerceAtMost(512 * 1024)) // Read up to 512KB
                raf.readFully(tagData)

                // Search for USLT frame id
                val usltMarker = byteArrayOf('U'.code.toByte(), 'S'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
                val index = indexOfBytes(tagData, usltMarker)
                if (index != -1 && index + 10 < tagData.size) {
                    val frameSize = if (version == 4) {
                        (tagData[index + 4].toInt() and 0x7F shl 21) or
                                (tagData[index + 5].toInt() and 0x7F shl 14) or
                                (tagData[index + 6].toInt() and 0x7F shl 7) or
                                (tagData[index + 7].toInt() and 0x7F)
                    } else {
                        (tagData[index + 4].toInt() and 0xFF shl 24) or
                                (tagData[index + 5].toInt() and 0xFF shl 16) or
                                (tagData[index + 6].toInt() and 0xFF shl 8) or
                                (tagData[index + 7].toInt() and 0xFF)
                    }
                    if (frameSize > 4 && index + 10 + frameSize <= tagData.size) {
                        val frameContent = tagData.copyOfRange(index + 10, index + 10 + frameSize)
                        val decoded = decodeUsltFrame(frameContent)
                        if (!decoded.isNullOrBlank()) return decoded
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun decodeUsltFrame(frameContent: ByteArray): String? {
        if (frameContent.size < 5) return null
        val encodingByte = frameContent[0].toInt() and 0xFF
        val isUtf16 = (encodingByte == 1 || encodingByte == 2)
        var textStart = 4 // skip encoding (1 byte) + language (3 bytes)

        if (isUtf16) {
            // In UTF-16, descriptor is null-terminated by 2 zero bytes: 0x00 0x00 (aligned to 2-byte boundary)
            while (textStart + 1 < frameContent.size) {
                if (frameContent[textStart] == 0.toByte() && frameContent[textStart + 1] == 0.toByte()) {
                    textStart += 2
                    break
                }
                textStart += 2
            }
        } else {
            // In UTF-8 / ISO-8859-1, descriptor is null-terminated by 1 zero byte: 0x00
            while (textStart < frameContent.size) {
                if (frameContent[textStart] == 0.toByte()) {
                    textStart++
                    break
                }
                textStart++
            }
        }

        if (textStart >= frameContent.size) return null
        val rawLyricsBytes = frameContent.copyOfRange(textStart, frameContent.size)
        return decodeLyricsBytes(rawLyricsBytes, encodingByte)
    }

    private fun decodeLyricsBytes(bytes: ByteArray, encodingByte: Int): String {
        if (bytes.isEmpty()) return ""
        val decoded = when (encodingByte) {
            1 -> {
                // UTF-16 with BOM
                if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                    String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
                } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                    String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
                } else {
                    String(bytes, Charsets.UTF_16)
                }
            }
            2 -> String(bytes, Charsets.UTF_16BE)
            3 -> String(bytes, Charsets.UTF_8)
            else -> {
                // Nominally ISO-8859-1, but many Persian/Arabic taggers write Windows-1256 or UTF-8
                val utf8 = tryDecode(bytes, Charsets.UTF_8)
                if (utf8 != null && !utf8.contains('\uFFFD') && hasNonAscii(bytes)) {
                    utf8
                } else {
                    try {
                        val w1256 = String(bytes, java.nio.charset.Charset.forName("windows-1256"))
                        if (containsPersianArabicChars(w1256)) {
                            w1256
                        } else {
                            String(bytes, Charsets.ISO_8859_1)
                        }
                    } catch (_: Exception) {
                        String(bytes, Charsets.ISO_8859_1)
                    }
                }
            }
        }

        val cleaned = decoded.replace("\u0000", "").trim()
        if (cleaned.contains('\uFFFD') || (cleaned.length > 4 && cleaned.contains('?'))) {
            // Fallback trial 1: UTF-16LE without BOM
            val utf16Le = tryDecode(bytes, Charsets.UTF_16LE)
            if (utf16Le != null && !utf16Le.contains('\uFFFD') && utf16Le.length > 5) {
                return utf16Le.replace("\u0000", "").trim()
            }
            // Fallback trial 2: Windows-1256
            try {
                val w1256 = String(bytes, java.nio.charset.Charset.forName("windows-1256"))
                if (containsPersianArabicChars(w1256)) {
                    return w1256.replace("\u0000", "").trim()
                }
            } catch (_: Exception) {
            }
        }

        return cleaned
    }

    private fun decodeLrcFileContent(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""
        // Check BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) {
            return utf8
        }
        return try {
            val w1256 = String(bytes, java.nio.charset.Charset.forName("windows-1256"))
            if (containsPersianArabicChars(w1256)) w1256 else utf8
        } catch (_: Exception) {
            utf8
        }
    }

    private fun tryDecode(bytes: ByteArray, charset: java.nio.charset.Charset): String? {
        return try {
            String(bytes, charset)
        } catch (_: Exception) {
            null
        }
    }

    private fun hasNonAscii(bytes: ByteArray): Boolean {
        for (b in bytes) {
            if ((b.toInt() and 0x80) != 0) return true
        }
        return false
    }

    private fun containsPersianArabicChars(text: String): Boolean {
        for (ch in text) {
            if (ch in '\u0600'..'\u06FF' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF') {
                return true
            }
        }
        return false
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        val max = source.size - target.size
        for (i in 0..max) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    /**
     * Embeds lyrics by writing companion .lrc file in the music file directory.
     * If directory is read-only, returns false.
     */
    suspend fun saveCompanionLrc(track: Track, content: String): Boolean = withContext(Dispatchers.IO) {
        val audioFilePath = resolveAudioFilePath(track) ?: return@withContext false
        val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
        try {
            val file = File(lrcPath)
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes the companion .lrc file next to the audio file if it exists.
     */
    suspend fun deleteCompanionLrc(track: Track): Boolean = withContext(Dispatchers.IO) {
        val audioFilePath = resolveAudioFilePath(track) ?: return@withContext false
        val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
        try {
            val file = File(lrcPath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getAudioFilePath(track: Track): String? = resolveAudioFilePath(track)

    private fun resolveAudioFilePath(track: Track): String? {
        if (!track.id.startsWith("local:")) return null
        val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return null

        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(mediaStoreId.toString())

        try {
            val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (dataCol != -1 && cursor.moveToFirst()) {
                    return cursor.getString(dataCol)
                }
            }
        } catch (_: Exception) {
        }

        return null
    }
}
