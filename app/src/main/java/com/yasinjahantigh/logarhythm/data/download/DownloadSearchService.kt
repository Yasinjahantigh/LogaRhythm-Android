package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Combines yt-dlp sources with MusicMan, optionally limited to one user-picked source. */
@Singleton
class DownloadSearchService @Inject constructor(
    private val ytDlp: YtDlpSearchService,
    private val musicMan: MusicManClient
) {

    data class Outcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind? = null
    )

    suspend fun search(query: String, source: DownloadSource?): Outcome = coroutineScope {
        val needsYtDlp = source == null || source.usesYtDlp
        val ytDlpSources = when (source) {
            null -> setOf(DownloadSource.YOUTUBE, DownloadSource.SOUNDCLOUD)
            DownloadSource.MUSICMAN -> emptySet()
            else -> setOf(source)
        }
        val ytDlpJob = if (needsYtDlp) {
            async(Dispatchers.IO) { ytDlp.search(query, ytDlpSources) }
        } else {
            null
        }
        val musicManJob = if (source == null || source == DownloadSource.MUSICMAN) {
            async(Dispatchers.IO) {
                try {
                    SourceOutcome(musicMan.search(query), null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (pipeline: DownloadPipelineException) {
                    SourceOutcome(emptyList(), pipeline.kind)
                } catch (_: Exception) {
                    SourceOutcome(emptyList(), DownloadErrorKind.UNKNOWN)
                }
            }
        } else {
            null
        }

        val yt = ytDlpJob?.await()
        val mm = musicManJob?.await()
        val results = (mm?.results.orEmpty() + yt?.results.orEmpty())
        val failures = listOfNotNull(yt?.failure, mm?.failure)
        Outcome(
            results = results,
            failure = when {
                results.isNotEmpty() -> null
                failures.contains(DownloadErrorKind.NETWORK) -> DownloadErrorKind.NETWORK
                else -> failures.firstOrNull()
            }
        )
    }

    private data class SourceOutcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind?
    )
}
