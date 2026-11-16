package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.library.LibrarySync
import com.yasinjahantigh.logarhythm.data.download.metadata.MetadataResolver
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManDownloader
import com.yasinjahantigh.logarhythm.data.download.saavn.SaavnClient
import com.yasinjahantigh.logarhythm.data.room.DownloadDao
import com.yasinjahantigh.logarhythm.data.sources.LrclibLyricsSource
import com.yasinjahantigh.logarhythm.data.download.TagEnrichStatus
import com.yasinjahantigh.logarhythm.domain.lyrics.LrcParser
import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsQuery
import com.yasinjahantigh.logarhythm.domain.download.DownloadErrorClassifier
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_CRAWLING_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_NETWORK_RETRY_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_SAVED_WITHOUT_TAGS_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_TAG_WRITE_FAILED_DETAIL
import com.yausername.youtubedl_android.YoutubeDL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val runtime: YtDlpRuntime,
    private val searchService: YtDlpSearchService,
    private val downloader: YtDlpDownloader,
    private val musicManClient: MusicManClient,
    private val musicManDownloader: MusicManDownloader,
    private val saavnClient: SaavnClient,
    private val saavnDownloader: com.yasinjahantigh.logarhythm.data.download.saavn.SaavnDownloader,
    private val radioJavanClient: com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanClient,
    private val radioJavanDownloader: com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanDownloader,
    private val metadataResolver: MetadataResolver,
    private val artworkLoader: ArtworkLoader,
    private val mp4Remuxer: Mp4Remuxer,
    private val tagEnricher: TagEnricher,
    private val mediaStoreSaver: MediaStoreSaver,
    private val workspace: DownloadWorkspace,
    private val notifications: DownloadNotifications,
    private val librarySync: LibrarySync,
    private val events: DownloadEvents,
    private val lrclibLyricsSource: LrclibLyricsSource
) : CoroutineWorker(appContext, params) {

    private val downloadId: String
        get() = inputData.getString(EXTRA_ID).orEmpty()

    override suspend fun doWork(): Result {
        val id = downloadId
        if (id.isBlank()) return Result.failure()

        val entity = dao.getById(id) ?: return Result.success()
        if (entity.status == DownloadStatus.SAVED.raw || entity.status == DownloadStatus.CANCELLED.raw) {
            return Result.success()
        }
        val item = entity.toDomain()

        return try {
            executePipeline(item)
        } catch (cancelled: CancellationException) {
            downloader.cancel(id)
            musicManDownloader.cancel(id)
            saavnDownloader.cancel(id)
            radioJavanDownloader.cancel(id)
            markCancelled(id)
            throw cancelled
        } catch (_: YoutubeDL.CanceledException) {
            markCancelled(id)
            Result.success()
        } catch (error: Throwable) {
            fail(item, DownloadErrorKind.UNKNOWN, error.message)
        }
    }

    private suspend fun markCancelled(id: String) {
        withContext(NonCancellable) {
            dao.markCancelledIfActive(id, now())
            workspace.cleanup(id)
        }
    }

    private suspend fun executePipeline(item: DownloadItem): Result {
        val id = item.id
        updateForeground(item.title, 0, 0, DownloadStatus.QUEUED)
        dao.updateProgress(id, DownloadStatus.QUEUED.raw, 0, 0, null, now())

        if (item.source.usesYtDlp) {
            val initError = try {
                runtime.ensureReady()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error
            }
            if (initError != null) {
                val kind = (initError as? DownloadPipelineException)?.kind ?: DownloadErrorKind.RUNTIME
                return fail(item, kind, initError.message)
            }
        }

        if (!workspace.hasRoom()) {
            return fail(item, DownloadErrorKind.STORAGE, null)
        }

        val parsed = DownloadTitleParser.parse(item.title, item.artist, item.artist)
        val initialTitle = item.title.ifBlank { parsed.second }
        val initialArtist = item.artist.ifBlank { parsed.first }

        val progressChannel = Channel<ProgressEvent>(Channel.CONFLATED)
        val progressJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            pumpProgress(id, initialTitle, progressChannel)
        }

        // Resolve metadata concurrently so audio streaming starts immediately without waiting.
        val (outcome, resolved) = coroutineScope {
            val metadataJob = async(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(8_000L) {
                        metadataResolver.resolve(parsed.second, parsed.first, item.durationMs)
                    }
                }.getOrNull()
            }

            val dlOutcome = try {
                performDownload(item, initialTitle, initialArtist, item.artworkUrl, progressChannel)
            } catch (cancelled: CancellationException) {
                metadataJob.cancel()
                stopProgressNow(progressChannel, progressJob)
                downloader.cancel(id)
                throw cancelled
            } catch (pipeline: DownloadPipelineException) {
                metadataJob.cancel()
                stopProgressAndJoin(progressChannel, progressJob)
                if (pipeline.kind == DownloadErrorKind.NETWORK && runAttemptCount < MAX_NETWORK_RETRIES) {
                    dao.updateProgress(
                        id,
                        DownloadStatus.QUEUED.raw,
                        0,
                        0,
                        DOWNLOAD_NETWORK_RETRY_DETAIL,
                        now()
                    )
                    updateForeground(item.title, 0, 0, DownloadStatus.QUEUED)
                    return@coroutineScope null to null
                }
                throw pipeline
            } catch (throwable: Throwable) {
                metadataJob.cancel()
                stopProgressNow(progressChannel, progressJob)
                throw throwable
            }

            dlOutcome to metadataJob.await()
        }

        if (outcome == null) return Result.retry()

        stopProgressAndJoin(progressChannel, progressJob)

        val finalTitle = resolved?.title ?: outcome.title
        val finalArtist = resolved?.artist ?: outcome.artist
        val finalAlbum = item.album.ifBlank { resolved?.album.orEmpty() }
        val finalArtwork = resolved?.artworkUrl ?: outcome.artworkUrl ?: item.artworkUrl
        val finalTrackNumber = item.trackNumber.takeIf { it > 0 } ?: resolved?.trackNumber

        return try {
            completeDownload(
                item = item,
                outcome = outcome.copy(
                    title = finalTitle,
                    artist = finalArtist,
                    artworkUrl = finalArtwork
                ),
                album = finalAlbum,
                genre = resolved?.genre,
                year = resolved?.year,
                trackNumber = finalTrackNumber,
                discNumber = resolved?.discNumber,
                composer = resolved?.composer
            )
        } catch (pipeline: DownloadPipelineException) {
            fail(item, pipeline.kind, pipeline.message)
        } catch (error: Exception) {
            fail(item, DownloadErrorKind.UNKNOWN, error.message)
        }
    }

    private suspend fun performDownload(
        item: DownloadItem,
        title: String,
        artist: String,
        artwork: String?,
        channel: Channel<ProgressEvent>
    ): DownloadOutcome {
        val dir = workspace.dirFor(item.id)
        if (item.source == DownloadSource.RADIOJAVAN) {
            val file = radioJavanDownloader.download(
                songId = item.videoId,
                sourceUrl = item.sourceUrl,
                downloadId = item.id,
                workspace = dir
            ) { progress, eta, line ->
                channel.trySend(ProgressEvent(progress, eta, line))
            }
            return DownloadOutcome(file, false, title, artist, artwork)
        } else if (item.source == DownloadSource.DEEZER) {
            // Prioritize MusicMan Bale endpoint first (fast & VPN-free), then Saavn, RadioJavan, yt-dlp.
            val query = "$title $artist".trim()
            val mmMatch = runCatching {
                musicManClient.search(query, DownloadSearchCategory.TRACK, 5).firstOrNull { it.hasReadyAudio }
            }.getOrNull()
            if (mmMatch != null) {
                try {
                    val file = musicManDownloader.download(
                        trackId = mmMatch.id,
                        downloadId = item.id,
                        workspace = dir,
                        preferredQuality = item.quality
                    ) { progress, eta, line ->
                        channel.trySend(ProgressEvent(progress, eta, line))
                    }
                    return DownloadOutcome(file, false, title, artist, artwork)
                } catch (_: Exception) {
                }
            }
            val saavnMatch = runCatching { saavnClient.search(query, 3).firstOrNull() }.getOrNull()
            if (saavnMatch != null) {
                try {
                    val file = saavnDownloader.download(
                        downloadUrl = saavnMatch.url,
                        songId = saavnMatch.id,
                        downloadId = item.id,
                        workspace = dir
                    ) { progress, eta, line ->
                        channel.trySend(ProgressEvent(progress, eta, line))
                    }
                    return DownloadOutcome(file, true, title, artist, artwork)
                } catch (_: Exception) {
                }
            }
            val rjMatches = runCatching { radioJavanClient.search(query, 3) }.getOrNull().orEmpty()
            val rjBest = rjMatches.firstOrNull()
            if (rjBest != null) {
                try {
                    val file = radioJavanDownloader.download(
                        songId = rjBest.id,
                        sourceUrl = rjBest.url,
                        downloadId = item.id,
                        workspace = dir
                    ) { progress, eta, line ->
                        channel.trySend(ProgressEvent(progress, eta, line))
                    }
                    return DownloadOutcome(file, false, title, artist, artwork)
                } catch (_: Exception) {
                }
            }
            val ytFallback = runCatching {
                searchService.searchSoundCloud(query).firstOrNull()
            }.getOrNull()
            if (ytFallback != null) {
                val file = downloadOnce(item, ytFallback.url, ytFallback.id, dir, channel)
                return DownloadOutcome(file, true, title, artist, artwork)
            }
            val ytSearchUrl = "ytsearch1:$query"
            val file = downloadOnce(item, ytSearchUrl, item.id, dir, channel)
            return DownloadOutcome(file, true, title, artist, artwork)
        } else if (item.source == DownloadSource.MUSICMAN) {
            val trackId = item.videoId.ifBlank { item.sourceUrl.substringAfterLast('/') }
            try {
                val file = musicManDownloader.download(
                    trackId = trackId,
                    downloadId = item.id,
                    workspace = dir,
                    preferredQuality = item.quality
                ) { progress, eta, line ->
                    channel.trySend(ProgressEvent(progress, eta, line))
                }
                return DownloadOutcome(file, false, title, artist, artwork)
            } catch (e: Exception) {
                val query = "$title $artist".trim()
                val saavnMatch = runCatching { saavnClient.search(query, 2).firstOrNull() }.getOrNull()
                if (saavnMatch != null) {
                    runCatching {
                        val file = saavnDownloader.download(
                            downloadUrl = saavnMatch.url,
                            songId = saavnMatch.id,
                            downloadId = item.id,
                            workspace = dir
                        ) { progress, eta, line ->
                            channel.trySend(ProgressEvent(progress, eta, line))
                        }
                        return DownloadOutcome(file, true, title, artist, artwork)
                    }
                }
                val rjMatch = runCatching { radioJavanClient.search(query, 1).firstOrNull() }.getOrNull()
                if (rjMatch != null) {
                    runCatching {
                        val file = radioJavanDownloader.download(
                            songId = rjMatch.id,
                            sourceUrl = rjMatch.url,
                            downloadId = item.id,
                            workspace = dir
                        ) { progress, eta, line ->
                            channel.trySend(ProgressEvent(progress, eta, line))
                        }
                        return DownloadOutcome(file, true, title, artist, artwork)
                    }
                }
                val ytFallback = runCatching { searchService.searchSoundCloud(query).firstOrNull() }.getOrNull()
                if (ytFallback != null) {
                    val file = downloadOnce(item, ytFallback.url, ytFallback.id, dir, channel)
                    return DownloadOutcome(file, true, title, artist, artwork)
                }
                throw e
            }
        } else if (item.source == DownloadSource.SAAVN) {
            val songId = item.videoId.ifBlank { "saavn_${item.id}" }
            val file = saavnDownloader.download(
                downloadUrl = item.sourceUrl,
                songId = songId,
                downloadId = item.id,
                workspace = dir
            ) { progress, eta, line ->
                channel.trySend(ProgressEvent(progress, eta, line))
            }
            return DownloadOutcome(file, false, title, artist, artwork)
        }

        val firstError = try {
            return DownloadOutcome(
                file = downloadOnce(item, item.sourceUrl, item.videoId, dir, channel),
                usedFallback = false,
                title = title,
                artist = artist,
                artworkUrl = artwork
            )
        } catch (pipeline: DownloadPipelineException) {
            pipeline
        }

        // yt-dlp can report an error after the audio file itself was written.
        downloader.findUsableFile(dir)?.let {
            return DownloadOutcome(it, false, title, artist, artwork)
        }

        // A site change can often be fixed by updating yt-dlp once.
        if (DownloadErrorClassifier.isSiteChange(firstError.kind)) {
            runtime.updateAndVerify()
            try {
                return DownloadOutcome(
                    file = downloadOnce(item, item.sourceUrl, item.videoId, dir, channel),
                    usedFallback = false,
                    title = title,
                    artist = artist,
                    artworkUrl = artwork
                )
            } catch (_: DownloadPipelineException) {
                downloader.findUsableFile(dir)?.let {
                    return DownloadOutcome(it, false, title, artist, artwork)
                }
            }
        }

        if (firstError.kind == DownloadErrorKind.NETWORK) throw firstError

        // YouTube content failure: try the same song on SoundCloud.
        if (item.source == DownloadSource.YOUTUBE) {
            val fallback = findSoundCloudMatch(title, artist, item.durationMs)
            if (fallback != null) {
                dao.markFallback(item.id, true, "fallback_soundcloud", now())
                val fallbackTitle = fallback.title.ifBlank { title }
                val fallbackArtist = fallback.artist.ifBlank { artist }
                val fallbackArtwork = fallback.thumbnailUrl ?: artwork
                try {
                    return DownloadOutcome(
                        file = downloadOnce(item, fallback.url, fallback.id, dir, channel),
                        usedFallback = true,
                        title = fallbackTitle,
                        artist = fallbackArtist,
                        artworkUrl = fallbackArtwork
                    )
                } catch (pipeline: DownloadPipelineException) {
                    downloader.findUsableFile(dir)?.let {
                        return DownloadOutcome(it, true, fallbackTitle, fallbackArtist, fallbackArtwork)
                    }
                    throw DownloadPipelineException(
                        pipeline.kind,
                        pipeline.message ?: firstError.message ?: "Download failed",
                        pipeline
                    )
                }
            }
        }

        throw firstError
    }

    private suspend fun downloadOnce(
        item: DownloadItem,
        url: String,
        videoId: String,
        dir: File,
        channel: Channel<ProgressEvent>
    ): File {
        val abr = item.quality.prefValue.toIntOrNull()?.takeIf { it > 0 }
        return try {
            downloader.download(url, videoId, item.id, dir, abr) { p, e, line ->
                channel.trySend(ProgressEvent(p, e, line))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (canceled: YoutubeDL.CanceledException) {
            throw canceled
        } catch (error: Exception) {
            throw error as? DownloadPipelineException
                ?: DownloadPipelineException(
                    DownloadErrorClassifier.classify(error.message, error),
                    error.message ?: "Download failed",
                    error
                )
        }
    }

    private suspend fun findSoundCloudMatch(
        title: String,
        artist: String,
        durationMs: Long
    ): DownloadSearchResult? {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return null
        val results = try {
            searchService.searchSoundCloud(query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        return results
            .map { it to DownloadMetadataMerge.similarity(it.title, title) }
            .filter { (candidate, score) ->
                score >= MIN_FALLBACK_SIMILARITY &&
                    (durationMs <= 0L || candidate.durationMs <= 0L ||
                        abs(candidate.durationMs - durationMs) <= FALLBACK_DURATION_TOLERANCE_MS)
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private suspend fun completeDownload(
        item: DownloadItem,
        outcome: DownloadOutcome,
        album: String,
        genre: String?,
        year: String?,
        trackNumber: Int?,
        discNumber: Int?,
        composer: String?
    ): Result {
        val id = item.id
        updateForeground(outcome.title, 100, 0, DownloadStatus.SAVING)
        dao.updateProgress(id, DownloadStatus.SAVING.raw, 100, 0, null, now())
        dao.updateMetadata(id, outcome.title, outcome.artist, album, outcome.artworkUrl, now())

        val audioFile = mp4Remuxer.normalize(outcome.file)
        val artworkBytes = artworkLoader.load(outcome.artworkUrl)
        val autoLyrics = fetchAutoLyrics(outcome.title, outcome.artist, album, item.durationMs)
        val tagOutcome = tagEnricher.enrich(
            file = audioFile,
            title = outcome.title,
            artist = outcome.artist,
            album = album.ifBlank { null },
            genre = genre,
            artwork = artworkBytes,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            composer = composer,
            lyrics = autoLyrics
        )
        val tagDetail = when (tagOutcome.status) {
            TagEnrichStatus.UNSUPPORTED -> DOWNLOAD_SAVED_WITHOUT_TAGS_DETAIL
            TagEnrichStatus.FAILED -> DOWNLOAD_TAG_WRITE_FAILED_DETAIL
            else -> null
        }
        val saved = mediaStoreSaver.save(audioFile, outcome.title, outcome.artist, album)

        withContext(NonCancellable) {
            dao.markSaved(
                id = id,
                mediaStoreId = saved.mediaStoreId,
                uri = saved.contentUri,
                path = saved.filePath,
                size = saved.sizeBytes,
                artwork = outcome.artworkUrl,
                detail = tagDetail ?: if (outcome.usedFallback) FALLBACK_DETAIL else null,
                now = now()
            )
            notifications.notifyFinished(id, outcome.title, true, null)
            events.emitSaved(DownloadSavedEvent(outcome.title, outcome.usedFallback))
            workspace.cleanup(id)
        }

        librarySync.requestRefresh(force = true)
        return Result.success()
    }

    private suspend fun fetchAutoLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): String? = withTimeoutOrNull(4_500L) {
        try {
            val result = lrclibLyricsSource.search(
                LyricsQuery(
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs
                )
            )
            val best = result.candidates.firstOrNull { it.isSynced }
                ?: result.candidates.firstOrNull()
                ?: return@withTimeoutOrNull null
            LrcParser.format(best.lyrics).takeIf { it.isNotBlank() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun pumpProgress(
        id: String,
        title: String,
        channel: Channel<ProgressEvent>
    ) {
        var status = DownloadStatus.QUEUED
        var lastProgress = -1
        var lastWriteAt = 0L

        for (event in channel) {
            val nextStatus = statusFor(event, status)
            val progress = if (event.progress >= 0f) {
                event.progress.toInt().coerceIn(0, 100)
            } else {
                lastProgress.coerceAtLeast(0)
            }
            val statusChanged = nextStatus != status
            status = nextStatus
            if (!statusChanged && progress == lastProgress) continue
            val now = System.currentTimeMillis()
            if (!statusChanged && now - lastWriteAt < PROGRESS_WRITE_INTERVAL_MS) continue

            lastProgress = progress
            lastWriteAt = now
            val detail = when {
                event.line.startsWith("[crawl]", ignoreCase = true) -> DOWNLOAD_CRAWLING_DETAIL
                else -> event.line.takeIf { it.isNotBlank() }?.take(90)
            }
            dao.updateProgress(
                id = id,
                status = status.raw,
                progress = progress,
                eta = event.etaSeconds,
                detail = detail,
                now = now
            )
            updateForeground(title, progress, event.etaSeconds, status)
        }
    }

    private fun stopProgressNow(channel: Channel<ProgressEvent>, job: Job) {
        channel.close()
        job.cancel()
    }

    private suspend fun stopProgressAndJoin(channel: Channel<ProgressEvent>, job: Job) {
        channel.close()
        job.cancelAndJoin()
    }

    private fun statusFor(event: ProgressEvent, current: DownloadStatus): DownloadStatus {
        val line = event.line.lowercase()
        return if (event.progress > 0f || line.contains("[download]") || line.contains("[crawl]")) {
            DownloadStatus.DOWNLOADING
        } else {
            current
        }
    }

    private suspend fun updateForeground(
        title: String,
        progress: Int,
        etaSeconds: Long,
        status: DownloadStatus
    ) {
        runCatching {
            setForeground(
                notifications.foregroundInfo(downloadId, title, progress, etaSeconds, status)
            )
        }
    }

    private suspend fun fail(
        item: DownloadItem,
        kind: DownloadErrorKind,
        message: String?
    ): Result {
        withContext(NonCancellable) {
            dao.markFailed(item.id, kind.raw, message?.take(500), null, now())
            notifications.notifyFinished(item.id, item.title, false, friendlyError(kind))
            if (kind != DownloadErrorKind.NETWORK) workspace.cleanup(item.id)
        }
        return Result.failure()
    }

    private fun friendlyError(kind: DownloadErrorKind): String = applicationContext.getString(
        when (kind) {
            DownloadErrorKind.NETWORK -> R.string.download_error_network_message
            DownloadErrorKind.STORAGE -> R.string.download_error_storage
            DownloadErrorKind.RUNTIME, DownloadErrorKind.UPDATE -> R.string.download_error_runtime
            else -> R.string.download_error_content
        }
    )

    companion object {
        const val EXTRA_ID = "download_id"
        const val WORK_TAG = "download"
        private const val FALLBACK_DETAIL = "fallback_soundcloud"
        private const val MAX_NETWORK_RETRIES = 2
        private const val PROGRESS_WRITE_INTERVAL_MS = 600L
        private const val MIN_FALLBACK_SIMILARITY = 0.45
        private const val FALLBACK_DURATION_TOLERANCE_MS = 20_000L
    }
}

private data class ProgressEvent(
    val progress: Float,
    val etaSeconds: Long,
    val line: String
)

private data class DownloadOutcome(
    val file: File,
    val usedFallback: Boolean,
    val title: String,
    val artist: String,
    val artworkUrl: String?
)

private fun now(): Long = System.currentTimeMillis()
