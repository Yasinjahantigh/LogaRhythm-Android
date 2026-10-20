package com.yasinjahantigh.logarhythm.data.sources

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.source.LyricsSource
import com.yasinjahantigh.logarhythm.data.tagger.AudioFileTagWriter
import com.yasinjahantigh.logarhythm.data.tagger.TagWriteOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    @param:ApplicationContext private val context: Context,
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
        val embedded = extractEmbeddedLyrics(track)
        if (embedded != null && !embedded.isEmpty) {
            return@withContext embedded.copy(source = "embedded")
        }

        null
    }

    private suspend fun extractEmbeddedLyrics(track: Track): Lyrics? {
        val raw = audioFileTagWriter.readEmbeddedTags(track)?.lyrics ?: return null
        val parsed = LrcParser.parse(raw)
        return parsed.takeUnless { it.isEmpty }
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

    /** Writes lyrics into the audio file's own tags (ID3 USLT, Vorbis LYRICS, MP4 ©lyr). */
    suspend fun saveEmbeddedLyrics(track: Track, content: String): Boolean =
        audioFileTagWriter.writeEmbeddedLyrics(track, content)

    /** Removes lyrics embedded in the audio file. Succeeds trivially when nothing is embedded. */
    suspend fun clearEmbeddedLyrics(track: Track): TagWriteOutcome {
        val existing = audioFileTagWriter.readEmbeddedTags(track)
        if (existing != null && existing.lyrics.isNullOrBlank()) return TagWriteOutcome.Success
        return audioFileTagWriter.clearEmbeddedLyrics(track)
    }

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
