package com.yasinjahantigh.logarhythm.data.ai

import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.AiFailure
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylist
import com.yasinjahantigh.logarhythm.domain.model.MagicPlaylistResult
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the provider the user configured. Two wire formats are supported: the OpenAI
 * chat-completions shape (also used by most local/self-hosted servers) and Google Gemini.
 */
@Singleton
class AiPlaylistClient @Inject constructor(
    baseClient: OkHttpClient
) {
    private val client = baseClient.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var lastErrorDetail: String? = null
        private set

    suspend fun testConnection(settings: AiSettings, apiKey: String): AiFailure? =
        withContext(Dispatchers.IO) {
            lastErrorDetail = null
            val request = when (settings.provider) {
                AiProvider.OPENAI_COMPATIBLE -> Request.Builder()
                    .url(modelsUrl(settings))
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                AiProvider.GEMINI -> Request.Builder()
                    .url(geminiModelsUrl(settings))
                    .header("x-goog-api-key", apiKey)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            }
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        null
                    } else if (response.code == 404 || response.code == 405) {
                        // Some OpenAI/Gemini compatible proxies do not expose /models; probe generate directly.
                        probeGeneration(settings, apiKey)
                    } else {
                        val errBody = response.body?.string().orEmpty()
                        lastErrorDetail = formatErrorDetail(response.code, errBody)
                        failureForCode(response.code)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (io: IOException) {
                lastErrorDetail = io.message
                AiFailure.NETWORK
            }
        }

    private fun probeGeneration(settings: AiSettings, apiKey: String): AiFailure? {
        val probe = when (settings.provider) {
            AiProvider.OPENAI_COMPATIBLE -> openAiRequest(
                settings = settings,
                apiKey = apiKey,
                system = "Reply OK.",
                user = "OK",
                jsonMode = false,
                includeTemperature = false
            )
            AiProvider.GEMINI -> geminiRequest(
                settings = settings,
                apiKey = apiKey,
                system = "Reply OK.",
                user = "OK",
                jsonMode = false
            )
        }
        return try {
            client.newCall(probe).execute().use { resp ->
                if (resp.isSuccessful) {
                    null
                } else {
                    lastErrorDetail = formatErrorDetail(resp.code, resp.body?.string().orEmpty())
                    failureForCode(resp.code)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            lastErrorDetail = io.message
            AiFailure.NETWORK
        }
    }

    suspend fun generate(
        settings: AiSettings,
        apiKey: String,
        prompt: String,
        trackCount: Int,
        catalog: List<Track>,
        tasteSummary: String = ""
    ): MagicPlaylistResult = withContext(Dispatchers.IO) {
        if (catalog.isEmpty()) return@withContext MagicPlaylistResult.Failure(AiFailure.NO_TRACKS)
        val count = trackCount.coerceIn(1, catalog.size)
        performGeneration(
            settings = settings,
            apiKey = apiKey,
            system = SYSTEM_PROMPT,
            user = buildUserPrompt(prompt, count, catalog, tasteSummary),
            byId = catalog.associateBy { it.id },
            count = count
        )
    }

    /** Asks the model for additions only; the existing mix itself is never re-picked. */
    suspend fun generateMix(
        settings: AiSettings,
        apiKey: String,
        prompt: String,
        mixName: String,
        baseTracks: List<Track>,
        candidates: List<Track>,
        additions: Int,
        tasteSummary: String = ""
    ): MagicPlaylistResult = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext MagicPlaylistResult.Failure(AiFailure.NO_TRACKS)
        val count = additions.coerceIn(1, candidates.size)
        val result = performGeneration(
            settings = settings,
            apiKey = apiKey,
            system = MIX_SYSTEM_PROMPT,
            user = buildMixUserPrompt(prompt, baseTracks, candidates, count, tasteSummary),
            byId = candidates.associateBy { it.id },
            count = count
        )
        if (result !is MagicPlaylistResult.Success) return@withContext result

        val baseIds = baseTracks.mapTo(HashSet()) { it.id }
        val additionsTracks = result.playlist.tracks.filter { it.id !in baseIds }
        if (additionsTracks.isEmpty()) {
            return@withContext MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
        }
        MagicPlaylistResult.Success(
            MagicPlaylist(
                name = result.playlist.name.ifBlank { mixName },
                description = result.playlist.description,
                tracks = (baseTracks + additionsTracks).distinctBy { it.id }
            )
        )
    }

    data class AiTagFix(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String = "",
        val year: Int = 0
    )

    suspend fun cleanMetadataWithAi(
        settings: AiSettings,
        apiKey: String,
        tracks: List<Track>
    ): Map<String, Triple<String, String, String>> =
        cleanMetadataTagsWithAi(settings, apiKey, tracks).mapValues { (_, v) ->
            Triple(v.title, v.artist, v.album)
        }

    suspend fun cleanMetadataTagsWithAi(
        settings: AiSettings,
        apiKey: String,
        tracks: List<Track>
    ): Map<String, AiTagFix> = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext emptyMap()
        val userPrompt = buildString {
            appendLine(
                "Clean and standardize these music tracks (English or Persian). Extract clean title and artist, " +
                    "remove noise ([Official Video], (Audio), [320], @channel, website URLs, download prefixes, " +
                    "leading track numbers), fix Persian ک/ی script, keep missing album blank if unsure, and infer " +
                    "concise genre (e.g. Pop, Persian Pop, Rap, Rock, Traditional, Electronic, Soundtrack) and " +
                    "4-digit release year (or 0 if unsure) whenever missing or Unknown."
            )
            appendLine("Format: id | title | artist | album | genre | year")
            tracks.forEach {
                appendLine("${it.id} | ${it.title} | ${it.artist} | ${it.album} | ${it.genre} | ${it.year}")
            }
        }
        val tagSystem =
            "You are a professional music tag cleaner and discography assistant. Output only valid JSON with shape: " +
                "{\"items\": [{\"id\":\"...\",\"title\":\"...\",\"artist\":\"...\",\"album\":\"...\",\"genre\":\"...\",\"year\":2022}]}"
        val request = when (settings.provider) {
            AiProvider.OPENAI_COMPATIBLE -> openAiRequest(
                settings = settings,
                apiKey = apiKey,
                system = tagSystem,
                user = userPrompt,
                jsonMode = true,
                includeTemperature = !isReasoningModel(settings.model)
            )
            AiProvider.GEMINI -> geminiRequest(
                settings = settings,
                apiKey = apiKey,
                system = tagSystem,
                user = userPrompt,
                jsonMode = true
            )
        }
        try {
            client.newCall(request).execute().use { response ->
                val actualResponse = if (!response.isSuccessful && response.code == 400) {
                    val fallback = when (settings.provider) {
                        AiProvider.OPENAI_COMPATIBLE -> openAiRequest(
                            settings = settings,
                            apiKey = apiKey,
                            system = tagSystem,
                            user = userPrompt,
                            jsonMode = false,
                            includeTemperature = false
                        )
                        AiProvider.GEMINI -> geminiRequest(
                            settings = settings,
                            apiKey = apiKey,
                            system = tagSystem,
                            user = userPrompt,
                            jsonMode = false
                        )
                    }
                    client.newCall(fallback).execute()
                } else {
                    response
                }
                actualResponse.use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyMap()
                    val text = extractText(settings.provider, resp.body?.string().orEmpty()) ?: return@withContext emptyMap()
                    val jsonStr = extractJsonObject(text) ?: return@withContext emptyMap()
                    val obj = JSONObject(jsonStr)
                    val items = obj.optJSONArray("items") ?: return@withContext emptyMap()
                    val result = mutableMapOf<String, AiTagFix>()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val title = MetadataNormalizer.cleanDisplayText(item.optString("title")).takeIf { it.isNotBlank() } ?: continue
                        val artist = MetadataNormalizer.cleanDisplayText(item.optString("artist")).takeIf { it.isNotBlank() } ?: continue
                        val album = MetadataNormalizer.cleanDisplayText(item.optString("album"))
                        val rawGenre = item.optString("genre")
                        val genre = MetadataNormalizer.canonicalizeGenre(rawGenre, fallback = "")
                        val year = item.optInt("year", 0).takeIf { it in 1900..2100 }
                            ?: item.optString("year").trim().take(4).toIntOrNull()?.takeIf { it in 1900..2100 }
                            ?: 0
                        result[id] = AiTagFix(title, artist, album, genre, year)
                    }
                    result
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun performGeneration(
        settings: AiSettings,
        apiKey: String,
        system: String,
        user: String,
        byId: Map<String, Track>,
        count: Int
    ): MagicPlaylistResult {
        lastErrorDetail = null
        val first = when (settings.provider) {
            AiProvider.OPENAI_COMPATIBLE -> openAiRequest(
                settings = settings,
                apiKey = apiKey,
                system = system,
                user = user,
                jsonMode = true,
                includeTemperature = !isReasoningModel(settings.model)
            )
            AiProvider.GEMINI -> geminiRequest(
                settings = settings,
                apiKey = apiKey,
                system = system,
                user = user,
                jsonMode = true
            )
        }

        return try {
            client.newCall(first).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    // Reasoning models (o1/o3/o4/gpt-5), some OpenAI proxies, and some Gemini/Gemma
                    // endpoints return HTTP 400 for response_format/responseMimeType/temperature; retry cleanly.
                    if (response.code == 400) {
                        val retry = when (settings.provider) {
                            AiProvider.OPENAI_COMPATIBLE -> openAiRequest(
                                settings = settings,
                                apiKey = apiKey,
                                system = system,
                                user = user,
                                jsonMode = false,
                                includeTemperature = false
                            )
                            AiProvider.GEMINI -> geminiRequest(
                                settings = settings,
                                apiKey = apiKey,
                                system = system,
                                user = user,
                                jsonMode = false,
                                inlineSystem = true
                            )
                        }
                        return@use executeGeneration(settings.provider, retry, byId, count)
                    }
                    lastErrorDetail = formatErrorDetail(response.code, errBody)
                    return MagicPlaylistResult.Failure(failureForCode(response.code))
                }
                val body = response.body?.string().orEmpty()
                val text = extractText(settings.provider, body)
                    ?: run {
                        lastErrorDetail = "Empty or filtered response from AI provider"
                        return MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
                    }
                parsePlaylistJson(text, byId, count)
                    ?.let { MagicPlaylistResult.Success(it) }
                    ?: run {
                        lastErrorDetail = "AI response did not contain valid track IDs"
                        MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
                    }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            lastErrorDetail = io.message
            MagicPlaylistResult.Failure(AiFailure.NETWORK)
        }
    }

    private fun executeGeneration(
        provider: AiProvider,
        request: Request,
        byId: Map<String, Track>,
        count: Int
    ): MagicPlaylistResult = try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                lastErrorDetail = formatErrorDetail(response.code, response.body?.string().orEmpty())
                MagicPlaylistResult.Failure(failureForCode(response.code))
            } else {
                val text = extractText(provider, response.body?.string().orEmpty())
                val playlist = text?.let { parsePlaylistJson(it, byId, count) }
                if (playlist == null) {
                    lastErrorDetail = "AI response did not contain valid track IDs"
                    MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
                } else {
                    MagicPlaylistResult.Success(playlist)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        lastErrorDetail = io.message
        MagicPlaylistResult.Failure(AiFailure.NETWORK)
    }

    private fun isReasoningModel(model: String): Boolean {
        val m = model.trim().substringAfterLast('/').lowercase()
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("gpt-5")
    }

    private fun openAiRequest(
        settings: AiSettings,
        apiKey: String,
        system: String,
        user: String,
        jsonMode: Boolean,
        includeTemperature: Boolean = true
    ): Request {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", settings.model)
            .put("messages", messages)
        if (includeTemperature) body.put("temperature", 0.7)
        if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))
        return Request.Builder()
            .url(chatCompletionsUrl(settings.baseUrl))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun geminiRequest(
        settings: AiSettings,
        apiKey: String,
        system: String,
        user: String,
        jsonMode: Boolean = true,
        inlineSystem: Boolean = false
    ): Request {
        val genConfig = JSONObject().put("temperature", 0.7)
        if (jsonMode) genConfig.put("responseMimeType", "application/json")
        val promptText = if (inlineSystem) "$system\n\n$user" else user
        val body = JSONObject()
            .apply {
                if (!inlineSystem) {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", system))
                        )
                    )
                }
            }
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", promptText)))
                )
            )
            .put("generationConfig", genConfig)
        return Request.Builder()
            .url(geminiGenerateUrl(settings.baseUrl, settings.model))
            .header("x-goog-api-key", apiKey)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    internal fun buildUserPrompt(
        prompt: String,
        count: Int,
        catalog: List<Track>,
        tasteSummary: String = ""
    ): String = buildString {
        appendLine("Create a playlist from this specific music library. Choose exactly $count songs if possible.")
        appendLine("Request: ${prompt.trim()}")
        if (tasteSummary.isNotBlank()) {
            appendLine("Listener profile: ${tasteSummary.trim()}")
        }
        appendLine("Catalog format: id | title | artist | genre | year | seconds")
        catalog.forEach { track ->
            appendLine(compactLine(track, includeDuration = true))
        }
    }

    internal fun buildMixUserPrompt(
        prompt: String,
        baseTracks: List<Track>,
        candidates: List<Track>,
        count: Int,
        tasteSummary: String = ""
    ): String = buildString {
        appendLine("Rebuild an existing playlist. Keep its direction and add exactly $count new songs if possible.")
        appendLine("Request: ${prompt.trim()}")
        if (tasteSummary.isNotBlank()) {
            appendLine("Listener profile: ${tasteSummary.trim()}")
        }
        appendLine("Existing playlist (never return these ids, they stay in the mix):")
        appendLine("id | title | artist | genre | year")
        baseTracks.take(MAX_PROMPT_BASE_TRACKS).forEach { appendLine(compactLine(it, includeDuration = false)) }
        appendLine("Candidate additions (choose only from these ids):")
        appendLine("id | title | artist | genre | year")
        candidates.forEach { appendLine(compactLine(it, includeDuration = false)) }
    }

    private fun compactLine(track: Track, includeDuration: Boolean): String = buildString {
        append(track.id)
        append(" | ")
        append(track.title.take(80))
        append(" | ")
        append(track.artist.take(60))
        append(" | ")
        append(track.genre.take(40))
        append(" | ")
        append(track.year)
        if (includeDuration) {
            append(" | ")
            append(track.durationMs / 1000)
        }
    }

    internal fun parsePlaylistJson(
        raw: String,
        tracksById: Map<String, Track>,
        requestedCount: Int
    ): MagicPlaylist? {
        val json = extractJsonObject(raw) ?: return null
        return try {
            val (ids, name, description) = if (json.startsWith("[")) {
                Triple(JSONArray(json), "", "")
            } else {
                val obj = JSONObject(json)
                val arr = obj.optJSONArray("trackIds")
                    ?: obj.optJSONArray("tracks")
                    ?: obj.optJSONArray("track_ids")
                    ?: obj.optJSONArray("ids")
                    ?: obj.optJSONArray("songs")
                    ?: return null
                Triple(arr, obj.optString("name").trim(), obj.optString("description").trim())
            }
            val picked = mutableListOf<Track>()
            val seen = mutableSetOf<String>()
            for (index in 0 until ids.length()) {
                val elemObj = ids.optJSONObject(index)
                val id = elemObj?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: ids.optString(index).takeIf { it.isNotBlank() }
                    ?: continue
                val track = tracksById[id] ?: continue
                if (seen.add(track.id)) picked.add(track)
                if (picked.size >= requestedCount) break
            }
            if (picked.isEmpty()) return null
            MagicPlaylist(name = name.take(80), description = description.take(300), tracks = picked)
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        val fenced = FENCE.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: trimmed
        val objStart = candidate.indexOf('{')
        val objEnd = candidate.lastIndexOf('}')
        if (objStart >= 0 && objEnd > objStart) {
            return candidate.substring(objStart, objEnd + 1)
        }
        val arrStart = candidate.indexOf('[')
        val arrEnd = candidate.lastIndexOf(']')
        if (arrStart >= 0 && arrEnd > arrStart) {
            return candidate.substring(arrStart, arrEnd + 1)
        }
        return null
    }

    private fun extractText(provider: AiProvider, body: String): String? = try {
        val obj = JSONObject(body)
        when (provider) {
            AiProvider.OPENAI_COMPATIBLE -> obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }

            AiProvider.GEMINI -> {
                val parts = obj.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val nonThought = buildString {
                    for (index in 0 until (parts?.length() ?: 0)) {
                        val part = parts?.optJSONObject(index) ?: continue
                        if (part.optBoolean("thought", false)) continue
                        append(part.optString("text"))
                    }
                }.trim()
                if (nonThought.isNotBlank()) {
                    nonThought
                } else {
                    buildString {
                        for (index in 0 until (parts?.length() ?: 0)) {
                            append(parts?.optJSONObject(index)?.optString("text").orEmpty())
                        }
                    }.trim().takeIf { it.isNotBlank() }
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun formatErrorDetail(code: Int, rawBody: String): String {
        val msg = runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return if (!msg.isNullOrBlank()) "HTTP $code: ${msg.take(180)}" else "HTTP $code"
    }

    private fun failureForCode(code: Int): AiFailure = when {
        code == 401 || code == 403 -> AiFailure.INVALID_KEY
        code == 429 -> AiFailure.RATE_LIMITED
        code >= 500 -> AiFailure.SERVER
        else -> AiFailure.SERVER
    }

    internal fun chatCompletionsUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    internal fun modelsUrl(settings: AiSettings): String {
        val base = settings.baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        return if (base.endsWith("/models")) base else "$base/models"
    }

    internal fun geminiModelsUrl(settings: AiSettings): String {
        val root = settings.baseUrl.trim().trimEnd('/')
            .substringBefore(":generateContent")
            .substringBefore("/models/")
            .removeSuffix("/models")
        return "$root/models"
    }

    internal fun geminiGenerateUrl(baseUrl: String, model: String): String {
        val base = baseUrl.trim().trimEnd('/').removeSuffix("/models")
        if (base.contains(":generateContent")) return base
        val cleanModel = model.trim().removePrefix("models/")
        return "$base/models/$cleanModel:generateContent"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```")

        const val MAX_PROMPT_BASE_TRACKS = 20

        const val SYSTEM_PROMPT =
            "You curate playlists for a personal music library. Pick songs only from the catalog " +
                "provided by the user. Never invent ids. Order selected trackIds with smooth energy " +
                "and mood flow, spread artists out instead of clumping one artist repeatedly, and " +
                "use the listener profile when compatible with the request. Reply with strict JSON only, " +
                "no prose and no code fences, using this shape: " +
                "{\"name\": \"short playlist name\", \"description\": \"one sentence\", " +
                "\"trackIds\": [\"exact ids from the catalog\"]}. " +
                "Respect the requested mood and language whenever the catalog allows it."

        const val MIX_SYSTEM_PROMPT =
            "You rebuild an existing daily mix for a personal music library. The existing songs " +
                "always stay; pick additional songs only from the candidate ids. Never invent ids " +
                "and never return ids from the existing playlist. Keep energy transitions smooth, " +
                "avoid repeating too many songs from the same artist, and consider the listener profile. " +
                "Reply with strict JSON only, no prose and no code fences, using this shape: " +
                "{\"name\": \"short mix name\", \"description\": \"one sentence\", " +
                "\"trackIds\": [\"candidate ids\"]}. Match the requested direction and keep the " +
                "mix varied."
    }
}
