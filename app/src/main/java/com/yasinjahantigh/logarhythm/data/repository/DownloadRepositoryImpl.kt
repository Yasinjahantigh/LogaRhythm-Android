package com.yasinjahantigh.logarhythm.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yasinjahantigh.logarhythm.data.analytics.AppLog
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.download.DownloadWorker
import com.yasinjahantigh.logarhythm.data.download.DownloadWorkspace
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManDownloader
import com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanDownloader
import com.yasinjahantigh.logarhythm.data.download.saavn.SaavnDownloader
import com.yasinjahantigh.logarhythm.data.room.DownloadDao
import com.yasinjahantigh.logarhythm.data.room.DownloadEntity
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val workspace: DownloadWorkspace,
    private val preferencesManager: PreferencesManager,
    private val musicManDownloader: MusicManDownloader,
    private val saavnDownloader: SaavnDownloader,
    private val radioJavanDownloader: RadioJavanDownloader
) : DownloadRepository {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val now = System.currentTimeMillis()
                dao.markStaleActive(now, now - STALE_ACTIVE_AFTER_MS)
            }
        }
    }

    override fun observeHistory(): Flow<List<DownloadItem>> =
        dao.observeAll().map { entities -> entities.map(DownloadEntity::toDomain) }

    override suspend fun enqueue(result: DownloadSearchResult): String {
        dao.findActiveByUrl(result.url)?.let { return it.id }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val qualityPref = runCatching { preferencesManager.downloadQualityPref.first() }
            .getOrDefault(DownloadQuality.BEST.prefValue)
        dao.upsert(
            DownloadEntity(
                id = id,
                title = result.title,
                artist = result.artist,
                album = result.album,
                albumArtist = result.albumArtist.ifBlank { result.artist },
                trackNumber = result.trackNumber,
                durationMs = result.durationMs,
                source = result.source.raw,
                sourceUrl = result.url,
                videoId = result.id,
                quality = DownloadQuality.fromPref(qualityPref).prefValue,
                status = DownloadStatus.QUEUED.raw,
                progress = 0,
                etaSeconds = 0,
                artworkUrl = result.thumbnailUrl,
                mediaStoreId = null,
                contentUri = null,
                filePath = null,
                fileSizeBytes = 0,
                errorKind = null,
                errorMessage = null,
                statusDetail = null,
                usedFallback = false,
                createdAt = now,
                updatedAt = now
            )
        )

        enqueueWork(id, ExistingWorkPolicy.KEEP)
        AppLog.i("Download", "Enqueued id=$id src=${result.source.raw} q=$qualityPref title=${result.title.take(40)}")
        withContext(Dispatchers.IO) { workspace.prune() }
        return id
    }

    override suspend fun enqueueBatch(results: List<DownloadSearchResult>): Int {
        var queued = 0
        for (result in results) {
            if (dao.findActiveByUrl(result.url) != null) continue
            enqueue(result)
            queued++
        }
        return queued
    }

    override suspend fun cancel(id: String) {
        AppLog.i("Download", "Cancelled id=$id")
        dao.markCancelled(id, System.currentTimeMillis())
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        musicManDownloader.cancel(id)
        saavnDownloader.cancel(id)
        radioJavanDownloader.cancel(id)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    override suspend fun retry(id: String) {
        AppLog.i("Download", "Retry id=$id")
        dao.markQueued(id, System.currentTimeMillis())
        enqueueWork(id, ExistingWorkPolicy.REPLACE)
    }

    override suspend fun retryAllFailed(): Int {
        val failed = observeHistory().first().filter { it.status == DownloadStatus.FAILED }
        AppLog.i("Download", "Retrying all failed count=${failed.size}")
        for (item in failed) {
            dao.markQueued(item.id, System.currentTimeMillis())
            enqueueWork(item.id, ExistingWorkPolicy.REPLACE)
        }
        return failed.size
    }

    override suspend fun remove(id: String) {
        dao.delete(id)
        workspace.cleanup(id)
    }

    override suspend fun clearFinished() {
        dao.deleteFinished()
    }

    private fun enqueueWork(id: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.EXTRA_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), policy, request)
    }

    private fun workName(id: String): String = "download_$id"

    private companion object {
        const val STALE_ACTIVE_AFTER_MS = 10 * 60 * 1000L
    }
}
