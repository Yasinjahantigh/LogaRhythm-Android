package com.yasinjahantigh.logarhythm.data.download.radiojavan

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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioJavanDownloader @Inject constructor(
    client: OkHttpClient,
    private val clientApi: RadioJavanClient
) {
    private val audioClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    private val runningCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val proxyBase = "https://abraava.ir/?url="

    suspend fun download(
        songId: String,
        sourceUrl: String,
        downloadId: String,
        workspace: File,
        onProgress: (Float, Long, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val resolvedUrl = clientApi.resolveMp3Url(songId, sourceUrl)
            ?: throw DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                "Radio Javan could not resolve the audio file for this track."
            )

        val safeId = DownloadTitleParser.sanitizeFileName(songId.ifBlank { "radiojavan_track" })
        val target = File(workspace, "$safeId.part")
        target.delete()

        try {
            try {
                downloadTo(target, resolvedUrl, downloadId, onProgress)
            } catch (pipeline: DownloadPipelineException) {
                // If direct fails on blocked network, try proxy
                if (pipeline.kind == DownloadErrorKind.NETWORK) {
                    target.delete()
                    val proxiedUrl = proxyBase + URLEncoder.encode(resolvedUrl, Charsets.UTF_8.name())
                    downloadTo(target, proxiedUrl, downloadId, onProgress)
                } else {
                    throw pipeline
                }
            }

            if (!AudioFileProbe.isUsable(target)) {
                target.delete()
                throw DownloadPipelineException(
                    DownloadErrorKind.CONTENT,
                    "Radio Javan returned an unusable audio file."
                )
            }

            val result = File(workspace, "$safeId.mp3")
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
            .header("User-Agent", "Radio Javan/10.7.6 (Android; Android OS 14; en)")
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
                        "Radio Javan download failed (HTTP ${response.code})."
                    )
                }
                val body = response.body
                    ?: throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "Radio Javan returned an empty file."
                    )
                val expected = body.contentLength().takeIf { it > 0 }
                    ?: response.header("Content-Range")
                        ?.substringAfterLast('/')
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                onProgress(0f, 0L, "[download] Radio Javan")

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
                                    onProgress(percent / 100f, eta, "[download] Radio Javan $percent%")
                                }
                            }
                        }
                    }
                }
                if (written <= 0L) {
                    throw DownloadPipelineException(
                        DownloadErrorKind.CONTENT,
                        "Radio Javan returned an empty file."
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
                error.message ?: "Radio Javan download failed.",
                error
            )
        } finally {
            runningCalls.remove(downloadId)
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
