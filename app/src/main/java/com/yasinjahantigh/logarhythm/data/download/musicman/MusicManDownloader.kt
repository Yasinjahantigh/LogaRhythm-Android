package com.yasinjahantigh.logarhythm.data.download.musicman

import android.os.SystemClock
import com.yasinjahantigh.logarhythm.data.download.AudioFileProbe
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    private val runningCalls = ConcurrentHashMap<String, okhttp3.Call>()

    suspend fun download(
        trackId: String,
        downloadId: String,
        workspace: File,
        preferredQuality: DownloadQuality = DownloadQuality.BEST,
        onProgress: (Float, Long, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val candidates = api.resolveAudioCandidates(trackId, preferredQuality) { percent, stage ->
            val msg = if (stage == "musicman_queued") "[crawl] MusicMan queued" else "[crawl] MusicMan crawling $percent%"
            onProgress(percent.coerceIn(0, 99).toFloat(), 0L, msg)
        }
        val safeId = DownloadTitleParser.sanitizeFileName(trackId)
        val target = File(workspace, "$safeId.part")
        target.delete()

        var chosenAudio: MusicManClient.Audio? = null
        var lastError: DownloadPipelineException? = null

        try {
            for (audio in candidates) {
                val urlsToTry = buildList {
                    val primary = api.playableUrl(audio)
                    add(primary)
                    if (!audio.isBale) {
                        val proxied = api.proxied(audio.url)
                        if (proxied != primary) add(proxied)
                    }
                }
                for (url in urlsToTry) {
                    target.delete()
                    try {
                        downloadWithRetry(target, url, downloadId, onProgress)
                        if (AudioFileProbe.isUsable(target)) {
                            chosenAudio = audio
                            lastError = null
                            break
                        }
                        lastError = DownloadPipelineException(
                            DownloadErrorKind.CONTENT,
                            "MusicMan returned a file that is not playable audio."
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (pipeline: DownloadPipelineException) {
                        lastError = pipeline
                    }
                }
                if (chosenAudio != null) break
            }

            val finalAudio = chosenAudio ?: throw (lastError ?: DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                "MusicMan returned no playable audio."
            ))

            val extension = resolveExtension(target, finalAudio)
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

    private suspend fun downloadWithRetry(
        target: File,
        url: String,
        downloadId: String,
        onProgress: (Float, Long, String) -> Unit
    ) {
        var attempt = 0
        while (true) {
            try {
                downloadTo(target, url, downloadId, onProgress)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (pipeline: DownloadPipelineException) {
                if (pipeline.kind != DownloadErrorKind.NETWORK || attempt >= MAX_ATTEMPTS - 1) {
                    throw pipeline
                }
                attempt++
                target.delete()
                delay(450L * attempt)
            }
        }
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
        const val MAX_ATTEMPTS = 2
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac")
    }
}
