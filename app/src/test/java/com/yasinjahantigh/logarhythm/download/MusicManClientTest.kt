package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicManClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MusicManClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = MusicManClient(OkHttpClient())
        client.apiBase = server.url("/mm/api").toString().trimEnd('/')
        client.proxyBase = "https://proxy.example/?url="
        client.pollIntervalMs = 10
        client.crawlTimeoutMs = 2_000
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun search_keepsOnlyReadyTracksAndUpgradesArtwork() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [
                  {"wrapperType": "artist", "artistName": "Mohsen Chavoshi"},
                  {"wrapperType": "track", "trackId": 42, "trackName": "Saat Divari",
                   "artistName": "Mohsen Chavoshi", "trackTimeMillis": 291000,
                   "attachments": {
                     "artworkUrls": [{"size": "100x100", "url": "https://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg"}],
                     "audioUrls": [{"quality": "320", "url": "https://api.telegram.org/file/bot<token>/14618",
                                    "telegramFilename": "Saat Divari.mp3"}]
                   }},
                  {"wrapperType": "track", "trackId": 43, "trackName": "Not ready",
                   "artistName": "Someone", "attachments": {"audioUrls": []}}
                ]}
                """.trimIndent()
            )
        )

        val results = client.search("saat")

        assertEquals(1, results.size)
        val result = results.single()
        assertEquals("42", result.id)
        assertEquals("Saat Divari", result.title)
        assertEquals("Mohsen Chavoshi", result.artist)
        assertEquals(DownloadSource.MUSICMAN, result.source)
        assertEquals(291000L, result.durationMs)
        assertEquals("musicman://track/42", result.url)
        assertTrue(result.thumbnailUrl.orEmpty().endsWith("/600x600bb.jpg"))
        val request = server.takeRequest()
        assertEquals("/mm/api/search", request.requestUrl?.encodedPath)
        assertEquals("saat", request.requestUrl?.queryParameter("term"))
        assertEquals("50", request.requestUrl?.queryParameter("limit"))
    }

    @Test
    fun resolveAudio_usesLookupWhenServerAlreadyHasTheFile() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [{"wrapperType": "track", "trackId": "7",
                  "attachments": {"audioUrls": [
                    {"quality": "128", "url": "https://cdn.example/low.mp3"},
                    {"quality": "320", "url": "https://cdn.example/high.mp3"}
                  ]}}]}
                """.trimIndent()
            )
        )

        val audio = client.resolveAudio("7")

        assertEquals(320, audio.quality)
        assertEquals("https://cdn.example/high.mp3", audio.url)
        assertEquals(1, server.requestCount)
        assertEquals("/mm/api/lookup", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun resolveAudio_queuesMissingTrackAndPollsUntilReady() = runTest {
        server.enqueue(MockResponse().setBody("""{"results": []}"""))
        server.enqueue(MockResponse().setBody("""{"success": false, "error": "Download entry not found"}"""))
        server.enqueue(MockResponse().setBody("""{"success": true, "added_count": 1}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"success": true, "download": {"download_status": "pending", "percent": 40}}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {"success": true, "download": {"download_status": "completed", "percent": 100,
                  "attachments": {"audioUrls": [
                    {"quality": "320", "url": "https://api.telegram.org/file/bot<token>/99",
                     "telegramFilename": "Track.mp3"}
                  ]}}}
                """.trimIndent()
            )
        )

        val audio = client.resolveAudio("99")

        assertEquals(320, audio.quality)
        assertEquals(5, server.requestCount)
        assertEquals("/mm/api/lookup", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("/mm/api/download/status", server.takeRequest().requestUrl?.encodedPath)
        val add = server.takeRequest()
        assertEquals("POST", add.method)
        assertEquals("/mm/api/download/add", add.requestUrl?.encodedPath)
        assertTrue(add.body.readUtf8().contains("\"trackId\":\"99\""))
    }

    @Test
    fun playableUrl_proxiesTelegramAndLeavesDirectHostsAlone() {
        val telegram = MusicManClient.Audio(
            320,
            "https://api.telegram.org/file/bot<token>/1",
            "Track.mp3"
        )
        val direct = MusicManClient.Audio(320, "https://cdn.example/track.mp3", null)

        assertTrue(client.playableUrl(telegram).startsWith("https://proxy.example/?url="))
        assertEquals(direct.url, client.playableUrl(direct))
    }
}
