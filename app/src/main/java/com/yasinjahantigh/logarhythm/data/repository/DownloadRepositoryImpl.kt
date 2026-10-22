package com.yasinjahantigh.logarhythm.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yasinjahantigh.logarhythm.data.download.DownloadWorker
import com.yasinjahantigh.logarhythm.data.download.DownloadWorkspace
import com.yasinjahantigh.logarhythm.data.room.DownloadDao
import com.yasinjahantigh.logarhythm.data.room.DownloadEntity
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val workspace: DownloadWorkspace
) : DownloadRepository {

    override fun observeHistory(): Flow<List<DownloadItem>> =
        dao.observeAll().map { entities -> entities.map(DownloadEntity::toDomain) }

    override suspend fun enqueue(result: DownloadSearchResult): String {
        dao.findActiveByUrl(result.url)?.let { return it.id }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadEntity(
                id = id,
                title = result.title,
                artist = result.artist,
                album = "",
                durationMs = result.durationMs,
                source = result.source.raw,
                sourceUrl = result.url,
                videoId = result.id,
                quality = DownloadQuality.BEST.prefValue,
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
        withContext(Dispatchers.IO) { workspace.prune() }
        return id
    }

    override suspend fun cancel(id: String) {
        dao.markCancelled(id, System.currentTimeMillis())
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    override suspend fun retry(id: String) {
        dao.markQueued(id, System.currentTimeMillis())
        enqueueWork(id, ExistingWorkPolicy.REPLACE)
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
}
