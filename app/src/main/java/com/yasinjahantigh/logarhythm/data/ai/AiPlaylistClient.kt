package com.yasinjahantigh.logarhythm.data.ai

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

    suspend fun testConnection(settings: AiSettings, apiKey: String): AiFailure? =
        withContext(Dispatchers.IO) {
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
                    if (response.isSuccessful) null else failureForCode(response.code)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                AiFailure.NETWORK
            }
        }

    suspend fun generate(
        settings: AiSettings,
        apiKey: String,
        prompt: String,
        trackCount: Int,
        catalog: List<Track>
    ): MagicPlaylistResult = withContext(Dispatchers.IO) {
        if (catalog.isEmpty()) return@withContext MagicPlaylistResult.Failure(AiFailure.NO_TRACKS)
        val count = trackCount.coerceIn(1, catalog.size)
        performGeneration(
            settings = settings,
            apiKey = apiKey,
            system = SYSTEM_PROMPT,
            user = buildUserPrompt(prompt, count, catalog),
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
        additions: Int
    ): MagicPlaylistResult = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext MagicPlaylistResult.Failure(AiFailure.NO_TRACKS)
        val count = additions.coerceIn(1, candidates.size)
        val result = performGeneration(
            settings = settings,
            apiKey = apiKey,
            system = MIX_SYSTEM_PROMPT,
            user = buildMixUserPrompt(prompt, baseTracks, candidates, count),
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

    private suspend fun performGeneration(
        settings: AiSettings,
        apiKey: String,
        system: String,
        user: String,
        byId: Map<String, Track>,
        count: Int
    ): MagicPlaylistResult {
        val first = when (settings.provider) {
            AiProvider.OPENAI_COMPATIBLE -> openAiRequest(settings, apiKey, system, user, jsonMode = true)
            AiProvider.GEMINI -> geminiRequest(settings, apiKey, system, user)
        }

        return try {
            client.newCall(first).execute().use { response ->
                if (!response.isSuccessful) {
                    // Some OpenAI-compatible servers reject response_format; retry once without it.
                    if (settings.provider == AiProvider.OPENAI_COMPATIBLE && response.code == 400) {
                        val retry = openAiRequest(settings, apiKey, system, user, jsonMode = false)
                        return@use executeGeneration(retry, byId, count)
                    }
                    return MagicPlaylistResult.Failure(failureForCode(response.code))
                }
                val body = response.body?.string().orEmpty()
                val text = extractText(settings.provider, body)
                    ?: return MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
                parsePlaylistJson(text, byId, count)
                    ?.let { MagicPlaylistResult.Success(it) }
                    ?: MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            MagicPlaylistResult.Failure(AiFailure.NETWORK)
        }
    }

    private fun executeGeneration(
        request: Request,
        byId: Map<String, Track>,
        count: Int
    ): MagicPlaylistResult = try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                MagicPlaylistResult.Failure(failureForCode(response.code))
            } else {
                val text = extractText(AiProvider.OPENAI_COMPATIBLE, response.body?.string().orEmpty())
                val playlist = text?.let { parsePlaylistJson(it, byId, count) }
                if (playlist == null) MagicPlaylistResult.Failure(AiFailure.BAD_RESPONSE)
                else MagicPlaylistResult.Success(playlist)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        MagicPlaylistResult.Failure(AiFailure.NETWORK)
    }

    private fun openAiRequest(
        settings: AiSettings,
        apiKey: String,
        system: String,
        user: String,
        jsonMode: Boolean
    ): Request {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", settings.model)
            .put("temperature", 0.7)
            .put("messages", messages)
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
        user: String
    ): Request {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", system))
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user)))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.7)
            )
        return Request.Builder()
            .url(geminiGenerateUrl(settings.baseUrl, settings.model))
            .header("x-goog-api-key", apiKey)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    internal fun buildUserPrompt(prompt: String, count: Int, catalog: List<Track>): String = buildString {
        appendLine("Create a playlist from this specific music library. Choose exactly $count songs if possible.")
        appendLine("Request: ${prompt.trim()}")
        appendLine("Catalog format: id | title | artist | genre | year | seconds")
        catalog.forEach { track ->
            appendLine(compactLine(track, includeDuration = true))
        }
    }

    internal fun buildMixUserPrompt(
        prompt: String,
        baseTracks: List<Track>,
        candidates: List<Track>,
        count: Int
    ): String = buildString {
        appendLine("Rebuild an existing playlist. Keep its direction and add exactly $count new songs if possible.")
        appendLine("Request: ${prompt.trim()}")
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
            val obj = JSONObject(json)
            val ids = obj.optJSONArray("trackIds") ?: obj.optJSONArray("tracks") ?: return null
            val picked = mutableListOf<Track>()
            val seen = mutableSetOf<String>()
            for (index in 0 until ids.length()) {
                val id = ids.optString(index).takeIf { it.isNotBlank() } ?: continue
                val track = tracksById[id] ?: continue
                if (seen.add(track.id)) picked.add(track)
                if (picked.size >= requestedCount) break
            }
            if (picked.isEmpty()) return null
            val name = obj.optString("name").trim()
            val description = obj.optString("description").trim()
            MagicPlaylist(name = name.take(80), description = description.take(300), tracks = picked)
        } catch (_: Exception) {
            null
        }
    }

    internal fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        val fenced = FENCE.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return candidate.substring(start, end + 1)
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
                buildString {
                    for (index in 0 until (parts?.length() ?: 0)) {
                        append(parts?.optJSONObject(index)?.optString("text").orEmpty())
                    }
                }.takeIf { it.isNotBlank() }
            }
        }
    } catch (_: Exception) {
        null
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
                "provided by the user. Never invent ids. Reply with strict JSON only, no prose and " +
                "no code fences, using this shape: " +
                "{\"name\": \"short playlist name\", \"description\": \"one sentence\", " +
                "\"trackIds\": [\"exact ids from the catalog\"]}. " +
                "Respect the requested mood and language whenever the catalog allows it."

        const val MIX_SYSTEM_PROMPT =
            "You rebuild an existing daily mix for a personal music library. The existing songs " +
                "always stay; pick additional songs only from the candidate ids. Never invent ids " +
                "and never return ids from the existing playlist. Reply with strict JSON only, no " +
                "prose and no code fences, using this shape: " +
                "{\"name\": \"short mix name\", \"description\": \"one sentence\", " +
                "\"trackIds\": [\"candidate ids\"]}. Match the requested direction and keep the " +
                "mix varied."
    }
}
