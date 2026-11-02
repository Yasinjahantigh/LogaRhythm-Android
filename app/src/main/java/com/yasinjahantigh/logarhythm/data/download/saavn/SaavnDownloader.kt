package com.yasinjahantigh.logarhythm.data.download.saavn

import android.os.SystemClock
import com.yasinjahantigh.logarhythm.data.download.AudioFileProbe
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaavnDownloader @Inject constructor(
    client: OkHttpClient
) {
    private val audioClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    private val runningCalls = ConcurrentHashMap<String, okhttp3.Call>()

    suspend fun download(
        downloadUrl: String,
        songId: String,
        downloadId: String,
        workspace: File,
        onProgress: (Float, Long, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val safeId = DownloadTitleParser.sanitizeFileName(songId.ifBlank { "saavn_audio" })
        val target = File(workspace, "$safeId.part")
        target.delete()

        try {
            downloadTo(target, downloadUrl, downloadId, onProgress)

            if (!AudioFileProbe.isUsable(target)) {
                target.delete()
                throw DownloadPipelineException(
                    DownloadErrorKind.CONTENT,
                    "JioSaavn returned a file that is not playable audio."
                )
            }

            val extension = resolveExtension(target, downloadUrl)
            val result = File(workspace, "$safeId.$extension")
            result.delete()
            if (!target.renameTo(result)) {
                target.copyTo(result, overwrite = true)
                target.delete()
            }
            result
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (error: DownloadPipelineException) {
            target.delete()
            throw error
        }
    }

    fun cancel(downloadId: String) {
        runningCalls.remove(downloadId)?.cancel()
    }

    private suspend fun downloadTo(
        target: File,
        url: String,
        downloadId: String,
        onProgress: (Float, Long, String) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "audio/*,*/*")
            .header("Range", "bytes=0-")
            .build()
        val call = audioClient.newCall(request)
        runningCalls[downloadId] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful && response.code != 416) {
                    val kind = if (response.code >= 500 || response.code == 429) {
                        DownloadErrorKind.NETWORK
                    } else {
                        DownloadErrorKind.CONTENT
                    }
                    throw DownloadPipelineException(
                        kind,
                        "Download failed (HTTP ${response.code})."
                    )
                }
                val body = response.body
                    ?: throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "Server returned an empty file."
                    )
                val expected = body.contentLength().takeIf { it > 0 }
                    ?: response.header("Content-Range")
                        ?.substringAfterLast('/')
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                onProgress(0f, 0L, "[download] JioSaavn")

                var written = 0L
                var lastPercent = -1
                val startedAt = SystemClock.elapsedRealtime()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (expected != null && expected > 0) {
                                val percent = (written * 100 / expected).toInt().coerceIn(0, 99)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
                                    val remaining = (expected - written).coerceAtLeast(0L)
                                    val eta = (remaining * elapsed / written.coerceAtLeast(1L)) / 1000
                                    onProgress(percent / 100f, eta, "[download] $percent%")
                                }
                            }
                        }
                    }
                }
                if (written <= 0L) {
                    throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "Server returned an empty file."
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pipeline: DownloadPipelineException) {
            throw pipeline
        } catch (error: IOException) {
            throw DownloadPipelineException(
                DownloadErrorKind.NETWORK,
                error.message ?: "JioSaavn download failed.",
                error
            )
        } finally {
            runningCalls.remove(downloadId)
        }
    }

    private fun resolveExtension(file: File, url: String): String {
        val urlExt = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (urlExt in AUDIO_EXTENSIONS) return urlExt
        val header = ByteArray(12)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
        if (read >= 4) {
            val magic = { start: Int, end: Int -> String(header, start, end - start, Charsets.ISO_8859_1) }
            when {
                magic(0, 4) == "fLaC" -> return "flac"
                magic(0, 4) == "OggS" -> return "ogg"
                read >= 8 && magic(4, 8) == "ftyp" -> return "m4a"
                magic(0, 3) == "ID3" -> return "mp3"
                header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0 -> return "mp3"
            }
        }
        return "m4a"
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "ogg", "flac")
    }
}
