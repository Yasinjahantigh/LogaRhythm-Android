package com.yasinjahantigh.logarhythm.data.analytics

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

@HiltWorker
class CrashUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val installReporter: InstallReporter,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dir = CrashReportHandler.pendingDir(applicationContext)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        if (files.isEmpty()) return Result.success()

        val consentAnswered = runCatching { preferencesManager.privacyConsentAnswered.first() }.getOrDefault(false)
        if (!consentAnswered) {
            return Result.retry()
        }
        val enabled = runCatching { preferencesManager.crashReportsEnabled.first() }.getOrDefault(true)
        if (!enabled) {
            files.forEach { it.delete() }
            return Result.success()
        }

        var retryNeeded = false
        for (file in files) {
            val payload = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (payload == null) {
                file.delete()
                continue
            }
            val outcome = installReporter.sendCrashReport(payload)
            when (outcome) {
                InstallReporter.DeliveryResult.DELIVERED,
                InstallReporter.DeliveryResult.REJECTED_PERMANENT -> file.delete()
                InstallReporter.DeliveryResult.RETRY -> retryNeeded = true
            }
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "logarhythm_crash_upload"

        fun enqueueIfPending(context: Context) {
            val dir = CrashReportHandler.pendingDir(context)
            val hasPending = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.isNotEmpty() == true
            if (!hasPending) return
            val request = OneTimeWorkRequestBuilder<CrashUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
