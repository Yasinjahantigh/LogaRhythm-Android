package com.yasinjahantigh.logarhythm.data.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube DASH audio arrives as a fragmented MP4, which the tag writer refuses to touch.
 * MediaExtractor/Muxer rewrites the audio track into a regular MP4 without re-encoding.
 */
@Singleton
class Mp4Remuxer @Inject constructor() {

    suspend fun normalize(file: File): File = withContext(Dispatchers.IO) {
        if (file.extension.lowercase() !in MP4_EXTENSIONS || !isFragmented(file)) {
            return@withContext file
        }
        val target = File(file.parentFile, "${file.nameWithoutExtension}.remux")
        target.delete()
        try {
            remux(file, target)
            if (target.length() < MIN_BYTES) {
                target.delete()
                return@withContext file
            }
            if (!file.delete()) {
                target.delete()
                return@withContext file
            }
            if (!target.renameTo(file)) {
                target.copyTo(file, overwrite = true)
                target.delete()
            }
            file
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (_: Exception) {
            target.delete()
            file
        }
    }

    private fun remux(source: File, target: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IllegalStateException("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)

            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(format)
            muxer.start()

            val maxInput = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                .getOrDefault(BUFFER_SIZE)
            val buffer = ByteBuffer.allocateDirect(maxOf(maxInput, BUFFER_SIZE))
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, 0)
                muxer.writeSampleData(outputTrack, buffer, info)
                if (!extractor.advance()) break
            }
            muxer.stop()
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
    }

    internal fun isFragmented(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var pos = 0L
            while (pos + 8 <= length) {
                raf.seek(pos)
                val header = ByteArray(8)
                raf.readFully(header)
                var size = be32(header, 0)
                val type = String(header, 4, 4, Charsets.ISO_8859_1)
                if (size == 1L) {
                    if (pos + 16 > length) return false
                    val extended = ByteArray(8)
                    raf.readFully(extended)
                    size = be64(extended, 0)
                } else if (size == 0L) {
                    size = length - pos
                }
                if (size < 8) return false
                if (type == "moof") return true
                pos += size
            }
            false
        }
    } catch (_: Exception) {
        false
    }

    private fun be32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun be64(bytes: ByteArray, offset: Int): Long =
        (be32(bytes, offset) shl 32) or be32(bytes, offset + 4)

    private companion object {
        val MP4_EXTENSIONS = setOf("m4a", "mp4", "aac")
        const val BUFFER_SIZE = 256 * 1024
        const val MIN_BYTES = 32 * 1024L
    }
}
