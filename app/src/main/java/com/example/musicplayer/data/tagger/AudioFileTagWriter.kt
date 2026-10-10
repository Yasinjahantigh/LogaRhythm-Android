package com.example.musicplayer.data.tagger

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.musicplayer.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modifies tags directly in underlying audio files through MediaStore content URIs.
 * Supports:
 * MP3 ID3v2.3 text frames are the only embedded metadata format currently supported.
 * Lyrics are written as ID3 USLT for MP3. Other formats fail explicitly instead of pretending
 * a MediaStore column change embeds data. Compose UI requests MediaStore consent before a user
 * initiated write; this service still treats any failed or unverifiable write as a failure.
 */
@Singleton
class AudioFileTagWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Writes embedded tags for MP3 only and verifies them by reading the file again.
     * The MediaStore audio metadata columns are indexed/read-only, so the embedded file tags
     * are verified directly and Room is updated by the repository only after this returns true.
     * Other MediaStore views may refresh asynchronously after the underlying file changes.
     */
    suspend fun writeTagsToFileAndMediaStore(
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!track.id.startsWith("local:")) return@withContext false
        val contentUri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return@withContext false
        val expectedId = track.id.removePrefix("local:").toLongOrNull() ?: return@withContext false
        if (!contentUri.scheme.equals("content", ignoreCase = true) ||
            !contentUri.authority.equals("media", ignoreCase = true) ||
            contentUri.lastPathSegment?.toLongOrNull() != expectedId
        ) return@withContext false
        if (!queryDisplayName(contentUri).orEmpty().substringAfterLast('.', "").equals("mp3", ignoreCase = true)) return@withContext false
        // Use MediaStore's content URI as the source of truth. DATA is optional/deprecated
        // and is not available for many scoped-storage items.
        val staged = File(context.cacheDir, "audio_tag_stage_${System.nanoTime()}.mp3")
        val original = File(context.noBackupFilesDir, "audio_tag_original_${System.nanoTime()}.mp3")
        var outputAttempted = false
        var verificationSucceeded = false
        var preserveOriginalBackup = false
        try {
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                original.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            if (!original.isFile || original.length() < 10L) return@withContext false
            original.copyTo(staged, overwrite = true)
            if (!staged.isFile || staged.length() < 10L) return@withContext false

            val wrote = writeMp3Tags(staged, title, artist, album, genre)
            if (!wrote || !verifyMp3Tags(staged, title, artist, album, genre)) return@withContext false

            // Writing a shared MediaStore item may require user consent. Never bypass it;
            // if this stream is denied, return failure so the UI cannot claim success.
            outputAttempted = true
            val outputOpened = context.contentResolver.openOutputStream(contentUri, "rwt")?.let { stream ->
                stream.use { output -> staged.inputStream().use { input -> input.copyTo(output) } }
                true
            } ?: false
            if (!outputOpened) return@withContext false

            // Read back the complete URI and compare it byte-for-byte with the already-verified
            // staged file. Streaming avoids allocating a third full copy of a potentially large song.
            if (!contentUriMatchesFile(contentUri, staged)) return@withContext false

            verificationSucceeded = true
            // Notify observers that the backing item changed. This is not treated as a metadata
            // write; Android's MediaStore index is provider-managed and may refresh asynchronously.
            runCatching { context.contentResolver.notifyChange(contentUri, null) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            // Best-effort rollback if a provider truncates or partially writes the item.
            // Consent has already been granted for this URI by the UI flow.
            if (outputAttempted && !verificationSucceeded && original.isFile) {
                val restored = withContext(NonCancellable) { restoreOriginalAndVerify(contentUri, original, "audio_tag_recovery") }
                if (!restored) {
                    preserveOriginalBackup = true
                    Log.e(TAG, "Could not verify audio restoration; retaining recovery copy at ${original.absolutePath}")
                }
            }
            runCatching { staged.delete() }
            if (!preserveOriginalBackup) runCatching { original.delete() }
        }
    }

    private fun verifyMp3Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Boolean {
        // Verify the ID3 frames themselves. MediaMetadataRetriever can return inferred or
        // cached values and may omit TCON; it is not a sufficient read-after-write check.
        val tag = readId3TextFrames(file)
        return tag["TIT2"] == title && tag["TPE1"] == artist && tag["TALB"] == album &&
            (if (genre == "Unknown Genre") tag["TCON"] == null else tag["TCON"] == genre)
    }

    /** Strictly reads ID3v2.3 text frames; any malformed/truncated frame invalidates the read. */
    private fun readId3TextFrames(file: File): Map<String, String> {
        return try {
            RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return emptyMap()
            val header = ByteArray(10)
            raf.readFully(header)
            if (String(header, 0, 3, Charsets.ISO_8859_1) != "ID3" ||
                (header[3].toInt() and 0xff) != 3 || header[4].toInt() != 0 || header[5].toInt() != 0 ||
                (6..9).any { (header[it].toInt() and 0x80) != 0 }
            ) return emptyMap()
            val tagSize = ((header[6].toInt() and 0x7f) shl 21) or
                ((header[7].toInt() and 0x7f) shl 14) or
                ((header[8].toInt() and 0x7f) shl 7) or (header[9].toInt() and 0x7f)
            if (tagSize <= 0 || tagSize > 8 * 1024 * 1024 || tagSize.toLong() > raf.length() - 10L) return emptyMap()
            val data = ByteArray(tagSize)
            raf.readFully(data)
            val values = mutableMapOf<String, String>()
            var offset = 0
            while (offset + 10 <= data.size) {
                if (data[offset] == 0.toByte()) {
                    if (data.copyOfRange(offset, data.size).any { it != 0.toByte() }) return emptyMap()
                    return values
                }
                val id = String(data, offset, 4, Charsets.ISO_8859_1)
                if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return emptyMap()
                val size = ((data[offset + 4].toInt() and 0xff) shl 24) or
                    ((data[offset + 5].toInt() and 0xff) shl 16) or
                    ((data[offset + 6].toInt() and 0xff) shl 8) or (data[offset + 7].toInt() and 0xff)
                if (size < 0) return emptyMap()
                val start = offset + 10
                val endLong = start.toLong() + size.toLong()
                if (endLong > data.size.toLong()) return emptyMap()
                val end = endLong.toInt()
                if (id in setOf("TIT2", "TPE1", "TALB", "TCON")) {
                    val payload = data.copyOfRange(start, end)
                    if (payload.isNotEmpty()) {
                        val encoding = payload[0].toInt() and 0xff
                        var textStart = 1
                        var charset = when (encoding) {
                            1, 2 -> Charsets.UTF_16BE
                            3 -> Charsets.UTF_8
                            0 -> Charsets.ISO_8859_1
                            else -> return emptyMap()
                        }
                        if (encoding == 1) {
                            if (payload.size < 3) return emptyMap()
                            when {
                                payload[1] == 0xFF.toByte() && payload[2] == 0xFE.toByte() -> { charset = Charsets.UTF_16LE; textStart = 3 }
                                payload[1] == 0xFE.toByte() && payload[2] == 0xFF.toByte() -> { charset = Charsets.UTF_16BE; textStart = 3 }
                                else -> return emptyMap()
                            }
                        }
                        val decoded = String(payload.copyOfRange(textStart, payload.size), charset)
                            .trim('\u0000', '\uFEFF')
                        if (decoded.isNotEmpty()) values[id] = decoded
                    }
                }
                offset = end
            }
                if (offset < data.size && data.copyOfRange(offset, data.size).any { it != 0.toByte() }) return emptyMap()
                values
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Write ID3v2.3 unsynchronized lyrics (USLT) into an MP3, preserving other frames. */
    suspend fun writeEmbeddedLyrics(track: Track, content: String): Boolean = withContext(Dispatchers.IO) {
        if (!track.id.startsWith("local:") || content.isBlank() ||
            content.length > (8 * 1024 * 1024 - 8192) / 2
        ) return@withContext false
        val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return@withContext false
        val expectedId = track.id.removePrefix("local:").toLongOrNull() ?: return@withContext false
        if (!uri.scheme.equals("content", ignoreCase = true) ||
            !uri.authority.equals("media", ignoreCase = true) ||
            uri.lastPathSegment?.toLongOrNull() != expectedId
        ) return@withContext false
        val displayName = queryDisplayName(uri).orEmpty()
        if (!displayName.substringAfterLast('.', "").equals("mp3", ignoreCase = true)) return@withContext false
        val staged = File(context.cacheDir, "lyrics_tag_stage_${System.nanoTime()}.mp3")
        val original = File(context.noBackupFilesDir, "lyrics_tag_original_${System.nanoTime()}.mp3")
        var outputAttempted = false
        var verificationSucceeded = false
        var preserveOriginalBackup = false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                original.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            if (!original.isFile || original.length() < 10L) return@withContext false
            original.copyTo(staged, overwrite = true)
            if (!staged.isFile || staged.length() < 10L) return@withContext false
            if (!writeMp3Lyrics(staged, content) || !verifyEmbeddedLyrics(staged, content)) return@withContext false
            outputAttempted = true
            val outputOpened = context.contentResolver.openOutputStream(uri, "rwt")?.let { stream ->
                stream.use { output -> staged.inputStream().use { input -> input.copyTo(output) } }
                true
            } ?: false
            if (!outputOpened) return@withContext false

            // staged was already parsed as a valid USLT tag. Proving the provider's full
            // byte stream matches it verifies the actual embedded payload without another full copy.
            val verified = contentUriMatchesFile(uri, staged) && verifyEmbeddedLyrics(staged, content)
            verificationSucceeded = verified
            verified
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            if (outputAttempted && !verificationSucceeded && original.isFile) {
                val restored = withContext(NonCancellable) { restoreOriginalAndVerify(uri, original, "lyrics_tag_recovery") }
                if (!restored) {
                    preserveOriginalBackup = true
                    Log.e(TAG, "Could not verify lyrics-write restoration; retaining recovery copy at ${original.absolutePath}")
                }
            }
            runCatching { staged.delete() }
            if (!preserveOriginalBackup) runCatching { original.delete() }
        }
    }

    private fun verifyEmbeddedLyrics(file: File, expected: String): Boolean {
        val actual = readId3TextFrame(file, "USLT") ?: return false
        return actual.replace("\r\n", "\n").trim() == expected.replace("\r\n", "\n").trim()
    }

    private fun readId3TextFrame(file: File, targetId: String): String? {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return null
            val header = ByteArray(10)
            raf.readFully(header)
            if (String(header, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
            val version = header[3].toInt() and 0xff
            if (version != 3 || (header[5].toInt() and 0xff) != 0) return null
            val size = ((header[6].toInt() and 0x7f) shl 21) or ((header[7].toInt() and 0x7f) shl 14) or
                ((header[8].toInt() and 0x7f) shl 7) or (header[9].toInt() and 0x7f)
            if (size <= 0 || size > 8 * 1024 * 1024 || size > raf.length() - 10) return null
            val data = ByteArray(size)
            raf.readFully(data)
            var offset = 0
            while (offset + 10 <= data.size) {
                if (data[offset] == 0.toByte()) break
                val id = String(data, offset, 4, Charsets.ISO_8859_1)
                val frameSize = if (version == 4) {
                    ((data[offset + 4].toInt() and 0x7f) shl 21) or ((data[offset + 5].toInt() and 0x7f) shl 14) or
                        ((data[offset + 6].toInt() and 0x7f) shl 7) or (data[offset + 7].toInt() and 0x7f)
                } else {
                    ((data[offset + 4].toInt() and 0xff) shl 24) or ((data[offset + 5].toInt() and 0xff) shl 16) or
                        ((data[offset + 6].toInt() and 0xff) shl 8) or (data[offset + 7].toInt() and 0xff)
                }
                val end = offset + 10 + frameSize
                if (frameSize <= 0 || end > data.size) break
                if (id == targetId) {
                    val statusFlags = data[offset + 8].toInt() and 0xff
                    val formatFlags = data[offset + 9].toInt() and 0xff
                    // This reader handles plain, uncompressed ID3v2.3 frames only. Valid
                    // status bits do not affect decoding, but reserved status bits and any
                    // format bits require behavior that is not implemented here.
                    if (targetId == "USLT" && ((statusFlags and 0x1F) != 0 || formatFlags != 0)) return null
                    val frame = data.copyOfRange(offset + 10, end)
                    if (targetId == "USLT") return Id3UsltCodec.decodePayload(frame)
                    return decodeTextPayload(frame, 0)
                }
                offset = end
            }
        }
        return null
    }

    private fun decodeTextPayload(payload: ByteArray, start: Int): String {
        if (payload.size <= start) return ""
        val encoding = payload[0].toInt() and 0xff
        var actualStart = start.coerceAtMost(payload.size)
        var charset = when (encoding) {
            1 -> Charsets.UTF_16BE // corrected to LE below when an LE BOM is present
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        // Select byte order before stripping the BOM. Using UTF_16 after stripping a LE BOM
        // defaults to BE and corrupts the read-back check for Persian/Arabic lyrics.
        if (encoding == 1 && actualStart + 1 < payload.size) {
            when {
                payload[actualStart] == 0xFF.toByte() && payload[actualStart + 1] == 0xFE.toByte() -> {
                    charset = Charsets.UTF_16LE
                    actualStart += 2
                }
                payload[actualStart] == 0xFE.toByte() && payload[actualStart + 1] == 0xFF.toByte() -> {
                    charset = Charsets.UTF_16BE
                    actualStart += 2
                }
            }
        }
        val textBytes = payload.copyOfRange(actualStart, payload.size)
        return String(textBytes, charset).trim('\u0000', '\uFEFF', ' ', '\n', '\r')
    }

    private fun writeMp3Lyrics(file: File, content: String): Boolean {
        val tempFile = File(context.cacheDir, "id3_lyrics_${System.nanoTime()}.tmp")
        try {
            RandomAccessFile(file, "r").use { source ->
                if (source.length() < 10) return false
                val header = ByteArray(10)
                source.readFully(header)
                val hasId3 = String(header, 0, 3, Charsets.ISO_8859_1) == "ID3"
                val version = if (hasId3) header[3].toInt() and 0xff else 3
                if (hasId3 && version != 3) return false
                if (hasId3) {
                    val flags = header[5].toInt() and 0xff
                    // We only rewrite flag-free ID3v2.3 headers with revision zero.
                    if (flags != 0 || (header[4].toInt() and 0xff) != 0) return false
                }
                if (hasId3 && (6..9).any { (header[it].toInt() and 0x80) != 0 }) return false
                val oldSize = if (hasId3) {
                    ((header[6].toInt() and 0x7f) shl 21) or ((header[7].toInt() and 0x7f) shl 14) or
                        ((header[8].toInt() and 0x7f) shl 7) or (header[9].toInt() and 0x7f)
                } else 0
                if (oldSize < 0 || oldSize > 8 * 1024 * 1024 || oldSize.toLong() > source.length() - 10) return false
                val tagData = ByteArray(oldSize)
                if (oldSize > 0) source.readFully(tagData)
                val kept = mutableListOf<RawId3Frame>()
                parseAndFilterId3Frames(tagData, version, kept, setOf("USLT"))
                // The codec writes separate UTF-16 BOMs for the descriptor and lyrics strings.
                val lyricsPayload = Id3UsltCodec.encodePayload(content)
                kept.add(RawId3Frame("USLT", byteArrayOf(0, 0), lyricsPayload))
                val frameBytes = ByteArrayOutputStream().apply { kept.forEach { write(it.toBytes()) } }.toByteArray()
                val padding = 1024
                val payloadSize = frameBytes.size + padding
                if (payloadSize > 8 * 1024 * 1024 || payloadSize >= (1 shl 28)) return false
                val newHeader = byteArrayOf(
                    'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
                    ((payloadSize shr 21) and 0x7f).toByte(), ((payloadSize shr 14) and 0x7f).toByte(),
                    ((payloadSize shr 7) and 0x7f).toByte(), (payloadSize and 0x7f).toByte()
                )
                RandomAccessFile(tempFile, "rw").use { output ->
                    output.setLength(0L)
                    output.write(newHeader); output.write(frameBytes); output.write(ByteArray(padding))
                    val audioOffset = if (hasId3) 10L + oldSize else 0L
                    val expectedAudioBytes = source.length() - audioOffset
                    if (expectedAudioBytes < 1L) return false
                    source.seek(audioOffset)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = expectedAudioBytes
                    while (remaining > 0L) {
                        val count = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count <= 0) return false
                        output.write(buffer, 0, count)
                        remaining -= count.toLong()
                    }
                    output.fd.sync()
                    if (output.length() != 10L + payloadSize.toLong() + expectedAudioBytes) return false
                }
            }
            // This function edits only its staged file. The caller owns the external
            // MediaStore write and verifies the original URI after committing.
            if (!tempFile.exists() || tempFile.length() <= 0L) return false
            tempFile.copyTo(file, overwrite = true)
            return file.length() == tempFile.length() && filesEqual(file, tempFile)
        } catch (_: Exception) {
            return false
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) { null }

    /**
     * Updates or injects ID3v2.3 tags for MP3 files.
     * Rewrites/updates TIT2, TPE1, TALB, TCON while preserving any other tags (artwork APIC, lyrics USLT, etc.)
     */
    private fun writeMp3Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Boolean {
        var success = false
        val tempFile = File(context.cacheDir, "id3_edit_${System.nanoTime()}.tmp")

        try {
            RandomAccessFile(file, "rw").use { raf ->
                if (raf.length() < 10) return false

                raf.seek(0)
                val header = ByteArray(10)
                raf.readFully(header)

                val hasId3 = header[0] == 'I'.code.toByte() &&
                        header[1] == 'D'.code.toByte() &&
                        header[2] == '3'.code.toByte()

                val existingOtherFrames = mutableListOf<RawId3Frame>()
                var existingTagTotalSize = 0

                if (hasId3) {
                    val version = header[3].toInt() and 0xff
                    // Only v2.3 can currently be round-tripped without transforming every
                    // frame payload and flag. Refuse v2.4 rather than corrupt unrelated tags.
                    if (version != 3) return false
                    // Extended headers, global unsynchronisation and v2.4 footer semantics need
                    // dedicated transforms. Refuse them rather than corrupting opaque frames.
                    val headerFlags = header[5].toInt() and 0xff
                    // Only a flag-free ID3v2.3 tag is understood by this writer. Refusing all
                    // flags also rejects reserved bits we cannot safely interpret.
                    if (headerFlags != 0 || (header[4].toInt() and 0xff) != 0) return false
                    if ((6..9).any { (header[it].toInt() and 0x80) != 0 }) return false
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                            ((header[7].toInt() and 0x7F) shl 14) or
                            ((header[8].toInt() and 0x7F) shl 7) or
                            (header[9].toInt() and 0x7F)
                    // Refuse unusually large/truncated tags rather than silently dropping
                    // artwork or other frames when the parser cannot preserve them.
                    if (tagSize > 2 * 1024 * 1024 || tagSize.toLong() > raf.length() - 10L) return false
                    existingTagTotalSize = 10 + tagSize

                    if (tagSize > 0) {
                        val tagData = ByteArray(tagSize)
                        raf.readFully(tagData)
                        parseAndFilterId3Frames(tagData, version, existingOtherFrames)
                    }
                }

                // Build new text frames for Title, Artist, Album, Genre
                val newFrames = mutableListOf<RawId3Frame>()
                newFrames.add(buildTextFrame("TIT2", title))
                newFrames.add(buildTextFrame("TPE1", artist))
                newFrames.add(buildTextFrame("TALB", album))
                if (genre.isNotBlank() && genre != "Unknown Genre") {
                    newFrames.add(buildTextFrame("TCON", genre))
                }

                // Combine frames: keep other existing frames (APIC, USLT, COMM, etc.)
                val allFrames = mutableListOf<RawId3Frame>()
                allFrames.addAll(newFrames)
                allFrames.addAll(existingOtherFrames)

                val framesBytesStream = ByteArrayOutputStream()
                for (frame in allFrames) {
                    framesBytesStream.write(frame.toBytes())
                }
                val framesBytes = framesBytesStream.toByteArray()

                // Always rebuild a complete staged file with an ID3v2.3 header. Reusing a
                // v2.4 header after serializing v2.3 frames would make the frame sizes invalid.
                // Create a new tag payload with 1KB padding
                val padding = 1024
                val totalPayloadSize = framesBytes.size + padding
                if (totalPayloadSize > 2 * 1024 * 1024 || totalPayloadSize >= (1 shl 28)) return false

                val audioDataOffset = if (hasId3) existingTagTotalSize.toLong() else 0L
                val audioDataLength = raf.length() - audioDataOffset
                if (audioDataLength < 1L) return false // Never rewrite an ID3-only/empty file as a song.

                // Write new complete MP3 with ID3v2.3 into temp file in cacheDir
                RandomAccessFile(tempFile, "rw").use { tempRaf ->
                    tempRaf.setLength(0L)
                    val newHeader = ByteArray(10)
                    newHeader[0] = 'I'.code.toByte()
                    newHeader[1] = 'D'.code.toByte()
                    newHeader[2] = '3'.code.toByte()
                    newHeader[3] = 3.toByte() // ID3v2.3
                    newHeader[4] = 0.toByte()
                    newHeader[5] = 0.toByte()
                    // Syncsafe integer for tag size
                    newHeader[6] = ((totalPayloadSize shr 21) and 0x7F).toByte()
                    newHeader[7] = ((totalPayloadSize shr 14) and 0x7F).toByte()
                    newHeader[8] = ((totalPayloadSize shr 7) and 0x7F).toByte()
                    newHeader[9] = (totalPayloadSize and 0x7F).toByte()

                    tempRaf.write(newHeader)
                    tempRaf.write(framesBytes)
                    tempRaf.write(ByteArray(padding))

                    // Copy original audio payload
                    raf.seek(audioDataOffset)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = audioDataLength
                    while (remaining > 0L) {
                        val bytesRead = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (bytesRead <= 0) return false
                        tempRaf.write(buffer, 0, bytesRead)
                        remaining -= bytesRead.toLong()
                    }
                    tempRaf.fd.sync()
                    val expectedLength = 10L + totalPayloadSize.toLong() + audioDataLength
                    if (tempRaf.length() != expectedLength) return false
                }
            }

            // Rewrite only the private staging file; external storage writes are owned
            // by the caller so consent and post-write verification cannot be skipped.
            if (!tempFile.exists() || tempFile.length() <= 0L) return false
            tempFile.copyTo(file, overwrite = true)
            success = file.length() == tempFile.length() && filesEqual(file, tempFile)
        } catch (_: Exception) {
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }

        return success
    }

    private fun contentUriMatchesFile(uri: Uri, expectedFile: File): Boolean {
        return try {
            val actual = context.contentResolver.openInputStream(uri) ?: return false
            actual.buffered().use { actualStream ->
                expectedFile.inputStream().buffered().use { expectedStream ->
                    val actualBuffer = ByteArray(64 * 1024)
                    val expectedBuffer = ByteArray(64 * 1024)
                    var matched = true
                    while (true) {
                        val actualCount = fillChunk(actualStream, actualBuffer)
                        val expectedCount = fillChunk(expectedStream, expectedBuffer)
                        if (actualCount != expectedCount) {
                            matched = false
                            break
                        }
                        if (actualCount < 0) {
                            matched = true
                            break
                        }
                        var chunkMatch = true
                        for (index in 0 until actualCount) {
                            if (actualBuffer[index] != expectedBuffer[index]) {
                                chunkMatch = false
                                break
                            }
                        }
                        if (!chunkMatch) {
                            matched = false
                            break
                        }
                    }
                    matched
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Fills a buffer unless EOF is reached, so streams with different read chunk sizes compare correctly. */
    private fun fillChunk(input: java.io.InputStream, buffer: ByteArray): Int {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[count++] = single.toByte()
            } else {
                count += read
            }
        }
        return if (count == 0) -1 else count
    }

    private fun filesEqual(first: File, second: File): Boolean {
        if (first.length() != second.length()) return false
        first.inputStream().buffered().use { a ->
            second.inputStream().buffered().use { b ->
                val left = ByteArray(32 * 1024)
                val right = ByteArray(32 * 1024)
                while (true) {
                    val n = a.read(left)
                    val m = b.read(right)
                    if (n != m) return false
                    if (n < 0) return true
                    for (i in 0 until n) if (left[i] != right[i]) return false
                }
            }
        }
    }

    private data class RawId3Frame(
        val id: String,
        val flags: ByteArray,
        val payload: ByteArray
    ) {
        fun toBytes(): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(id.toByteArray(Charsets.ISO_8859_1))
            // 4-byte size (ID3v2.3 uses regular 32-bit big-endian for frame size)
            val size = payload.size
            out.write((size shr 24 and 0xFF).toByte().toInt())
            out.write((size shr 16 and 0xFF).toByte().toInt())
            out.write((size shr 8 and 0xFF).toByte().toInt())
            out.write((size and 0xFF).toByte().toInt())
            out.write(flags)
            out.write(payload)
            return out.toByteArray()
        }
    }

    private fun parseAndFilterId3Frames(
        tagData: ByteArray,
        version: Int,
        outFrames: MutableList<RawId3Frame>,
        replaceFrames: Set<String> = setOf("TIT2", "TPE1", "TALB", "TCON")
    ) {
        var offset = 0

        while (offset + 10 <= tagData.size) {
            // Padding is valid only when the remaining tag bytes are all zero. Refuse
            // malformed tails instead of silently dropping any frames after the bad bytes.
            if (tagData[offset] == 0.toByte()) {
                if (tagData.copyOfRange(offset, tagData.size).any { it != 0.toByte() }) {
                    throw IllegalArgumentException("Malformed ID3 padding")
                }
                break
            }

            val frameId = String(tagData, offset, 4, Charsets.ISO_8859_1)
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) {
                throw IllegalArgumentException("Malformed ID3 frame identifier")
            }

            val frameSize = if (version == 4) {
                ((tagData[offset + 4].toInt() and 0x7F) shl 21) or
                        ((tagData[offset + 5].toInt() and 0x7F) shl 14) or
                        ((tagData[offset + 6].toInt() and 0x7F) shl 7) or
                        (tagData[offset + 7].toInt() and 0x7F)
            } else {
                ((tagData[offset + 4].toInt() and 0xFF) shl 24) or
                        ((tagData[offset + 5].toInt() and 0xFF) shl 16) or
                        ((tagData[offset + 6].toInt() and 0xFF) shl 8) or
                        (tagData[offset + 7].toInt() and 0xFF)
            }

            val flags = tagData.copyOfRange(offset + 8, offset + 10)
            if (version != 3) throw IllegalArgumentException("Only ID3v2.3 frames can be rewritten safely")
            if (flags.any { it != 0.toByte() }) {
                throw IllegalArgumentException("Flagged ID3v2.3 frames cannot be safely rewritten: $frameId")
            }
            val payloadStart = offset + 10
            val payloadEndLong = payloadStart.toLong() + frameSize.toLong()
            if (frameSize < 0 || payloadEndLong > tagData.size.toLong()) {
                throw IllegalArgumentException("Malformed or truncated ID3v2.3 frame: $frameId")
            }
            val payloadEnd = payloadEndLong.toInt()
            if (!replaceFrames.contains(frameId)) {
                val payload = tagData.copyOfRange(payloadStart, payloadEnd)
                outFrames.add(RawId3Frame(frameId, flags, payload))
            }
            offset = payloadEnd
        }
        if (offset < tagData.size && tagData.copyOfRange(offset, tagData.size).any { it != 0.toByte() }) {
            throw IllegalArgumentException("Truncated ID3v2.3 frame header")
        }
    }

    /**
     * Builds an ID3v2.3 UTF-16LE text frame with BOM (0x01 encoding) so Persian, Arabic, English, and all UTF characters render accurately.
     */
    private fun buildTextFrame(frameId: String, text: String): RawId3Frame {
        val out = ByteArrayOutputStream()
        out.write(1) // 0x01: UTF-16 with BOM
        // BOM for UTF-16LE: 0xFF, 0xFE
        out.write(0xFF)
        out.write(0xFE)
        out.write(text.toByteArray(Charsets.UTF_16LE))

        val flags = byteArrayOf(0, 0)
        return RawId3Frame(frameId, flags, out.toByteArray())
    }

    /** Attempt rollback after a partial provider write and verify every byte against the backup. */
    private fun restoreOriginalAndVerify(uri: Uri, original: File, prefix: String): Boolean {
        val checkFile = File(context.cacheDir, "${prefix}_${System.nanoTime()}.mp3")
        return try {
            // openOutputStream may fail before mutating anything. Check first so a denied write
            // does not create a spurious recovery copy or trigger a second unnecessary write.
            val alreadyOriginal = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    checkFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching false
                filesEqual(original, checkFile)
            }.getOrDefault(false)
            if (alreadyOriginal) return true

            val output = context.contentResolver.openOutputStream(uri, "rwt") ?: return false
            output.use { stream -> original.inputStream().use { input -> input.copyTo(stream) } }
            context.contentResolver.openInputStream(uri)?.use { input ->
                checkFile.outputStream().use { outputStream -> input.copyTo(outputStream) }
            } ?: return false
            filesEqual(original, checkFile)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { checkFile.delete() }
        }
    }

    private companion object {
        const val TAG = "AudioFileTagWriter"
    }

}
