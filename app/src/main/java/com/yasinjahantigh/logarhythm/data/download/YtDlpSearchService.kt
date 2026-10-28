package com.yasinjahantigh.logarhythm.data.download

import com.yasinjahantigh.logarhythm.domain.download.DownloadCommand
import com.yasinjahantigh.logarhythm.domain.download.DownloadErrorClassifier
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpSearchService @Inject constructor(
    private val runtime: YtDlpRuntime,
    private val cookies: DownloadCookies
) {

    data class Outcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind? = null
    )

    private data class SourceOutcome(
        val results: List<DownloadSearchResult>,
        val failure: DownloadErrorKind?
    )

    suspend fun search(query: String, sources: Set<DownloadSource>): Outcome = coroutineScope {
        val youtube = if (DownloadSource.YOUTUBE in sources) {
            async(Dispatchers.IO) { searchSource(DownloadSource.YOUTUBE, "ytsearch10:$query") }
        } else {
            null
        }
        val soundcloud = if (DownloadSource.SOUNDCLOUD in sources) {
            async(Dispatchers.IO) { searchSource(DownloadSource.SOUNDCLOUD, "scsearch10:$query") }
        } else {
            null
        }
        val yt = youtube?.await() ?: SourceOutcome(emptyList(), null)
        val sc = soundcloud?.await() ?: SourceOutcome(emptyList(), null)
        val failures = listOfNotNull(yt.failure, sc.failure)
        Outcome(
            results = yt.results + sc.results,
            failure = when {
                failures.isEmpty() -> null
                failures.contains(DownloadErrorKind.NETWORK) -> DownloadErrorKind.NETWORK
                else -> yt.failure ?: sc.failure
            }
        )
    }

    suspend fun searchSoundCloud(query: String): List<DownloadSearchResult> =
        searchSource(DownloadSource.SOUNDCLOUD, "scsearch5:$query").results

    private suspend fun searchSource(source: DownloadSource, expression: String): SourceOutcome {
        val processId = "search-${source.raw}-${UUID.randomUUID()}"
        return try {
            val request = YoutubeDLRequest(expression).apply {
                DownloadCommand.searchOptions(cookies.path()).forEach { (option, argument) ->
                    if (argument == null) addOption(option) else addOption(option, argument)
                }
            }
            val response = runtime.engineUse {
                runtime.executeWithTimeout(SEARCH_TIMEOUT_MS, processId) {
                    YoutubeDL.getInstance().execute(request, processId)
                }
            }
            SourceOutcome(SearchJsonParser.parse(source, response.out), null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (pipeline: DownloadPipelineException) {
            SourceOutcome(emptyList(), pipeline.kind)
        } catch (error: YoutubeDLException) {
            SourceOutcome(emptyList(), DownloadErrorClassifier.classify(error.message, error))
        } catch (error: Exception) {
            SourceOutcome(emptyList(), DownloadErrorClassifier.classify(error.message, error))
        }
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 90_000L
    }
}
