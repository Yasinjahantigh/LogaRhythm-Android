package com.yasinjahantigh.logarhythm.data.download

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/** Platform check that a downloaded file really contains a playable audio track. */
internal object AudioFileProbe {
    private const val MIN_BYTES = 32 * 1024L
    private const val MIN_DURATION_US = 100_000L

    fun isUsable(file: File): Boolean {
        if (file.length() < MIN_BYTES) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasAudio = false
            var durationUs = 0L
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
                    break
                }
            }
            hasAudio && (durationUs <= 0L || durationUs >= MIN_DURATION_US)
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
    }
}
