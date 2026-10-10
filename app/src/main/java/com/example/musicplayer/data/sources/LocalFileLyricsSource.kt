package com.example.musicplayer.data.sources

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.musicplayer.domain.lyrics.LrcParser
import com.example.musicplayer.domain.model.Lyrics
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.source.LyricsSource
import com.example.musicplayer.data.tagger.AudioFileTagWriter
import com.example.musicplayer.data.tagger.Id3UsltCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and embeds lyrics in local audio files:
 * 1. Checks companion .lrc file in the same directory.
 * 2. Parses a structured ID3v2 USLT frame from the audio file.
 * 3. Writes embedded lyrics only to supported MP3 ID3 tags; companion .lrc is a separate fallback.
 */
@Singleton
class LocalFileLyricsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileTagWriter: AudioFileTagWriter
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
                    if (!parsed.isEmpty) return@withContext parsed.copy(source = "companion")
                } catch (_: Exception) {
                }
            }
        }

        // 2. Check embedded ID3 lyrics directly from the file/URI stream.
        val embedded = extractEmbeddedLyrics(track, audioFilePath)
        if (embedded != null && !embedded.isEmpty) {
            return@withContext embedded.copy(source = "embedded")
        }

        null
    }

    private fun extractEmbeddedLyrics(track: Track, filePath: String?): Lyrics? {
        // Read only the ID3 header and tag payload. Never stage/copy the full song just to
        // find lyrics; large music files should not cause large transient cache copies.
        return try {
            val raw = filePath?.let(::File)?.takeIf { it.isFile && it.canRead() }?.let { file ->
                FileInputStream(file).use(::extractId3LyricsFromStream)
            } ?: run {
                if (!track.uri.startsWith("content://", ignoreCase = true)) return null
                context.contentResolver.openInputStream(Uri.parse(track.uri))?.use(::extractId3LyricsFromStream)
            }
            val parsed = raw?.let(LrcParser::parse) ?: return null
            parsed.takeUnless { it.isEmpty }
        } catch (_: Exception) {
            null
        }
    }

    /** Parse a real ID3 frame table; never search artwork/audio bytes for the string "USLT". */
    private fun extractId3LyricsFromStream(input: InputStream): String? {
        return try {
            DataInputStream(input).use { stream ->
                val header = ByteArray(10)
                stream.readFully(header)
                if (String(header, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
                val version = header[3].toInt() and 0xff
                val flags = header[5].toInt() and 0xff
                if (version !in 3..4) return null
                // Global unsynchronisation and extended headers need transforms we do not
                // implement. A false negative is safer than decoding misaligned bytes.
                if ((version == 3 && flags and 0xE0 != 0) || (version == 4 && flags and 0xD0 != 0)) return null
                if ((6..9).any { (header[it].toInt() and 0x80) != 0 }) return null
                val tagSize = ((header[6].toInt() and 0x7f) shl 21) or ((header[7].toInt() and 0x7f) shl 14) or
                    ((header[8].toInt() and 0x7f) shl 7) or (header[9].toInt() and 0x7f)
                if (tagSize !in 1..(8 * 1024 * 1024)) return null
                val tag = ByteArray(tagSize)
                stream.readFully(tag) // Truncated tags are rejected by EOFException.
                var offset = 0
                while (offset + 10 <= tag.size) {
                    if (tag[offset] == 0.toByte()) return null // padding / malformed tail
                    val id = String(tag, offset, 4, Charsets.ISO_8859_1)
                    if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return null
                    val frameSize = if (version == 4) {
                        if ((4..7).any { (tag[offset + it].toInt() and 0x80) != 0 }) return null
                        ((tag[offset + 4].toInt() and 0x7f) shl 21) or ((tag[offset + 5].toInt() and 0x7f) shl 14) or
                            ((tag[offset + 6].toInt() and 0x7f) shl 7) or (tag[offset + 7].toInt() and 0x7f)
                    } else {
                        ((tag[offset + 4].toInt() and 0xff) shl 24) or ((tag[offset + 5].toInt() and 0xff) shl 16) or
                            ((tag[offset + 6].toInt() and 0xff) shl 8) or (tag[offset + 7].toInt() and 0xff)
                    }
                    if (frameSize < 0) return null
                    val frameStart = offset + 10
                    val frameEndLong = frameStart.toLong() + frameSize.toLong()
                    if (frameEndLong > tag.size.toLong()) return null
                    val frameEnd = frameEndLong.toInt()
                    if (id == "USLT") {
                        val statusFlags = tag[offset + 8].toInt() and 0xff
                        val formatFlags = tag[offset + 9].toInt() and 0xff
                        // We only decode plain USLT payloads. Preserve valid status semantics,
                        // but reject reserved status bits and every format flag (compression,
                        // encryption, grouping, per-frame unsync, or a data-length indicator).
                        val reservedStatusMask = if (version == 3) 0x1F else 0x8F
                        if ((statusFlags and reservedStatusMask) != 0 || formatFlags != 0) return null
                        return Id3UsltCodec.decodePayload(tag.copyOfRange(frameStart, frameEnd))
                    }
                    offset = frameEnd
                }
                null
            }
        } catch (_: Exception) {
            null
        }
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

    private fun containsPersianArabicChars(text: String): Boolean {
        for (ch in text) {
            if (ch in '\u0600'..'\u06FF' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF') {
                return true
            }
        }
        return false
    }

    /** Writes lyrics into the MP3's ID3 USLT frame. Companion files are a separate fallback. */
    suspend fun saveEmbeddedLyrics(track: Track, content: String): Boolean =
        audioFileTagWriter.writeEmbeddedLyrics(track, content)

    /** Writes a sibling .lrc file. This is not embedded audio metadata. */
    suspend fun saveCompanionLrc(track: Track, content: String): Boolean = withContext(Dispatchers.IO) {
        val audioFilePath = resolveAudioFilePath(track) ?: return@withContext false
        val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
        try {
            val file = File(lrcPath)
            file.writeText(content, Charsets.UTF_8)
            file.exists() && file.length() > 0L && file.readText(Charsets.UTF_8) == content
        } catch (_: Exception) {
            false
        }
    }

    /** Deletes the companion .lrc file next to the audio file if it exists. */
    suspend fun deleteCompanionLrc(track: Track): Boolean = withContext(Dispatchers.IO) {
        val audioFilePath = resolveAudioFilePath(track) ?: return@withContext false
        val lrcPath = audioFilePath.substringBeforeLast('.') + ".lrc"
        try {
            val file = File(lrcPath)
            if (!file.exists()) return@withContext true
            val deleted = file.delete()
            deleted && !file.exists()
        } catch (_: Exception) {
            false
        }
    }

    fun getAudioFilePath(track: Track): String? = resolveAudioFilePath(track)

    private fun resolveAudioFilePath(track: Track): String? {
        if (!track.id.startsWith("local:")) return null
        val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return null
        val itemUri = try {
            Uri.parse(track.uri)
        } catch (_: Exception) {
            return null
        }

        // Query the item's exact URI. IDs are volume-scoped; querying EXTERNAL_CONTENT_URI
        // by _ID alone can resolve a different file when multiple MediaStore volumes exist.
        if (itemUri.scheme != "content" ||
            itemUri.authority != "media" ||
            itemUri.lastPathSegment?.toLongOrNull() != mediaStoreId
        ) return null

        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        try {
            context.contentResolver.query(itemUri, projection, null, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (dataCol != -1 && cursor.moveToFirst()) {
                    return cursor.getString(dataCol)?.takeIf { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            // DATA is unavailable on some scoped-storage/provider combinations. Companion
            // files must fail closed there; embedded tags still use the content URI directly.
        }

        return null
    }
}
