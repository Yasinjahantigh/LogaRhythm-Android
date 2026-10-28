package com.yasinjahantigh.logarhythm.data.download.musicman

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
class MusicManDownloader @Inject constructor(
    client: OkHttpClient,
    private val api: MusicManClient
) {

    private val audioClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .build()

    private val runningCalls = ConcurrentHashMap<String, okhttp3.Call>()

    suspend fun download(
        trackId: String,
        downloadId: String,
        workspace: File,
        onProgress: (Float, Long, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val audio = api.resolveAudio(trackId) { _, stage ->
            if (stage == "musicman_queued") onProgress(0f, 0L, "[download] MusicMan queued")
        }
        val direct = api.playableUrl(audio)
        val proxied = api.proxied(audio.url)
        val target = File(workspace, "${DownloadTitleParser.sanitizeFileName(trackId)}.part")
        target.delete()

        try {
            try {
                downloadTo(target, direct, downloadId, onProgress)
            } catch (pipeline: DownloadPipelineException) {
                if (pipeline.kind != DownloadErrorKind.NETWORK || direct == proxied) throw pipeline
                target.delete()
                downloadTo(target, proxied, downloadId, onProgress)
            }

            if (!AudioFileProbe.isUsable(target)) {
                target.delete()
                throw DownloadPipelineException(
                    DownloadErrorKind.CONTENT,
                    "MusicMan returned a file that is not playable audio."
                )
            }
            val extension = resolveExtension(target, audio)
            val result = File(workspace, "${DownloadTitleParser.sanitizeFileName(trackId)}.$extension")
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
                        "MusicMan download failed (HTTP ${response.code})."
                    )
                }
                val body = response.body
                    ?: throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "MusicMan returned an empty file."
                    )
                val expected = body.contentLength().takeIf { it > 0 }
                    ?: response.header("Content-Range")
                        ?.substringAfterLast('/')
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                onProgress(0f, 0L, "[download] MusicMan")

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
                            if (expected != null) {
                                val percent = (written * 100 / expected).toInt().coerceIn(0, 99)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    val elapsed = (SystemClock.elapsedRealtime() - startedAt)
                                        .coerceAtLeast(1L)
                                    val remaining = (expected - written).coerceAtLeast(0L)
                                    val eta = (remaining * elapsed / written.coerceAtLeast(1L)) / 1000
                                    onProgress(percent / 100f, eta, "[download] MusicMan $percent%")
                                }
                            }
                        }
                    }
                }
                if (written <= 0L) {
                    throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "MusicMan returned an empty file."
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
                error.message ?: "MusicMan download failed.",
                error
            )
        } finally {
            runningCalls.remove(downloadId)
        }
    }

    private fun resolveExtension(file: File, audio: MusicManClient.Audio): String {
        val hint = audio.filename
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in AUDIO_EXTENSIONS }
        val header = ByteArray(12)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(0)
        if (read >= 4) {
            val magic = { start: Int, end: Int -> String(header, start, end - start, Charsets.ISO_8859_1) }
            when {
                magic(0, 4) == "fLaC" -> return "flac"
                magic(0, 4) == "OggS" -> return hint ?: "ogg"
                read >= 8 && magic(4, 8) == "ftyp" -> return "m4a"
                magic(0, 3) == "ID3" -> return "mp3"
                header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0 -> return hint ?: "mp3"
            }
        }
        return hint ?: "mp3"
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac")
    }
}
