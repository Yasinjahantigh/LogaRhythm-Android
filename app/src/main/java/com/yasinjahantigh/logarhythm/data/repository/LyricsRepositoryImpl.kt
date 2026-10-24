package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsCircuitBreaker
import com.yasinjahantigh.logarhythm.data.lyrics.LyricsSearchResult
import com.yasinjahantigh.logarhythm.data.lyrics.OnlineLyricsSource
import com.yasinjahantigh.logarhythm.data.room.LyricsDao
import com.yasinjahantigh.logarhythm.data.room.LyricsEntity
import com.yasinjahantigh.logarhythm.data.sources.CustomLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.GeniusLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.KugouLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.LocalFileLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.LrclibLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.LyricsOvhLyricsSource
import com.yasinjahantigh.logarhythm.data.sources.NeteaseLyricsSource
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsMatchScore
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsMatchScorer
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsMismatch
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceStatus
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsSourceTestResult
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsTextNormalizer
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.LyricsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves lyrics from the Room cache, embedded/local .lrc data, then online providers raced in
 * parallel under a hard deadline. A confident synced result wins; plain results need a stronger
 * metadata match. Manual choices are locked and never replaced by cache refreshes.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val localFileLyricsSource: LocalFileLyricsSource,
    private val lrclib: LrclibLyricsSource,
    private val netease: NeteaseLyricsSource,
    private val kugou: KugouLyricsSource,
    private val lyricsOvh: LyricsOvhLyricsSource,
    private val genius: GeniusLyricsSource,
    private val custom: CustomLyricsSource,
    private val circuitBreaker: LyricsCircuitBreaker,
    private val preferencesManager: PreferencesManager
) : LyricsRepository {

    override fun getLyrics(track: Track): Flow<Lyrics?> {
        return lyricsDao.getLyricsForTrack(track.id).map { entity ->
            if (entity == null || entity.source == "deleted" || entity.content.isBlank()) {
                null
            } else {
                LrcParser.parse(entity.content).copy(source = entity.source)
            }
        }
    }

    override suspend fun loadLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val cached = lyricsDao.getLyricsForTrackOnce(track.id)
        if (cached?.source == "deleted") return@withContext null

        val cachedLyrics = cached?.takeIf { it.content.isNotBlank() }?.let {
            LrcParser.parse(it.content).copy(source = it.source).takeIf { parsed -> !parsed.isEmpty }
        }
        if (cachedLyrics != null && isFresh(cached)) return@withContext cachedLyrics

        // Companion .lrc or embedded tags still beat a network lookup.
        val local = localFileLyricsSource.getLyrics(track)
        if (local != null && !local.isEmpty) {
            cacheLyrics(track.id, local, local.source ?: "local_file")
            return@withContext local
        }

        val online = searchBest(track)
        if (online != null) {
            cacheLyrics(track.id, online.lyrics, online.providerId)
            return@withContext online.lyrics.copy(source = online.providerId)
        }

        // A stale cached copy is still better than an empty screen.
        cachedLyrics
    }

    override suspend fun fetchOnlineLyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val found = searchBest(track) ?: return@withContext null
        cacheLyrics(track.id, found.lyrics, found.providerId)
        found.lyrics.copy(source = found.providerId)
    }

    override suspend fun searchLyricsCandidates(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val query = LyricsQuery(title, artist, album, durationMs)
        val providers = enabledProviders()
        if (providers.isEmpty()) return@withContext emptyList()

        val results = raceProviders(providers, query, stopOnConfidentSynced = false)
        val scored = results.flatMap { (_, result) ->
            result.candidates.map { candidate ->
                candidate to LyricsMatchScorer.match(query, candidate)
            }
        }.filter { (candidate, match) ->
            !candidate.lyrics.isEmpty && match.total > 0
        }

        val seen = HashSet<String>()
        scored
            .sortedWith(
                compareByDescending<Pair<LyricsCandidate, LyricsMatchScore>> {
                    if (it.first.isSynced) 1 else 0
                }.thenByDescending { it.second.total }
            )
            .map { it.first }
            .filter { candidate ->
                val key = buildString {
                    append(candidate.providerId).append('|')
                    append(LyricsTextNormalizer.normalize(candidate.trackName)).append('|')
                    append(LyricsTextNormalizer.normalize(candidate.artistName)).append('|')
                    append(candidate.isSynced)
                }
                seen.add(key)
            }
            .take(MAX_CANDIDATES)
    }

    override suspend fun saveCandidate(track: Track, candidate: LyricsCandidate): Boolean =
        withContext(Dispatchers.IO) {
            val content = formatLyricsToLrc(candidate.lyrics).trim()
            if (content.isBlank()) return@withContext false
            val source = "manual:${candidate.providerId}"
            lyricsDao.insertLyrics(
                LyricsEntity(
                    trackId = track.id,
                    content = content,
                    isSynced = candidate.lyrics.isSynced,
                    source = source,
                    updatedAt = System.currentTimeMillis()
                )
            )
            localFileLyricsSource.saveEmbeddedLyrics(track, content)
        }

    override suspend fun testLyricsSources(
        title: String,
        artist: String
    ): List<LyricsSourceTestResult> = withContext(Dispatchers.IO) {
        val query = LyricsQuery(title, artist)
        coroutineScope {
            enabledProviders().map { provider ->
                async {
                    circuitBreaker.reset(provider.id)
                    val started = System.currentTimeMillis()
                    val result = try {
                        withTimeout(PROVIDER_TIMEOUT_MS) { provider.search(query) }
                    } catch (timeout: TimeoutCancellationException) {
                        LyricsSearchResult(status = LyricsSourceStatus.TIMEOUT)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        LyricsSearchResult(status = LyricsSourceStatus.ERROR, detail = error.message)
                    }
                    if (result.status == LyricsSourceStatus.OK || result.status == LyricsSourceStatus.EMPTY) {
                        circuitBreaker.recordSuccess(provider.id)
                    } else {
                        circuitBreaker.recordFailure(provider.id)
                    }
                    LyricsSourceTestResult(
                        id = provider.id,
                        displayName = provider.displayName,
                        status = result.status,
                        latencyMs = System.currentTimeMillis() - started,
                        candidateCount = result.candidates.size,
                        detail = result.detail
                    )
                }
            }.awaitAll()
        }
    }

    override suspend fun saveLyrics(track: Track, content: String, source: String): Boolean =
        withContext(Dispatchers.IO) {
            val cleanContent = content.trim()
            if (cleanContent.isBlank()) return@withContext false
            val parsed = LrcParser.parse(cleanContent)
            lyricsDao.insertLyrics(
                LyricsEntity(
                    trackId = track.id,
                    content = cleanContent,
                    isSynced = parsed.isSynced,
                    source = source,
                    updatedAt = System.currentTimeMillis()
                )
            )
            localFileLyricsSource.saveEmbeddedLyrics(track, cleanContent)
        }

    override suspend fun deleteLyrics(trackId: String) = withContext(Dispatchers.IO) {
        lyricsDao.insertLyrics(
            LyricsEntity(
                trackId = trackId,
                content = "",
                isSynced = false,
                source = "deleted",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteLyrics(track: Track, removeFromFile: Boolean): Boolean = withContext(Dispatchers.IO) {
        lyricsDao.insertLyrics(
            LyricsEntity(
                trackId = track.id,
                content = "",
                isSynced = false,
                source = "deleted",
                updatedAt = System.currentTimeMillis()
            )
        )
        localFileLyricsSource.deleteCompanionLrc(track)
        if (!removeFromFile) return@withContext false
        localFileLyricsSource.clearEmbeddedLyrics(track).isSuccess
    }

    override fun getOffset(trackId: String): Flow<Long> =
        lyricsDao.getOffset(trackId).map { it ?: 0L }

    override suspend fun setOffset(trackId: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        if (offsetMs == 0L) lyricsDao.clearOffset(trackId)
        else lyricsDao.setOffset(com.yasinjahantigh.logarhythm.data.room.LyricsOffsetEntity(trackId, offsetMs))
    }

    override fun getHiddenTrackIds(): Flow<List<String>> = lyricsDao.getHiddenTrackIds()

    override suspend fun resetLyricsToDefault(trackId: String) = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyricsForTrack(trackId)
    }

    // ---- online lookup ---------------------------------------------------------------------

    private data class OnlineHit(val lyrics: Lyrics, val providerId: String)

    private suspend fun enabledProviders(): List<OnlineLyricsSource> {
        val prefs = preferencesManager.lyricsSourcePreferencesNow()
        return buildList {
            if (prefs.lrclib) add(lrclib)
            if (prefs.netease) add(netease)
            if (prefs.kugou) add(kugou)
            if (prefs.lyricsOvh) add(lyricsOvh)
            if (prefs.genius) add(genius)
            if (prefs.customUsable) add(custom)
        }
    }

    private suspend fun searchBest(track: Track): OnlineHit? {
        val providers = enabledProviders()
        if (providers.isEmpty()) return null
        val query = LyricsQuery(track.title, track.artist, track.album, track.durationMs)
        val results = raceProviders(providers, query, stopOnConfidentSynced = true)
        val scored = results.flatMap { (_, result) ->
            result.candidates.map { candidate -> candidate to LyricsMatchScorer.match(query, candidate) }
        }.filter { (candidate, match) ->
            !candidate.lyrics.isEmpty && match.confident &&
                !(candidate.isSynced && LyricsMismatch.isLikely(candidate.lyrics.lines, track.durationMs))
        }

        val bestSynced = scored
            .filter { it.first.isSynced }
            .maxByOrNull { it.second.total }
        val bestPlain = scored
            .filter { !it.first.isSynced }
            .maxByOrNull { it.second.total }
        val best = bestSynced ?: bestPlain ?: return null
        return OnlineHit(best.first.lyrics, best.first.providerId)
    }

    /**
     * Runs providers concurrently and returns before the slowest source when a confident synced
     * result arrives. The per-provider and overall deadlines keep a blocked host from freezing
     * the lyrics screen.
     */
    private suspend fun raceProviders(
        providers: List<OnlineLyricsSource>,
        query: LyricsQuery,
        stopOnConfidentSynced: Boolean
    ): List<Pair<OnlineLyricsSource, LyricsSearchResult>> = supervisorScope {
        val jobs = providers.map { provider ->
            provider to async {
                if (circuitBreaker.isOpen(provider.id)) {
                    return@async LyricsSearchResult(
                        status = LyricsSourceStatus.BLOCKED,
                        detail = "Temporarily skipped after failures"
                    )
                }
                val result = try {
                    withTimeout(PROVIDER_TIMEOUT_MS) { provider.search(query) }
                } catch (timeout: TimeoutCancellationException) {
                    LyricsSearchResult(status = LyricsSourceStatus.TIMEOUT)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    LyricsSearchResult(status = LyricsSourceStatus.ERROR, detail = error.message)
                }
                if (result.status == LyricsSourceStatus.OK || result.status == LyricsSourceStatus.EMPTY) {
                    circuitBreaker.recordSuccess(provider.id)
                } else {
                    circuitBreaker.recordFailure(provider.id)
                }
                result
            }
        }

        val results = mutableListOf<Pair<OnlineLyricsSource, LyricsSearchResult>>()
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        for ((provider, deferred) in jobs) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                deferred.cancel()
                continue
            }
            val result = withTimeoutOrNull(remaining) { deferred.await() }
            if (result == null) {
                deferred.cancel()
                continue
            }
            results += provider to result
            if (stopOnConfidentSynced && result.candidates.any { candidate ->
                    candidate.isSynced && LyricsMatchScorer.match(query, candidate).confident
                }
            ) {
                jobs.forEach { (_, job) -> job.cancel() }
                break
            }
        }
        results
    }

    private fun isFresh(entity: LyricsEntity): Boolean {
        if (entity.source.startsWith("manual") ||
            entity.source == "user_manual" ||
            entity.source == "user_clipboard" ||
            entity.source == "imported_file"
        ) return true
        if (entity.updatedAt <= 0L) return false
        return System.currentTimeMillis() - entity.updatedAt < CACHE_TTL_MS
    }

    private suspend fun cacheLyrics(trackId: String, lyrics: Lyrics, source: String) {
        val raw = formatLyricsToLrc(lyrics)
        lyricsDao.insertLyrics(
            LyricsEntity(
                trackId = trackId,
                content = raw,
                isSynced = lyrics.isSynced,
                source = source,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun formatLyricsToLrc(lyrics: Lyrics): String {
        return if (lyrics.isSynced) {
            fun stamp(ms: Long) = String.format(
                java.util.Locale.ROOT, "%02d:%02d.%02d", ms / 60_000L, (ms % 60_000L) / 1_000L, (ms % 1_000L) / 10L
            )
            lyrics.lines.joinToString("\n") { line ->
                val body = if (line.words.isEmpty()) line.text
                else line.words.joinToString("") { "<${stamp(it.startMs)}>${it.text}" }
                "[${stamp(line.timestampMs)}]$body"
            }
        } else {
            lyrics.plainText.orEmpty()
        }
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 6_000L
        const val TOTAL_BUDGET_MS = 7_000L
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_CANDIDATES = 30
    }
}
