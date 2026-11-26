package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
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

        val results = client.search("saat", DownloadSearchCategory.TRACK)

        assertEquals(2, results.size)
        val result = results.first()
        assertEquals("42", result.id)
        assertEquals("Saat Divari", result.title)
        assertEquals("Mohsen Chavoshi", result.artist)
        assertEquals(DownloadSource.MUSICMAN, result.source)
        assertEquals(291000L, result.durationMs)
        assertEquals("musicman://track/42", result.url)
        assertTrue(result.hasReadyAudio)
        assertTrue(result.thumbnailUrl.orEmpty().endsWith("/600x600bb.jpg"))
        assertEquals(false, results[1].hasReadyAudio)
        val request = server.takeRequest()
        assertEquals("/mm/api/search", request.requestUrl?.encodedPath)
        assertEquals("saat", request.requestUrl?.queryParameter("term"))
        assertEquals("50", request.requestUrl?.queryParameter("limit"))
    }

    @Test
    fun resolveAudio_prioritizesBaleMirrorOverTelegramAndRoutesToMessageIdProxy() = runTest {
        client.proxyBase = "https://mm.3rah.ir/api/proxy"
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [{"wrapperType": "track", "trackId": "77",
                  "attachments": {"audioUrls": [
                    {"quality": "320", "source": "telegram", "url": "https://api.telegram.org/file/bot<token>/77"},
                    {"quality": "192", "source": "bale", "baleMessageId": "110612", "baleFileUrl": "bale://message/110612"}
                  ]}}]}
                """.trimIndent()
            )
        )

        val candidates = client.resolveAudioCandidates("77")

        assertEquals(2, candidates.size)
        assertTrue(candidates.first().isBale)
        assertEquals("110612", candidates.first().baleMessageId)
        assertEquals("https://mm.3rah.ir/api/proxy?message_id=110612", client.playableUrl(candidates.first()))
    }

    @Test
    fun searchAndLookup_parsesArtistsAndAlbumsCleanly() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [
                  {"wrapperType": "artist", "artistId": 1001, "artistName": "Shadmehr Aghili", "primaryGenreName": "Pop"},
                  {"wrapperType": "collection", "collectionId": 2002, "collectionName": "Tajrobeh Kon", "artistName": "Shadmehr Aghili", "trackCount": 8, "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/x/100x100bb.jpg"}
                ]}
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [
                  {"wrapperType": "collection", "collectionId": 2002, "collectionName": "Tajrobeh Kon", "artistName": "Shadmehr Aghili", "trackCount": 1},
                  {"wrapperType": "track", "trackId": 3003, "trackName": "Tajrobeh Kon", "artistName": "Shadmehr Aghili", "collectionName": "Tajrobeh Kon", "collectionId": 2002, "trackNumber": 1, "trackTimeMillis": 240000}
                ]}
                """.trimIndent()
            )
        )

        val searchItems = client.search("shadmehr", DownloadSearchCategory.ALL)
        assertEquals(2, searchItems.size)
        assertEquals(DownloadResultKind.ARTIST, searchItems[0].kind)
        assertEquals(DownloadResultKind.ALBUM, searchItems[1].kind)
        assertEquals(8, searchItems[1].trackCount)

        val album = client.lookupAlbum("2002")
        assertEquals("Tajrobeh Kon", album.header?.title)
        assertEquals(1, album.tracks.size)
        assertEquals("Tajrobeh Kon", album.tracks.single().album)
        assertEquals(1, album.tracks.single().trackNumber)
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

    @Test
    fun resolveAudioCandidates_matchesItPrefixedIdAndPreservesHttpsFallbackUrls() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [
                  {"wrapperType": "track", "trackId": 99, "trackName": "Test Song",
                   "attachments": {
                     "audioUrls": [{
                       "quality": "320",
                       "source": "bale",
                       "baleMessageId": "123456",
                       "baleFileUrl": "bale://message/123456",
                       "url": "https://cdn.example/fallback-320.mp3"
                     }]
                   }}
                ]}
                """.trimIndent()
            )
        )

        val candidates = client.resolveAudioCandidates("it_99")
        assertEquals(1, candidates.size)
        assertEquals("bale://message/123456", candidates[0].url)
        assertTrue(candidates[0].fallbackUrls.contains("https://cdn.example/fallback-320.mp3"))
    }
}
