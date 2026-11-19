package com.yasinjahantigh.logarhythm.data.download

import android.content.ContextWrapper
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadSourceRouterTest {

    private fun createRouter(): DownloadSourceRouter {
        val fakeContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any? = null
        }
        return DownloadSourceRouter(context = fakeContext, baseClient = OkHttpClient())
    }

    @Test
    fun `when VPN is OFF, MusicMan is always prioritized first`() {
        val router = createRouter()
        val ordered = router.sortByMeasuredSpeed(
            candidates = listOf(
                DownloadSource.SAAVN,
                DownloadSource.DEEZER,
                DownloadSource.RADIOJAVAN,
                DownloadSource.MUSICMAN
            ),
            vpnActive = false
        )
        assertEquals(DownloadSource.MUSICMAN, ordered.first())
    }

    @Test
    fun `when VPN is ON, sources are sorted by fastest measured latency`() {
        val router = createRouter()
        router.recordLatency(DownloadSource.MUSICMAN, elapsedMs = 1_400L, succeeded = true)
        router.recordLatency(DownloadSource.SAAVN, elapsedMs = 190L, succeeded = true)
        router.recordLatency(DownloadSource.RADIOJAVAN, elapsedMs = 450L, succeeded = true)
        router.recordLatency(DownloadSource.DEEZER, elapsedMs = 310L, succeeded = true)

        val ordered = router.sortByMeasuredSpeed(
            candidates = listOf(
                DownloadSource.MUSICMAN,
                DownloadSource.RADIOJAVAN,
                DownloadSource.DEEZER,
                DownloadSource.SAAVN
            ),
            vpnActive = true
        )
        assertEquals(
            listOf(
                DownloadSource.SAAVN,
                DownloadSource.DEEZER,
                DownloadSource.RADIOJAVAN,
                DownloadSource.MUSICMAN
            ),
            ordered
        )
    }

    @Test
    fun `failed source is pushed to the end when VPN is ON`() {
        val router = createRouter()
        router.recordLatency(DownloadSource.SAAVN, elapsedMs = 5_000L, succeeded = false)
        router.recordLatency(DownloadSource.RADIOJAVAN, elapsedMs = 320L, succeeded = true)
        router.recordLatency(DownloadSource.MUSICMAN, elapsedMs = 680L, succeeded = true)

        val ordered = router.sortByMeasuredSpeed(
            candidates = listOf(
                DownloadSource.SAAVN,
                DownloadSource.MUSICMAN,
                DownloadSource.RADIOJAVAN
            ),
            vpnActive = true
        )
        assertEquals(DownloadSource.RADIOJAVAN, ordered.first())
        assertEquals(DownloadSource.SAAVN, ordered.last())
        assertFalse(ordered.first() == DownloadSource.SAAVN)
    }
}
