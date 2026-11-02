package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.data.download.metadata.DeezerClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanClient
import com.yasinjahantigh.logarhythm.data.download.saavn.SaavnClient
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

/** Combines Persian (Radio Javan) and International (Deezer, MusicMan, JioSaavn, YouTube, SoundCloud) sources. */
@Singleton
class DownloadSearchService @Inject constructor(
    private val ytDlp: YtDlpSearchService,
    private val musicMan: MusicManClient,
    private val saavn: SaavnClient,
    private val radioJavan: RadioJavanClient,
    private val deezer: DeezerClient
) {

    data class Outcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind? = null
    )

    suspend fun search(query: String, source: DownloadSource?): Outcome = coroutineScope {
        val needsYtDlp = source == null || source.usesYtDlp
        val ytDlpSources = when (source) {
            null -> setOf(DownloadSource.YOUTUBE, DownloadSource.SOUNDCLOUD, DownloadSource.BANDCAMP)
            DownloadSource.MUSICMAN, DownloadSource.SAAVN, DownloadSource.RADIOJAVAN, DownloadSource.DEEZER -> emptySet()
            else -> setOf(source)
        }
        val ytDlpJob = if (needsYtDlp) {
            async(Dispatchers.IO) { ytDlp.search(query, ytDlpSources) }
        } else {
            null
        }

        val radioJavanJob = if (source == null || source == DownloadSource.RADIOJAVAN) {
            async(Dispatchers.IO) {
                try {
                    SourceOutcome(radioJavan.search(query), null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SourceOutcome(emptyList(), DownloadErrorKind.UNKNOWN)
                }
            }
        } else {
            null
        }

        val deezerJob = if (source == null || source == DownloadSource.DEEZER) {
            async(Dispatchers.IO) {
                try {
                    SourceOutcome(deezer.searchDownload(query), null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SourceOutcome(emptyList(), DownloadErrorKind.UNKNOWN)
                }
            }
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
        val saavnJob = if (source == null || source == DownloadSource.SAAVN) {
            async(Dispatchers.IO) {
                try {
                    SourceOutcome(saavn.search(query), null)
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

        val rj = radioJavanJob?.await()
        val dz = deezerJob?.await()
        val sv = saavnJob?.await()
        val mm = musicManJob?.await()
        val yt = ytDlpJob?.await()

        val results = (rj?.results.orEmpty() + dz?.results.orEmpty() + sv?.results.orEmpty() + mm?.results.orEmpty() + yt?.results.orEmpty())
        val failures = listOfNotNull(rj?.failure, dz?.failure, yt?.failure, mm?.failure, sv?.failure)
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
