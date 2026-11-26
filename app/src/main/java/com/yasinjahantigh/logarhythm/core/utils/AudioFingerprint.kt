package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.net.Uri
import com.yasinjahantigh.logarhythm.domain.model.Track
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

/**
 * Computes fast, deterministic audio-payload SHA-256 fingerprints for duplicate candidate tracks.
 *
 * Strips ID3v2 headers and ID3v1 trailers so two copies of the exact same audio stream still
 * match even when one has different tags or embedded cover art, while different songs never match
 * solely because of similar file size.
 */
object AudioFingerprint {

    private const val CHUNK_SIZE = 32 * 1024
    private const val MIN_AUDIO_BYTES = 16 * 1024L
    private const val CANDIDATE_DURATION_WINDOW_MS = 4_000L

    fun findCandidateIds(tracks: List<Track>, fileSizes: Map<String, Long>): Set<String> {
        val eligible = tracks.filter { it.durationMs >= 15_000L && (fileSizes[it.id] ?: 0L) >= 64 * 1024L }
            .sortedBy { it.durationMs }
        if (eligible.size < 2) return emptySet()

        val candidates = HashSet<String>()
        for (i in eligible.indices) {
            val a = eligible[i]
            val sizeA = fileSizes[a.id] ?: 0L
            for (j in i + 1 until eligible.size) {
                val b = eligible[j]
                if (b.durationMs - a.durationMs > CANDIDATE_DURATION_WINDOW_MS) break
                val sizeB = fileSizes[b.id] ?: 0L
                val maxSize = max(sizeA, sizeB)
                val allowedDelta = max(600L * 1024L, (maxSize * 0.15).toLong())
                if (abs(sizeA - sizeB) <= allowedDelta) {
                    candidates.add(a.id)
                    candidates.add(b.id)
                }
            }
        }
        return candidates
    }

    fun computeForTrack(context: Context, track: Track): String? {
        if (track.filePath.isNotBlank()) {
            val f = File(track.filePath)
            if (f.isFile && f.canRead()) {
                return runCatching {
                    FileInputStream(f).use { fis -> computeFromChannel(fis.channel) }
                }.getOrNull()
            }
        }
        if (track.uri.startsWith("content://")) {
            return runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { fis -> computeFromChannel(fis.channel) }
                }
            }.getOrNull()
        }
        return null
    }

    fun computeFromFile(file: File): String? = runCatching {
        FileInputStream(file).use { fis -> computeFromChannel(fis.channel) }
    }.getOrNull()

    internal fun computeFromChannel(channel: FileChannel): String? {
        val fileSize = channel.size()
        if (fileSize < MIN_AUDIO_BYTES) return null

        var audioStart = 0L
        var audioEnd = fileSize

        // Skip ID3v2 header if present at byte 0
        val header = ByteBuffer.allocate(10)
        channel.position(0L)
        if (readFully(channel, header) == 10) {
            header.flip()
            if (header.get(0) == 'I'.code.toByte() &&
                header.get(1) == 'D'.code.toByte() &&
                header.get(2) == '3'.code.toByte()
            ) {
                val flags = header.get(5).toInt() and 0xFF
                val tagSize = ((header.get(6).toInt() and 0x7F) shl 21) or
                    ((header.get(7).toInt() and 0x7F) shl 14) or
                    ((header.get(8).toInt() and 0x7F) shl 7) or
                    (header.get(9).toInt() and 0x7F)
                val footerSize = if ((flags and 0x10) != 0) 10L else 0L
                val candidateStart = 10L + tagSize.toLong() + footerSize
                if (candidateStart in 10L until fileSize - MIN_AUDIO_BYTES) {
                    audioStart = candidateStart
                }
            }
        }

        // Skip trailing ID3v1 128-byte tag if present
        if (audioEnd - audioStart > 128L + MIN_AUDIO_BYTES) {
            val tail = ByteBuffer.allocate(3)
            channel.position(fileSize - 128L)
            if (readFully(channel, tail) == 3) {
                tail.flip()
                if (tail.get(0) == 'T'.code.toByte() &&
                    tail.get(1) == 'A'.code.toByte() &&
                    tail.get(2) == 'G'.code.toByte()
                ) {
                    audioEnd -= 128L
                }
            }
        }

        val audioLen = audioEnd - audioStart
        if (audioLen < MIN_AUDIO_BYTES) return null

        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteBuffer.allocate(CHUNK_SIZE)

        val offsets: List<Long> = if (audioLen <= CHUNK_SIZE * 3L) {
            listOf(audioStart)
        } else {
            val maxOffset = audioEnd - CHUNK_SIZE
            listOf(
                (audioStart + (audioLen * 0.15).toLong()).coerceIn(audioStart, maxOffset),
                (audioStart + (audioLen * 0.50).toLong()).coerceIn(audioStart, maxOffset),
                (audioStart + (audioLen * 0.85).toLong()).coerceIn(audioStart, maxOffset)
            )
        }

        val readTarget = if (audioLen <= CHUNK_SIZE * 3L) audioLen.toInt() else CHUNK_SIZE
        for (offset in offsets) {
            channel.position(offset)
            if (audioLen <= CHUNK_SIZE * 3L) {
                var remaining = readTarget
                while (remaining > 0) {
                    buf.clear()
                    buf.limit(minOf(CHUNK_SIZE, remaining))
                    val n = readFully(channel, buf)
                    if (n <= 0) break
                    digest.update(buf.array(), 0, n)
                    remaining -= n
                }
            } else {
                buf.clear()
                val n = readFully(channel, buf)
                if (n <= 0) return null
                digest.update(buf.array(), 0, n)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readFully(channel: FileChannel, dst: ByteBuffer): Int {
        var total = 0
        while (dst.hasRemaining()) {
            val read = channel.read(dst)
            if (read <= 0) break
            total += read
        }
        return total
    }
}
