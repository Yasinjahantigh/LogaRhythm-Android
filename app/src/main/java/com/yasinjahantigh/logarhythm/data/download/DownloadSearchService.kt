package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.analytics.AppLog
import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanClient
import com.yasinjahantigh.logarhythm.data.download.saavn.SaavnClient
import com.yasinjahantigh.logarhythm.domain.download.ArtistMerger
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines fast native HTTP APIs (MusicMan mm.3rah.ir, Deezer, JioSaavn, Radio Javan)
 * with yt-dlp fallback, streaming progressive results immediately as fast APIs respond.
 */
@Singleton
class DownloadSearchService @Inject constructor(
    private val ytDlp: YtDlpSearchService,
    private val musicMan: MusicManClient,
    private val saavn: SaavnClient,
    private val radioJavan: RadioJavanClient,
    private val deezer: DeezerClient,
    private val sourceRouter: DownloadSourceRouter
) {

    data class Outcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind? = null,
        val isFinal: Boolean = true
    )

    fun searchFlow(
        query: String,
        source: DownloadSource?,
        category: DownloadSearchCategory = DownloadSearchCategory.ALL
    ): Flow<Outcome> = channelFlow {
        val mutex = Mutex()
        val byBucket = linkedMapOf<String, List<DownloadSearchResult>>()
        val failures = mutableListOf<DownloadErrorKind>()

        // Resolve initial VPN-aware bucket order (MusicMan first when VPN is off; fastest source when VPN is on)
        var bucketOrder = sourceRouter.rankedSearchBuckets()
        AppLog.i(
            "DownloadSearch",
            "Start q='${query.take(40)}' src=${source?.raw ?: "ALL"} cat=${category.name} vpn=${sourceRouter.isVpnActive()} order=${bucketOrder.joinToString(",")}"
        )

        suspend fun publish(isFinal: Boolean) {
            // Re-read fastest order after live timings arrive while VPN is active
            if (sourceRouter.isVpnActive()) {
                bucketOrder = sourceRouter.rankedSearchBuckets()
            }
            mutex.withLock {
                val rawOrdered = buildList {
                    bucketOrder.forEach { key ->
                        addAll(byBucket[key].orEmpty())
                    }
                }.distinctBy { "${it.kind.name}:${it.source.raw}:${it.id}" }

                val ordered = ArtistMerger.mergeArtistResults(rawOrdered)

                send(
                    Outcome(
                        results = ordered,
                        failure = when {
                            ordered.isNotEmpty() -> null
                            failures.contains(DownloadErrorKind.NETWORK) -> DownloadErrorKind.NETWORK
                            else -> failures.firstOrNull()
                        },
                        isFinal = isFinal
                    )
                )
            }
        }

        val jobs = mutableListOf<kotlinx.coroutines.Job>()

        if (source == null || source == DownloadSource.MUSICMAN) {
            jobs += launch(Dispatchers.IO) {
                val started = System.nanoTime()
                val outcome = safeRun {
                    withTimeoutOrNull(FAST_API_TIMEOUT_MS) { musicMan.search(query, category) }
                        ?: throw DownloadPipelineException(DownloadErrorKind.NETWORK, "MusicMan timed out")
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                sourceRouter.recordLatency(DownloadSource.MUSICMAN, elapsedMs, outcome.failure == null)
                mutex.withLock {
                    byBucket["musicman"] = outcome.results
                    outcome.failure?.let { failures += it }
                }
                publish(isFinal = false)
            }
        }

        if (source == null || source == DownloadSource.DEEZER) {
            jobs += launch(Dispatchers.IO) {
                val started = System.nanoTime()
                val outcome = safeRun {
                    withTimeoutOrNull(FAST_API_TIMEOUT_MS) {
                        when (category) {
                            DownloadSearchCategory.TRACK -> deezer.searchDownload(query)
                            DownloadSearchCategory.ALBUM -> deezer.searchAlbums(query)
                            DownloadSearchCategory.ARTIST -> deezer.searchArtists(query)
                            DownloadSearchCategory.ALL -> coroutineScope {
                                val tracks = async { deezer.searchDownload(query, 15) }
                                val albums = async { deezer.searchAlbums(query, 8) }
                                val artists = async { deezer.searchArtists(query, 6) }
                                artists.await() + albums.await() + tracks.await()
                            }
                        }
                    }.orEmpty()
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                sourceRouter.recordLatency(DownloadSource.DEEZER, elapsedMs, outcome.failure == null && outcome.results.isNotEmpty())
                mutex.withLock {
                    byBucket["deezer"] = outcome.results
                    outcome.failure?.let { failures += it }
                }
                publish(isFinal = false)
            }
        }

        if ((source == null || source == DownloadSource.RADIOJAVAN) &&
            (category == DownloadSearchCategory.ALL || category == DownloadSearchCategory.TRACK || category == DownloadSearchCategory.ARTIST)
        ) {
            jobs += launch(Dispatchers.IO) {
                val started = System.nanoTime()
                val outcome = safeRun {
                    withTimeoutOrNull(FAST_API_TIMEOUT_MS) { radioJavan.search(query) }.orEmpty()
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                sourceRouter.recordLatency(DownloadSource.RADIOJAVAN, elapsedMs, outcome.failure == null && outcome.results.isNotEmpty())
                mutex.withLock {
                    byBucket["radiojavan"] = outcome.results
                    outcome.failure?.let { failures += it }
                }
                publish(isFinal = false)
            }
        }

        if ((source == null || source == DownloadSource.SAAVN) &&
            (category == DownloadSearchCategory.ALL || category == DownloadSearchCategory.TRACK)
        ) {
            jobs += launch(Dispatchers.IO) {
                val started = System.nanoTime()
                val outcome = safeRun {
                    withTimeoutOrNull(FAST_API_TIMEOUT_MS) { saavn.search(query) }.orEmpty()
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                sourceRouter.recordLatency(DownloadSource.SAAVN, elapsedMs, outcome.failure == null && outcome.results.isNotEmpty())
                mutex.withLock {
                    byBucket["saavn"] = outcome.results
                    outcome.failure?.let { failures += it }
                }
                publish(isFinal = false)
            }
        }

        jobs.forEach { it.join() }

        val fastCount = mutex.withLock { byBucket.values.sumOf { it.size } }
        val explicitYtDlp = source?.usesYtDlp == true
        val fallbackYtDlp = source == null &&
            (category == DownloadSearchCategory.ALL || category == DownloadSearchCategory.TRACK) &&
            fastCount == 0

        if (explicitYtDlp || fallbackYtDlp) {
            val ytSources = when (source) {
                null -> setOf(DownloadSource.YOUTUBE, DownloadSource.SOUNDCLOUD, DownloadSource.BANDCAMP)
                else -> setOf(source)
            }
            AppLog.i("DownloadSearch", "Trigger yt-dlp q='${query.take(40)}' explicit=$explicitYtDlp fastCount=$fastCount")
            val ytOutcome = withTimeoutOrNull(YT_FALLBACK_TIMEOUT_MS) {
                ytDlp.search(query, ytSources)
            }
            if (ytOutcome == null) {
                AppLog.w("DownloadSearch", "yt-dlp search timed out (${YT_FALLBACK_TIMEOUT_MS}ms) q='${query.take(40)}'")
            } else {
                AppLog.i("DownloadSearch", "yt-dlp search results=${ytOutcome.results.size} fail=${ytOutcome.failure?.raw}")
            }
            mutex.withLock {
                if (ytOutcome != null) {
                    byBucket["ytdlp"] = ytOutcome.results
                    ytOutcome.failure?.let { failures += it }
                }
            }
        }

        val summary = mutex.withLock {
            byBucket.entries.joinToString(" ") { "${it.key}=${it.value.size}" }
        }
        AppLog.i("DownloadSearch", "Done q='${query.take(40)}' [$summary] fails=${failures.joinToString(",") { it.raw }}")
        publish(isFinal = true)
    }

    suspend fun search(
        query: String,
        source: DownloadSource?,
        category: DownloadSearchCategory = DownloadSearchCategory.ALL
    ): Outcome {
        var latest = Outcome(emptyList())
        searchFlow(query, source, category).collect { latest = it }
        return latest
    }

    private suspend inline fun safeRun(
        crossinline block: suspend () -> List<DownloadSearchResult>
    ): SourceOutcome {
        return try {
            SourceOutcome(block(), null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pipeline: DownloadPipelineException) {
            SourceOutcome(emptyList(), pipeline.kind)
        } catch (_: Exception) {
            SourceOutcome(emptyList(), DownloadErrorKind.UNKNOWN)
        }
    }

    private data class SourceOutcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind?
    )

    private companion object {
        const val FAST_API_TIMEOUT_MS = 10_000L
        const val YT_FALLBACK_TIMEOUT_MS = 22_000L
    }
}
