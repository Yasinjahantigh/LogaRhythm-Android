package com.example.musicplayer.data.tagger

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.musicplayer.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles modifying tags directly in the underlying audio files and syncing with MediaStore.
 * Supports:
 * 1. MP3 ID3v2.3/ID3v2.4 frames (TIT2 for Title, TPE1 for Artist, TALB for Album, TCON for Genre)
 * 2. MediaStore audio columns updates (TITLE, ARTIST, ALBUM, GENRE)
 * 3. MediaScanner notification to ensure system-wide persistence
 */
@Singleton
class AudioFileTagWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun writeTagsToFileAndMediaStore(
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Boolean = withContext(Dispatchers.IO) {
        val audioFilePath = resolveAudioFilePath(track)
        val mediaStoreId = if (track.id.startsWith("local:")) track.id.removePrefix("local:").toLongOrNull() else null
        val contentUri = mediaStoreId?.let {
            Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.toString())
        }
        var physicalFileUpdated = false

        // 1. Write tags into audio file (e.g. MP3 ID3) directly or via ContentResolver stream
        if (!audioFilePath.isNullOrBlank()) {
            val file = File(audioFilePath)
            if (file.exists()) {
                try {
                    val ext = file.extension.lowercase()
                    if (ext == "mp3") {
                        physicalFileUpdated = writeMp3Tags(file, title, artist, album, genre, contentUri)
                    }
                } catch (_: Exception) {
                    // Fallback to MediaStore update
                }
            }
        }

        // 2. Update MediaStore columns if this is a local track
        val mediaStoreUpdated = updateMediaStoreMetadata(track, title, artist, album, genre)

        // 3. Trigger MediaScanner to rescan file if path is available
        if (audioFilePath != null) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(audioFilePath),
                    null,
                    null
                )
            } catch (_: Exception) {
            }
        }

        physicalFileUpdated || mediaStoreUpdated
    }

    private fun updateMediaStoreMetadata(
        track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String
    ): Boolean {
        if (!track.id.startsWith("local:")) return false
        val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return false

        return try {
            val uri = Uri.withAppendedPath(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mediaStoreId.toString()
            )
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.GENRE, genre)
                }
            }
            val rows = context.contentResolver.update(uri, values, null, null)
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

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

    /**
     * Updates or injects ID3v2.3 tags for MP3 files.
     * Rewrites/updates TIT2, TPE1, TALB, TCON while preserving any other tags (artwork APIC, lyrics USLT, etc.)
     */
    private fun writeMp3Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        genre: String,
        contentUri: Uri? = null
    ): Boolean {
        var success = false
        val tempFile = File(context.cacheDir, "id3_edit_${System.currentTimeMillis()}.tmp")

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
                    val version = header[3].toInt() // 3 = ID3v2.3, 4 = ID3v2.4
                    val tagSize = (header[6].toInt() and 0x7F shl 21) or
                            (header[7].toInt() and 0x7F shl 14) or
                            (header[8].toInt() and 0x7F shl 7) or
                            (header[9].toInt() and 0x7F)
                    existingTagTotalSize = 10 + tagSize

                    if (tagSize > 0 && tagSize <= 2 * 1024 * 1024 && tagSize <= raf.length() - 10) {
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

                // If existing tag has enough room, write in-place with zero file shifting!
                if (hasId3 && existingTagTotalSize >= framesBytes.size + 10) {
                    val remPadding = existingTagTotalSize - 10 - framesBytes.size
                    raf.seek(10)
                    raf.write(framesBytes)
                    if (remPadding > 0) {
                        raf.write(ByteArray(remPadding))
                    }
                    return true
                }

                // Otherwise create new tag payload with 1KB padding
                val padding = 1024
                val totalPayloadSize = framesBytes.size + padding

                val audioDataOffset = if (hasId3) existingTagTotalSize.toLong() else 0L
                val audioDataLength = raf.length() - audioDataOffset

                // Write new complete MP3 with ID3v2.3 into temp file in cacheDir
                RandomAccessFile(tempFile, "rw").use { tempRaf ->
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
                    var bytesRead: Int
                    var remaining = audioDataLength
                    while (remaining > 0) {
                        bytesRead = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (bytesRead <= 0) break
                        tempRaf.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                }
            }

            // Write back to original file
            if (tempFile.exists() && tempFile.length() > 0) {
                // Try direct file rewrite
                try {
                    RandomAccessFile(file, "rw").use { targetRaf ->
                        targetRaf.seek(0)
                        RandomAccessFile(tempFile, "r").use { srcRaf ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (srcRaf.read(buf).also { read = it } > 0) {
                                targetRaf.write(buf, 0, read)
                            }
                        }
                        targetRaf.setLength(tempFile.length())
                        success = true
                    }
                } catch (_: Exception) {
                }

                // If direct file write failed (e.g. Scoped Storage), try ContentResolver stream
                if (!success && contentUri != null) {
                    try {
                        context.contentResolver.openOutputStream(contentUri, "rwt")?.use { outStream ->
                            tempFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                            success = true
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }

        return success
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
        outFrames: MutableList<RawId3Frame>
    ) {
        var offset = 0
        val skipFrameIds = setOf("TIT2", "TPE1", "TALB", "TCON")

        while (offset + 10 <= tagData.size) {
            // Padding check: if frame ID begins with 0, it is padding
            if (tagData[offset] == 0.toByte()) break

            val frameId = String(tagData, offset, 4, Charsets.ISO_8859_1)
            // Valid frame ID: 4 uppercase letters/digits
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) break

            val frameSize = if (version == 4) {
                (tagData[offset + 4].toInt() and 0x7F shl 21) or
                        (tagData[offset + 5].toInt() and 0x7F shl 14) or
                        (tagData[offset + 6].toInt() and 0x7F shl 7) or
                        (tagData[offset + 7].toInt() and 0x7F)
            } else {
                (tagData[offset + 4].toInt() and 0xFF shl 24) or
                        (tagData[offset + 5].toInt() and 0xFF shl 16) or
                        (tagData[offset + 6].toInt() and 0xFF shl 8) or
                        (tagData[offset + 7].toInt() and 0xFF)
            }

            val flags = tagData.copyOfRange(offset + 8, offset + 10)
            val payloadStart = offset + 10
            val payloadEnd = payloadStart + frameSize

            if (payloadEnd <= tagData.size && frameSize >= 0) {
                if (!skipFrameIds.contains(frameId)) {
                    val payload = tagData.copyOfRange(payloadStart, payloadEnd)
                    outFrames.add(RawId3Frame(frameId, flags, payload))
                }
                offset = payloadEnd
            } else {
                break
            }
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
}
