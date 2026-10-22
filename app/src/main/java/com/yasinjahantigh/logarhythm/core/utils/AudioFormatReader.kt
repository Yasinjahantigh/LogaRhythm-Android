package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.webkit.MimeTypeMap
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class AudioFormatInfo(
    val container: String?,
    val codec: String?,
    val bitrateKbps: Int?,
    val bitrateEstimated: Boolean,
    val sampleRateHz: Int?,
    val lossless: Boolean
) {
    fun label(losslessLabel: String): String = buildList {
        container?.let(::add)
        codec?.let { if (!it.equals(container, ignoreCase = true)) add(it) }
        if (lossless) {
            add(losslessLabel)
        } else {
            bitrateKbps?.takeIf { it > 0 }?.let {
                add((if (bitrateEstimated) "~" else "") + "$it kbps")
            }
        }
        sampleRateHz?.takeIf { it > 0 }?.let { add(formatSampleRate(it)) }
    }.joinToString(" · ")
}

object AudioFormatReader {

    suspend fun read(context: Context, track: Track): AudioFormatInfo? =
        withContext(Dispatchers.IO) {
            runCatching { readBlocking(context, track) }.getOrNull()
        }

    private fun readBlocking(context: Context, track: Track): AudioFormatInfo? {
        val path = track.filePath.takeIf { it.isNotBlank() }
            ?: FileUtils.resolveTrackPath(context, track)
        val uri = track.uri.takeIf { it.startsWith("content://") }?.let(Uri::parse)
        val extension = path?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
            ?: uri
                ?.let { context.contentResolver.getType(it) }
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }

        val extractor = MediaExtractor()
        return try {
            when {
                path != null && File(path).isFile -> extractor.setDataSource(path)
                uri != null -> extractor.setDataSource(context, uri, null)
                else -> return null
            }

            var mime: String? = null
            var bitrate = 0
            var sampleRate = 0
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val candidate = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!candidate.startsWith("audio/")) continue
                mime = candidate
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                break
            }
            if (mime == null) return null

            val codec = codecName(mime)
            val lossless = (codec != null && codec in LOSSLESS_CODECS) ||
                (extension != null && extension in LOSSLESS_EXTENSIONS)
            val estimated = bitrate <= 0
            val effectiveBitrate = if (lossless) {
                0
            } else {
                bitrate.takeIf { it > 0 }
                    ?: estimateBitrateKbps(context, path, uri, track.durationMs)
            }

            AudioFormatInfo(
                container = containerName(extension, mime),
                codec = codec,
                bitrateKbps = effectiveBitrate.takeIf { it > 0 },
                bitrateEstimated = estimated && effectiveBitrate > 0,
                sampleRateHz = sampleRate.takeIf { it > 0 },
                lossless = lossless
            )
        } finally {
            extractor.release()
        }
    }

    private fun estimateBitrateKbps(
        context: Context,
        path: String?,
        uri: Uri?,
        durationMs: Long
    ): Int {
        if (durationMs <= 0) return 0
        val length = when {
            path != null && File(path).isFile -> File(path).length()
            uri != null -> runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            }.getOrDefault(0L)
            else -> 0L
        }
        if (length <= 0) return 0
        return ((length * 8) / durationMs).toInt()
    }

    private fun codecName(mime: String): String? = when {
        mime.contains("mp4a", ignoreCase = true) || mime == "audio/aac" -> "AAC"
        mime.contains("mpeg", ignoreCase = true) || mime == "audio/mp3" -> "MP3"
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
        mime.contains("alac", ignoreCase = true) -> "ALAC"
        mime.contains("raw", ignoreCase = true) -> "PCM"
        mime.contains("webm", ignoreCase = true) -> "WebM"
        else -> mime.substringAfter('/').uppercase(Locale.US).takeIf { it.isNotBlank() }
    }

    private fun containerName(extension: String?, mime: String): String? = when (extension) {
        "m4a", "mp4" -> "M4A"
        "mp3" -> "MP3"
        "aac" -> "AAC"
        "flac" -> "FLAC"
        "opus" -> "OPUS"
        "ogg", "oga" -> "OGG"
        "webm" -> "WEBM"
        "wav" -> "WAV"
        null -> when {
            mime.startsWith("audio/mp4") -> "M4A"
            mime == "audio/mpeg" -> "MP3"
            mime == "audio/flac" -> "FLAC"
            mime == "audio/ogg" -> "OGG"
            mime == "audio/webm" -> "WEBM"
            else -> null
        }
        else -> extension.uppercase(Locale.US)
    }

    private val LOSSLESS_CODECS = setOf("FLAC", "ALAC", "PCM")
    private val LOSSLESS_EXTENSIONS = setOf("flac", "wav", "aiff", "aif", "alac")
}

private fun formatSampleRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    }
