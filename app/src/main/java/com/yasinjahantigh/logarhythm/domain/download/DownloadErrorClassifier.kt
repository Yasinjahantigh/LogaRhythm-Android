package com.yasinjahantigh.logarhythm.domain.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns yt-dlp stderr / Java exceptions into the three outcomes the UI cares about:
 * connectivity failures (VPN hint), site/extractor failures (update and retry) and
 * ordinary content failures (never shown as a connection problem).
 */
object DownloadErrorClassifier {

    private val networkSignals = listOf(
        "failed to resolve",
        "name or service not known",
        "temporary failure in name resolution",
        "getaddrinfo failed",
        "nodename nor servname",
        "connection reset",
        "connection refused",
        "connection aborted",
        "broken pipe",
        "timed out",
        "read timed out",
        "connect timeout",
        "urlopen error",
        "network is unreachable",
        "no route to host",
        "eof occurred in violation of protocol",
        "certificate_verify_failed",
        "ssl",
        "tunnel connection failed",
        "proxyerror"
    )

    private val storageSignals = listOf(
        "no space left on device",
        "permission denied",
        "read-only file system",
        "errno 28",
        "enospc"
    )

    private val runtimeSignals = listOf(
        "traceback (most recent call last)",
        "modulenotfounderror",
        "syntaxerror",
        "importerror",
        "libpython",
        "failed to initialize"
    )

    private val updateSignals = listOf(
        "unable to extract",
        "nsig",
        "no video formats found",
        "player response",
        "sign in to confirm",
        "failed to extract",
        "requested format is not available"
    )

    private val contentSignals = listOf(
        "video unavailable",
        "this video is not available",
        "removed by the uploader",
        "private video",
        "members-only",
        "age-restricted",
        "not available in your country",
        "http error 403",
        "http error 404",
        "http error 429",
        "unsupported url",
        "does not exist",
        "no such track",
        "content is not available",
        "no output file"
    )

    fun classify(message: String?, cause: Throwable? = null): DownloadErrorKind {
        var current: Throwable? = cause
        while (current != null) {
            when (current) {
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is NoRouteToHostException,
                is SSLException,
                is InterruptedIOException -> return DownloadErrorKind.NETWORK
                is SocketException -> return DownloadErrorKind.NETWORK
            }
            current = current.cause
        }

        val text = message.orEmpty().lowercase()
        if (text.isBlank()) return DownloadErrorKind.UNKNOWN

        return when {
            networkSignals.any(text::contains) -> DownloadErrorKind.NETWORK
            storageSignals.any(text::contains) -> DownloadErrorKind.STORAGE
            runtimeSignals.any(text::contains) -> DownloadErrorKind.RUNTIME
            updateSignals.any(text::contains) -> DownloadErrorKind.UPDATE
            contentSignals.any(text::contains) -> DownloadErrorKind.CONTENT
            else -> DownloadErrorKind.UNKNOWN
        }
    }

    /** Site-change symptoms where updating yt-dlp once can fix the extraction. */
    fun isSiteChange(kind: DownloadErrorKind): Boolean = kind == DownloadErrorKind.UPDATE
}
