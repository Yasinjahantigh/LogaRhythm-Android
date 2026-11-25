package com.yasinjahantigh.logarhythm.data.analytics

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yasinjahantigh.logarhythm.BuildConfig
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklyListeningStatsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferencesManager: PreferencesManager,
    private val musicDao: MusicDao,
    private val installReporter: InstallReporter
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) return Result.success()
        val consentAnswered = runCatching { preferencesManager.privacyConsentAnswered.first() }.getOrDefault(false)
        val weeklyEnabled = runCatching { preferencesManager.weeklyStatsEnabled.first() }.getOrDefault(true)
        val privateSession = runCatching { preferencesManager.privateSession.first() }.getOrDefault(false)
        if (!consentAnswered || !weeklyEnabled || privateSession) return Result.success()

        val weekKey = currentIsoWeekKey()
        val lastSent = runCatching { preferencesManager.lastWeeklyStatsWeek.first() }.getOrDefault("")
        if (lastSent == weekKey) return Result.success()

        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - SEVEN_DAYS_MS
        val totalPlays = runCatching {
            musicDao.getCompletedPlayCountInRange(sevenDaysAgo, now).first()
        }.getOrDefault(0)
        if (totalPlays <= 0) return Result.success()

        val totalListenMs = runCatching {
            musicDao.getTotalListenTimeMsInRange(sevenDaysAgo, now).first()
        }.getOrDefault(0L)
        val totalMinutes = (totalListenMs / 60_000L).coerceAtLeast(0L)
        val topTracks = runCatching {
            musicDao.getTopTracksInRange(sevenDaysAgo, now, limit = 5).first()
        }.getOrDefault(emptyList()).map {
            Triple(it.title, it.artist, it.playCount)
        }

        val delivered = installReporter.sendWeeklyStats(
            week = weekKey,
            totalPlays = totalPlays,
            totalMinutes = totalMinutes,
            topTracks = topTracks
        )
        return if (delivered) {
            preferencesManager.setLastWeeklyStatsWeek(weekKey)
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "logarhythm_weekly_listening_stats"
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

        fun currentIsoWeekKey(timeMs: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance(Locale.US).apply {
                minimalDaysInFirstWeek = 4
                firstDayOfWeek = Calendar.MONDAY
                timeInMillis = timeMs
            }
            val year = cal.get(Calendar.YEAR)
            val week = cal.get(Calendar.WEEK_OF_YEAR)
            return String.format(Locale.US, "%04d-W%02d", year, week)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyListeningStatsWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
