package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.domain.download.DownloadErrorClassifier
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadErrorClassifierTest {

    @Test
    fun dnsFailure_isNetwork() {
        val message = "ERROR: Unable to download webpage: <urlopen error [Errno -3] " +
            "Temporary failure in name resolution>"
        assertEquals(DownloadErrorKind.NETWORK, DownloadErrorClassifier.classify(message))
    }

    @Test
    fun networkErrorInsideExtractionError_staysNetwork() {
        val message = "ERROR: [youtube] abc: Unable to extract nsig; " +
            "urlopen error [Errno 101] Network is unreachable"
        assertEquals(DownloadErrorKind.NETWORK, DownloadErrorClassifier.classify(message))
    }

    @Test
    fun unavailableVideo_isContent() {
        val message = "ERROR: [youtube] dQw4w9WgXcQ: Video unavailable"
        assertEquals(DownloadErrorKind.CONTENT, DownloadErrorClassifier.classify(message))
    }

    @Test
    fun http403_isContentNotNetwork() {
        val message = "ERROR: unable to download video data: HTTP Error 403: Forbidden"
        assertEquals(DownloadErrorKind.CONTENT, DownloadErrorClassifier.classify(message))
    }

    @Test
    fun nsigFailure_isSiteChange() {
        val message = "WARNING: nsig extraction failed: Some formats may be missing"
        assertEquals(DownloadErrorKind.UPDATE, DownloadErrorClassifier.classify(message))
    }

    @Test
    fun noSpace_isStorage() {
        assertEquals(
            DownloadErrorKind.STORAGE,
            DownloadErrorClassifier.classify("OSError: [Errno 28] No space left on device")
        )
    }

    @Test
    fun brokenPython_isRuntime() {
        assertEquals(
            DownloadErrorKind.RUNTIME,
            DownloadErrorClassifier.classify("ModuleNotFoundError: No module named 'mutagen'")
        )
    }

    @Test
    fun socketExceptionCause_isNetwork() {
        assertEquals(
            DownloadErrorKind.NETWORK,
            DownloadErrorClassifier.classify(null, java.net.SocketException("Connection reset"))
        )
    }

    @Test
    fun blankInput_isUnknown() {
        assertEquals(DownloadErrorKind.UNKNOWN, DownloadErrorClassifier.classify(null))
    }
}
