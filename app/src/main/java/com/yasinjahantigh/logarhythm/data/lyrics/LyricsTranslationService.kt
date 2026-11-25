package com.yasinjahantigh.logarhythm.data.lyrics

import android.content.Context
import android.net.Uri
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsInstrumentalDetector
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsTranslationParser
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Free, zero-API-key bilingual lyrics translation using Google Translate (GTX batch + line fallback)
 * and MyMemory fallback, with offline file caching per (trackId, lyricsHash, targetLang).
 */
@Singleton
class LyricsTranslationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val cacheDir: File by lazy {
        File(context.filesDir, "lyrics_translations").apply { mkdirs() }
    }

    suspend fun translateLyrics(
        trackId: String,
        lyrics: Lyrics,
        targetLang: String
    ): List<String>? = withContext(Dispatchers.IO) {
        val cleanLang = targetLang.trim().lowercase()
        if (cleanLang.isBlank()) return@withContext null

        val sourceLines = extractLines(lyrics)
        if (sourceLines.isEmpty()) return@withContext emptyList()

        val lyricsHash = sha256(sourceLines.joinToString("\n"))
        val cacheFile = File(cacheDir, "${sha256("${trackId}_${lyricsHash}_$cleanLang")}.json")

        readFromCache(cacheFile, sourceLines.size)?.let { cached ->
            return@withContext cached
        }

        val translatableIndices = sourceLines.indices.filter { idx ->
            val line = sourceLines[idx].trim()
            line.isNotEmpty() && !LyricsInstrumentalDetector.isInstrumentalMarker(line)
        }
        if (translatableIndices.isEmpty()) return@withContext List(sourceLines.size) { "" }

        val result = MutableList(sourceLines.size) { "" }
        val chunks = chunkIndices(translatableIndices, sourceLines, maxLines = 14, maxChars = 1400)
        var anyTranslated = false

        for (chunk in chunks) {
            val chunkInputs = chunk.map { sourceLines[it].trim() }
            val joined = chunkInputs.joinToString("\n")
            val batchGtx = fetchGtx(joined, cleanLang)
            val aligned = batchGtx?.let { LyricsTranslationParser.alignBatchTranslations(chunkInputs, it) }

            if (aligned != null) {
                for (i in chunk.indices) {
                    val translated = aligned[i]
                    if (translated.isNotBlank()) {
                        result[chunk[i]] = translated
                        anyTranslated = true
                    }
                }
            } else {
                // Fallback: translate lines individually with bounded concurrency (GTX -> MyMemory)
                val semaphore = Semaphore(3)
                coroutineScope {
                    chunk.map { lineIdx ->
                        async {
                            semaphore.withPermit {
                                val text = sourceLines[lineIdx].trim()
                                val translated = fetchGtx(text, cleanLang)
                                    ?: fetchMyMemory(text, cleanLang)
                                if (!translated.isNullOrBlank()) {
                                    lineIdx to translated
                                } else {
                                    null
                                }
                            }
                        }
                    }.awaitAll().forEach { pair ->
                        if (pair != null) {
                            result[pair.first] = pair.second
                            anyTranslated = true
                        }
                    }
                }
            }
        }

        if (!anyTranslated) return@withContext null
        writeToCache(cacheFile, cleanLang, lyricsHash, result)
        result
    }

    private fun extractLines(lyrics: Lyrics): List<String> =
        if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            lyrics.lines.map { it.text }
        } else {
            lyrics.plainText.orEmpty().lines()
        }

    private fun chunkIndices(
        indices: List<Int>,
        sourceLines: List<String>,
        maxLines: Int,
        maxChars: Int
    ): List<List<Int>> {
        val chunks = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var charCount = 0
        for (idx in indices) {
            val len = sourceLines[idx].trim().length + 1
            if (current.isNotEmpty() && (current.size >= maxLines || charCount + len > maxChars)) {
                chunks.add(current)
                current = mutableListOf()
                charCount = 0
            }
            current.add(idx)
            charCount += len
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    private fun fetchGtx(text: String, targetLang: String): String? {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" +
            Uri.encode(targetLang) + "&dt=t&q=" + Uri.encode(text)
        return executeGet(url)?.let(LyricsTranslationParser::parseGtxResponse)
    }

    private fun fetchMyMemory(text: String, targetLang: String): String? {
        val sourceLang = LyricsTranslationParser.detectSourceLanguage(text, targetLang)
        val url = "https://api.mymemory.translated.net/get?q=" +
            Uri.encode(text) + "&langpair=" + Uri.encode("$sourceLang|$targetLang")
        return executeGet(url)?.let(LyricsTranslationParser::parseMyMemoryResponse)
    }

    private fun executeGet(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun readFromCache(file: File, expectedCount: Int): List<String>? {
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val arr = json.optJSONArray("lines") ?: return null
            if (arr.length() != expectedCount) return null
            List(expectedCount) { i -> arr.optString(i, "") }
        }.getOrNull()
    }

    private fun writeToCache(
        file: File,
        targetLang: String,
        lyricsHash: String,
        lines: List<String>
    ) {
        runCatching {
            val json = JSONObject().apply {
                put("targetLang", targetLang)
                put("lyricsHash", lyricsHash)
                put("lines", JSONArray(lines))
            }
            file.writeText(json.toString())
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
