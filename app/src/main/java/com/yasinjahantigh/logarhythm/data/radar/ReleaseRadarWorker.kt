package com.yasinjahantigh.logarhythm.data.radar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.ArtistFollowDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ReleaseRadarWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferencesManager: PreferencesManager,
    private val artistFollowDao: ArtistFollowDao,
    private val repository: ArtistFollowRepository,
    private val notifications: ReleaseRadarNotifications
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val enabled = preferencesManager.releaseRadarEnabled.first()
        if (!enabled) return Result.success()
        if (artistFollowDao.getFollowedOnce().isEmpty()) return Result.success()

        val summary = repository.refreshAll()
        if (summary.newReleases.isNotEmpty()) {
            notifications.notifyReleases(summary.newReleases)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "release_radar_periodic_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ReleaseRadarWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
