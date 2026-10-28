package com.yasinjahantigh.logarhythm.data.ai

import com.yasinjahantigh.logarhythm.domain.model.AiFailure
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylistResult
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiPlaylistClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AiPlaylistClient

    private fun track(id: String, title: String = id) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/$id"
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AiPlaylistClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun openAiSettings() = AiSettings(
        provider = AiProvider.OPENAI_COMPATIBLE,
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        model = "test-model",
        hasKey = true
    )

    private fun geminiSettings() = AiSettings(
        provider = AiProvider.GEMINI,
        baseUrl = server.url("/v1beta").toString().trimEnd('/'),
        model = "gemini-test",
        hasKey = true
    )

    @Test
    fun parses_fenced_json_and_drops_unknown_ids() {
        val tracks = listOf(track("local:1"), track("local:2")).associateBy { it.id }
        val raw = """
            Here you go:
            ```json
            {"name":"Rainy Night","description":"Calm songs","trackIds":["local:1","ghost","local:2","local:1"]}
            ```
        """.trimIndent()
        val playlist = client.parsePlaylistJson(raw, tracks, requestedCount = 10)
        assertNotNull(playlist)
        assertEquals("Rainy Night", playlist!!.name)
        assertEquals(listOf("local:1", "local:2"), playlist.tracks.map { it.id })
    }

    @Test
    fun rejects_json_without_valid_tracks() {
        val tracks = mapOf("local:1" to track("local:1"))
        assertNull(client.parsePlaylistJson("""{"trackIds":["nope"]}""", tracks, 5))
        assertNull(client.parsePlaylistJson("no json at all", tracks, 5))
    }

    @Test
    fun builds_provider_urls() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            client.chatCompletionsUrl("https://api.openai.com/v1")
        )
        assertEquals(
            "https://example.com/chat/completions",
            client.chatCompletionsUrl("https://example.com/chat/completions")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
            client.geminiGenerateUrl(
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-2.0-flash"
            )
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/x:generateContent",
            client.geminiGenerateUrl(
                "https://generativelanguage.googleapis.com/v1beta/models",
                "models/x"
            )
        )
        assertEquals(
            "https://api.example.com/v1/models",
            client.modelsUrl(
                AiSettings(baseUrl = "https://api.example.com/v1/chat/completions", hasKey = true)
            )
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models",
            client.geminiModelsUrl(
                AiSettings(
                    provider = AiProvider.GEMINI,
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                    hasKey = true
                )
            )
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            client.geminiGenerateUrl(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                "ignored"
            )
        )
    }

    @Test
    fun openai_generation_returns_validated_tracks() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"name\":\"Focus\",\"description\":\"Deep work\",\"trackIds\":[\"local:1\",\"ghost\"]}"}}]}"""
            )
        )
        val result = client.generate(
            settings = openAiSettings(),
            apiKey = "secret",
            prompt = "focus music",
            trackCount = 10,
            catalog = listOf(track("local:1"), track("local:2"))
        )
        assertTrue(result is MagicPlaylistResult.Success)
        val playlist = (result as MagicPlaylistResult.Success).playlist
        assertEquals("Focus", playlist.name)
        assertEquals(listOf("local:1"), playlist.tracks.map { it.id })

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"response_format\""))
    }

    @Test
    fun gemini_generation_reads_parts_text() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"{\"name\":\"Night\",\"trackIds\":[\"local:2\"]}"}]}}]}"""
            )
        )
        val result = client.generate(
            settings = geminiSettings(),
            apiKey = "gemini-key",
            prompt = "night music",
            trackCount = 5,
            catalog = listOf(track("local:1"), track("local:2"))
        )
        assertTrue(result is MagicPlaylistResult.Success)
        assertEquals(
            listOf("local:2"),
            (result as MagicPlaylistResult.Success).playlist.tracks.map { it.id }
        )
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-test:generateContent", request.path)
        assertEquals("gemini-key", request.getHeader("x-goog-api-key"))
    }

    @Test
    fun maps_unauthorized_and_rate_limit_responses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val unauthorized = client.generate(
            openAiSettings(), "bad", "prompt", 5, listOf(track("local:1"))
        )
        assertEquals(
            MagicPlaylistResult.Failure(AiFailure.INVALID_KEY),
            unauthorized
        )

        server.enqueue(MockResponse().setResponseCode(429))
        val limited = client.generate(
            openAiSettings(), "key", "prompt", 5, listOf(track("local:1"))
        )
        assertEquals(MagicPlaylistResult.Failure(AiFailure.RATE_LIMITED), limited)
    }

    @Test
    fun test_connection_uses_models_endpoint() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        assertNull(client.testConnection(openAiSettings(), "key"))
        assertEquals("/v1/models", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("""{"models":[]}"""))
        assertNull(client.testConnection(geminiSettings(), "key"))
        assertEquals("/v1beta/models", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(AiFailure.INVALID_KEY, client.testConnection(openAiSettings(), "key"))
    }

    @Test
    fun user_prompt_lists_catalog_and_respects_count() {
        val prompt = client.buildUserPrompt(
            "rainy night",
            2,
            listOf(track("local:1", "First"), track("local:2", "Second"))
        )
        assertTrue(prompt.contains("rainy night"))
        assertTrue(prompt.contains("local:1 | First"))
        assertTrue(prompt.contains("exactly 2 songs"))
    }

    @Test
    fun mix_generation_keepsEveryBaseTrackAndOnlyAddsCandidates() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"name\":\"Calm Nights\",\"description\":\"softer\",\"trackIds\":[\"base:1\",\"cand:1\",\"ghost\"]}"}}]}"""
            )
        )
        val result = client.generateMix(
            settings = openAiSettings(),
            apiKey = "secret",
            prompt = "make it calmer",
            mixName = "Daily Mix 1",
            baseTracks = listOf(track("base:1"), track("base:2")),
            candidates = listOf(track("cand:1"), track("cand:2")),
            additions = 2
        )

        assertTrue(result is MagicPlaylistResult.Success)
        val playlist = (result as MagicPlaylistResult.Success).playlist
        assertEquals(listOf("base:1", "base:2", "cand:1"), playlist.tracks.map { it.id })
        assertEquals("Calm Nights", playlist.name)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Existing playlist"))
        assertTrue(body.contains("base:1"))
        assertTrue(body.contains("cand:1"))
        assertTrue(body.contains("never return these ids"))
    }

    @Test
    fun mix_prompt_skips_baseTracksBeyondTheSampleCap() {
        val base = (0 until 30).map { track("base:$it") }
        val candidates = (0 until 5).map { track("cand:$it") }
        val prompt = client.buildMixUserPrompt("softer", base, candidates, 3)

        assertTrue(prompt.contains("base:19"))
        assertFalse(prompt.contains("base:20"))
        assertTrue(prompt.contains("cand:4"))
        assertTrue(prompt.contains("exactly 3 new songs"))
    }
}
