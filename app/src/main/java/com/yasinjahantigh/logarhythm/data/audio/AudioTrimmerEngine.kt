package com.yasinjahantigh.logarhythm.data.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.yasinjahantigh.logarhythm.core.library.LibrarySync
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

data class TrimResult(
    val uri: Uri?,
    val isRingtone: Boolean,
    val ringtoneAppliedDirectly: Boolean,
    val error: String? = null
)

/**
 * Lossless container trimmer for M4A/MP4 (MediaExtractor + MediaMuxer) and MP3 (frame-level
 * MPEG audio slicer), plus MediaCodec AAC-M4A transcode fallback for FLAC/OGG/Opus/WAV.
 */
@Singleton
class AudioTrimmerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val librarySync: LibrarySync
) {

    suspend fun trimAndSave(
        track: Track,
        startMs: Long,
        endMs: Long,
        asRingtone: Boolean
    ): TrimResult = withContext(Dispatchers.IO) {
        val safeStartMs = startMs.coerceAtLeast(0L)
        val safeEndMs = endMs.coerceAtLeast(safeStartMs + 1_000L)
        val sourceUri = runCatching { Uri.parse(track.uri) }.getOrNull()
            ?: return@withContext TrimResult(null, asRingtone, false, "Invalid source URI")

        val ext = track.filePath.substringAfterLast('.', "").lowercase()
        val tempIn = File(context.cacheDir, "trim_in_${System.currentTimeMillis()}.${ext.ifBlank { "tmp" }}")
        val tempOut: File
        var outExt: String
        var outMime: String

        try {
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempIn).use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || !tempIn.exists() || tempIn.length() == 0L) {
                return@withContext TrimResult(null, asRingtone, false, "Cannot read audio file")
            }

            if (ext == "mp3") {
                outExt = "mp3"
                outMime = "audio/mpeg"
                tempOut = File(context.cacheDir, "trim_out_${System.currentTimeMillis()}.mp3")
                val ok = BufferedInputStream(FileInputStream(tempIn)).use { ins ->
                    BufferedOutputStream(FileOutputStream(tempOut)).use { outs ->
                        sliceMp3Stream(ins, outs, safeStartMs, safeEndMs)
                    }
                }
                if (!ok || tempOut.length() == 0L) {
                    tempOut.delete()
                    outExt = "m4a"
                    outMime = "audio/mp4"
                    val fallbackOut = File(context.cacheDir, "trim_out_${System.currentTimeMillis()}.m4a")
                    transcodeRangeToM4a(tempIn, fallbackOut, safeStartMs * 1000L, safeEndMs * 1000L)
                    val res = persistOutputFile(track, fallbackOut, outExt, outMime, asRingtone)
                    fallbackOut.delete()
                    return@withContext res
                }
            } else if (ext in setOf("m4a", "mp4", "aac")) {
                outExt = "m4a"
                outMime = "audio/mp4"
                tempOut = File(context.cacheDir, "trim_out_${System.currentTimeMillis()}.m4a")
                val remuxed = runCatching {
                    remuxRangeM4a(tempIn, tempOut, safeStartMs * 1000L, safeEndMs * 1000L)
                }.getOrDefault(false)
                if (!remuxed || tempOut.length() == 0L) {
                    tempOut.delete()
                    transcodeRangeToM4a(tempIn, tempOut, safeStartMs * 1000L, safeEndMs * 1000L)
                }
            } else {
                outExt = "m4a"
                outMime = "audio/mp4"
                tempOut = File(context.cacheDir, "trim_out_${System.currentTimeMillis()}.m4a")
                transcodeRangeToM4a(tempIn, tempOut, safeStartMs * 1000L, safeEndMs * 1000L)
            }

            if (!tempOut.exists() || tempOut.length() == 0L) {
                return@withContext TrimResult(null, asRingtone, false, "Trim produced empty output")
            }
            val result = persistOutputFile(track, tempOut, outExt, outMime, asRingtone)
            tempOut.delete()
            result
        } catch (e: Exception) {
            TrimResult(null, asRingtone, false, e.message ?: "Trim failed")
        } finally {
            tempIn.delete()
        }
    }

    private fun remuxRangeM4a(inputFile: File, outputFile: File, startUs: Long, endUs: Long): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) return false
            extractor.selectTrack(audioTrack)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var basePts = -1L
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                if (pts >= startUs) {
                    if (basePts < 0L) basePts = pts
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = (pts - basePts).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
            return true
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun transcodeRangeToM4a(inputFile: File, outputFile: File, startUs: Long, endUs: Long) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrack = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    audioTrack = i
                    inFormat = fmt
                    break
                }
            }
            if (audioTrack < 0 || inFormat == null) error("No audio track found")
            val inMime = inFormat.getString(MediaFormat.KEY_MIME) ?: error("No mime")
            val sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)

            extractor.selectTrack(audioTrack)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            decoder = MediaCodec.createDecoderByType(inMime).apply {
                configure(inFormat, null, null, 0)
                start()
            }

            val outFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxTrack = -1
            var muxStarted = false
            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var decInputDone = false
            var decOutputDone = false
            var encOutputDone = false
            var encodedPtsBase = -1L

            while (!encOutputDone) {
                if (!decInputDone) {
                    val idx = decoder.dequeueInputBuffer(5_000)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        val pts = extractor.sampleTime
                        if (sz < 0 || pts > endUs) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decInputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, sz, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decOutputDone) {
                    val outIdx = decoder.dequeueOutputBuffer(decInfo, 5_000)
                    if (outIdx >= 0) {
                        val eos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val decBuf = decoder.getOutputBuffer(outIdx)
                        if (decBuf != null && decInfo.size > 0 && decInfo.presentationTimeUs >= startUs) {
                            decBuf.position(decInfo.offset)
                            decBuf.limit(decInfo.offset + decInfo.size)
                            val encInIdx = encoder.dequeueInputBuffer(5_000)
                            if (encInIdx >= 0) {
                                val encIn = encoder.getInputBuffer(encInIdx)!!
                                encIn.clear()
                                val count = minOf(encIn.remaining(), decBuf.remaining())
                                val slice = decBuf.slice().apply { limit(count) }
                                encIn.put(slice)
                                if (encodedPtsBase < 0L) encodedPtsBase = decInfo.presentationTimeUs
                                val relPts = (decInfo.presentationTimeUs - encodedPtsBase).coerceAtLeast(0L)
                                encoder.queueInputBuffer(encInIdx, 0, count, relPts, 0)
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (eos) {
                            val encInIdx = encoder.dequeueInputBuffer(5_000)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            decOutputDone = true
                        }
                    }
                }

                val encOutIdx = encoder.dequeueOutputBuffer(encInfo, 5_000)
                when {
                    encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxStarted) {
                            muxTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxStarted = true
                        }
                    }
                    encOutIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(encOutIdx)
                        val isConfig = (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && encInfo.size > 0 && muxStarted && outBuf != null) {
                            outBuf.position(encInfo.offset)
                            outBuf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxTrack, outBuf, encInfo)
                        }
                        encoder.releaseOutputBuffer(encOutIdx, false)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encOutputDone = true
                        }
                    }
                }
            }
            if (muxStarted) muxer.stop()
        } finally {
            runCatching { decoder?.stop(); decoder?.release() }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun persistOutputFile(
        track: Track,
        file: File,
        ext: String,
        mime: String,
        asRingtone: Boolean
    ): TrimResult {
        val baseTitle = DownloadTitleParser.sanitizeFileName("${track.title}_clip").ifBlank { "trimmed" }
        val displayName = "${baseTitle}_${System.currentTimeMillis() % 10_000}.$ext"
        val relPath = if (asRingtone) Environment.DIRECTORY_RINGTONES else "${Environment.DIRECTORY_MUSIC}/LogaRhythm"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.TITLE, "${track.title} (Cut)")
            put(MediaStore.Audio.Media.ARTIST, track.artist)
            put(MediaStore.Audio.Media.IS_RINGTONE, if (asRingtone) 1 else 0)
            put(MediaStore.Audio.Media.IS_MUSIC, if (asRingtone) 0 else 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relPath)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val insertedUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return TrimResult(null, asRingtone, false, "MediaStore insert failed")

        resolver.openOutputStream(insertedUri)?.use { out ->
            FileInputStream(file).use { ins -> ins.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ready = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(insertedUri, ready, null, null)
        }

        var applied = false
        if (asRingtone && Settings.System.canWrite(context)) {
            applied = runCatching {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, insertedUri)
                true
            }.getOrDefault(false)
        }
        if (!asRingtone) {
            librarySync.requestRefresh(force = true)
        }
        return TrimResult(uri = insertedUri, isRingtone = asRingtone, ringtoneAppliedDirectly = applied)
    }

    companion object {
        private val BITRATE_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        private val BITRATE_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
        private val SAMPLE_RATE_V1 = intArrayOf(44100, 48000, 32000, 0)
        private val SAMPLE_RATE_V2 = intArrayOf(22050, 24000, 16000, 0)
        private val SAMPLE_RATE_V25 = intArrayOf(11025, 12000, 8000, 0)

        /**
         * Pure Kotlin MPEG audio frame slicer. Skips ID3v2 header, slices frames whose cumulative
         * presentation time falls within [startMs, endMs]. Returns false if no valid frames matched.
         */
        fun sliceMp3Stream(
            input: InputStream,
            output: OutputStream,
            startMs: Long,
            endMs: Long
        ): Boolean {
            val bytes = input.readBytes()
            if (bytes.size < 4) return false

            var offset = 0
            if (bytes.size >= 10 &&
                bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte()
            ) {
                val id3Size = ((bytes[6].toInt() and 0x7F) shl 21) or
                    ((bytes[7].toInt() and 0x7F) shl 14) or
                    ((bytes[8].toInt() and 0x7F) shl 7) or
                    (bytes[9].toInt() and 0x7F)
                offset = (10 + id3Size).coerceAtMost(bytes.size)
            }

            val startUs = startMs.coerceAtLeast(0L) * 1_000L
            val endUs = endMs.coerceAtLeast(startMs + 100L) * 1_000L
            var currentUs = 0.0
            var framesWritten = 0

            while (offset + 4 <= bytes.size) {
                val b0 = bytes[offset].toInt() and 0xFF
                val b1 = bytes[offset + 1].toInt() and 0xFF
                if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) {
                    offset++
                    continue
                }
                val b2 = bytes[offset + 2].toInt() and 0xFF
                val versionBits = (b1 ushr 3) and 0x03
                val layerBits = (b1 ushr 1) and 0x03
                val bitrateIdx = (b2 ushr 4) and 0x0F
                val sampleIdx = (b2 ushr 2) and 0x03
                val padding = (b2 ushr 1) and 0x01

                if (versionBits == 1 || layerBits != 1 || bitrateIdx == 0 || bitrateIdx == 15 || sampleIdx == 3) {
                    offset++
                    continue
                }

                val sampleRate = when (versionBits) {
                    3 -> SAMPLE_RATE_V1[sampleIdx]
                    2 -> SAMPLE_RATE_V2[sampleIdx]
                    else -> SAMPLE_RATE_V25[sampleIdx]
                }
                val bitrateKbps = if (versionBits == 3) BITRATE_V1_L3[bitrateIdx] else BITRATE_V2_L3[bitrateIdx]
                if (sampleRate <= 0 || bitrateKbps <= 0) {
                    offset++
                    continue
                }

                val samplesPerFrame = if (versionBits == 3) 1152 else 576
                val coefficient = if (versionBits == 3) 144 else 72
                val frameBytes = (coefficient * (bitrateKbps * 1000) / sampleRate) + padding
                if (frameBytes < 4 || offset + frameBytes > bytes.size) break

                val frameDurUs = (samplesPerFrame * 1_000_000.0) / sampleRate
                if (currentUs > endUs) break
                if (currentUs + frameDurUs >= startUs) {
                    output.write(bytes, offset, frameBytes)
                    framesWritten++
                }
                currentUs += frameDurUs
                offset += frameBytes
            }
            return framesWritten > 0
        }
    }
}
