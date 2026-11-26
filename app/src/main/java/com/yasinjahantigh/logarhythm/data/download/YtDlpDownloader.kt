package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.analytics.AppLog
import com.yasinjahantigh.logarhythm.domain.download.DownloadCommand
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpDownloader @Inject constructor(
    private val runtime: YtDlpRuntime,
    private val cookies: DownloadCookies
) {

    suspend fun download(
        url: String,
        videoId: String,
        downloadId: String,
        workspace: File,
        qualityAbrKbps: Int? = null,
        onProgress: (Float, Long, String) -> Unit
    ): File {
        val safeId = DownloadTitleParser.sanitizeFileName(videoId)
        cookies.withTempCookieFile { cookiePath ->
            val request = YoutubeDLRequest(url).apply {
                DownloadCommand.downloadOptions(workspace, safeId, cookiePath, qualityAbrKbps)
                    .forEach { (option, argument) ->
                        if (argument == null) addOption(option) else addOption(option, argument)
                    }
            }
            runtime.engineUse {
                runtime.executeWithTimeout(DOWNLOAD_TIMEOUT_MS, downloadId) {
                    YoutubeDL.getInstance().execute(request, downloadId) { progress, eta, line ->
                        onProgress(progress, eta, line)
                    }
                }
            }
        }
        AppLog.i("YtDlpDL", "Executing id=$downloadId vid=$videoId abr=$qualityAbrKbps")
        val usable = findUsableFile(workspace)
        if (usable == null) {
            AppLog.w("YtDlpDL", "No usable audio file produced id=$downloadId")
            throw DownloadPipelineException(
                DownloadErrorKind.CONTENT,
                "Download finished without an audio file."
            )
        }
        return usable
    }

    /** Keeps a complete file even when yt-dlp reports an error after writing it. */
    suspend fun findUsableFile(workspace: File): File? = withContext(Dispatchers.IO) {
        workspace.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS && AudioFileProbe.isUsable(it) }
            ?.maxByOrNull { it.lastModified() }
    }

    /** Best-effort cancellation for a running process; delegates to the runtime map. */
    fun cancel(downloadId: String) {
        runtime.cancelProcess(downloadId)
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 30 * 60_000L
        val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "opus", "ogg", "oga", "webm", "flac")
    }
}
