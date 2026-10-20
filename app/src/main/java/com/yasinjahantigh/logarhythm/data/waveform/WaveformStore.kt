package com.yasinjahantigh.logarhythm.data.waveform

import android.content.ContentUris
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real, decoded waveform for the Now Playing scrubber, computed once per song in the background
 * and cached as a 160-byte file. Anything that cannot be decoded (very long files, DRM, web
 * tracks, a failing codec) returns null and the UI keeps its seeded fingerprint, so playback and
 * scrolling are never affected.
 *
 * Decoding is serialised (one song at a time), runs at background priority and is cancelled as
 * soon as the song changes. Only the first [MAX_DECODE_MS] of a file are decoded.
 */
object WaveformStore {
    const val BINS = 160
    private const val MAX_DECODE_MS = 15 * 60_000L
    private val decodeLock = Mutex()

    fun cacheFile(context: Context, trackId: String): File {
        val dir = File(context.cacheDir, "waveforms").apply { mkdirs() }
        return File(dir, trackId.hashCode().toUInt().toString(16) + ".wf")
    }

    private fun uriFor(trackId: String): Uri? {
        val id = trackId.removePrefix("local:").toLongOrNull()?.takeIf { trackId.startsWith("local:") } ?: return null
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }

    suspend fun load(
        context: Context,
        trackId: String,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): FloatArray? = withContext(dispatcher) {
        val file = cacheFile(context.applicationContext, trackId)
        readCache(file)?.let { return@withContext it }
        val uri = uriFor(trackId) ?: return@withContext null
        decodeLock.withLock {
            readCache(file)?.let { return@withContext it }
            val bins = try {
                decode(context.applicationContext, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return@withContext null
            runCatching { file.writeBytes(ByteArray(BINS) { (bins[it] * 255f).toInt().coerceIn(0, 255).toByte() }) }
            bins
        }
    }

    private fun readCache(file: File): FloatArray? {
        if (!file.exists() || file.length() != BINS.toLong()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return FloatArray(BINS) { (bytes[it].toInt() and 0xFF) / 255f }
    }

    private suspend fun decode(context: Context, uri: Uri): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs <= 0L) return null
            val span = minOf(durationUs, MAX_DECODE_MS * 1000L)
            if (durationUs > MAX_DECODE_MS * 1000L * 4) return null // audiobook-sized: keep the fingerprint

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sums = DoubleArray(BINS)
            val counts = LongArray(BINS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44_100
            var frameIndex = 0L
            val totalFrames = (span / 1_000_000.0 * sampleRate).toLong().coerceAtLeast(1L)

            while (!outputDone) {
                kotlin.coroutines.coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > span) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 5_000)
                when {
                    outIndex >= 0 -> {
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                            val frames = shorts.remaining() / channels.coerceAtLeast(1)
                            // Sample sparsely: every 8th frame is plenty for 160 bins.
                            var f = 0
                            while (f < frames) {
                                val position = frameIndex + f
                                val bin = ((position * BINS) / totalFrames).toInt().coerceIn(0, BINS - 1)
                                var peak = 0
                                for (c in 0 until channels) {
                                    peak = maxOf(peak, abs(shorts.get(f * channels + c).toInt()))
                                }
                                val v = peak / 32768.0
                                sums[bin] += v * v
                                counts[bin]++
                                f += 8
                            }
                            frameIndex += frames
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            }
            return normalize(sums, counts)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** RMS per bin, scaled so the 95th percentile reaches full height and the floor stays visible. */
    internal fun normalize(sums: DoubleArray, counts: LongArray): FloatArray? {
        val rms = DoubleArray(sums.size) { if (counts[it] > 0) sqrt(sums[it] / counts[it]) else 0.0 }
        if (rms.all { it == 0.0 }) return null
        val sorted = rms.sorted()
        val reference = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)].coerceAtLeast(1e-4)
        return FloatArray(sums.size) { (0.12f + 0.88f * (rms[it] / reference).toFloat()).coerceIn(0.12f, 1f) }
    }
}
