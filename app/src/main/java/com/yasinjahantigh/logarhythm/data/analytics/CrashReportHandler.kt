package com.yasinjahantigh.logarhythm.data.analytics

import android.content.Context
import android.os.Build
import com.yasinjahantigh.logarhythm.BuildConfig
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

/**
 * Installs a fail-safe uncaught exception handler that writes sanitized crash telemetry
 * to `noBackupFilesDir/pending_crashes/` for upload on the next launch when crash reporting is enabled.
 */
class CrashReportHandler(
    private val appContext: Context,
    private val logBuffer: AppLogBuffer,
    private val installIdProvider: () -> String,
    private val isEnabledProvider: () -> Boolean,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Always persist uncaught crashes to the local rotating log file for offline inspection.
            logBuffer.recordCrash(thread.name, throwable)
            if (!BuildConfig.DEBUG && isEnabledProvider()) {
                persistCrash(throwable)
            }
        } catch (_: Throwable) {
            // Never swallow or crash inside the crash handler itself.
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun persistCrash(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawStack = sw.toString()

        val payload = JSONObject().apply {
            put("installId", installIdProvider())
            put("version", BuildConfig.VERSION_NAME)
            put("code", BuildConfig.VERSION_CODE)
            put("channel", BuildConfig.DISTRIBUTION_CHANNEL)
            put("exceptionType", throwable::class.java.name.take(120))
            put("message", AppLogBuffer.sanitize(throwable.message.orEmpty()).take(500))
            put("stackTrace", AppLogBuffer.sanitizeMultiline(rawStack, 5500))
            put("recentLogs", AppLogBuffer.sanitizeMultiline(logBuffer.snapshotTail(5500), 5500))
            put("androidVersion", Build.VERSION.RELEASE.orEmpty().take(24))
            put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty().take(24))
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(64))
        }

        val dir = pendingDir(appContext).apply { mkdirs() }
        val existing = dir.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
        if (existing.size >= MAX_PENDING_FILES) {
            existing.take(existing.size - MAX_PENDING_FILES + 1).forEach { it.delete() }
        }
        val tmp = File(dir, "crash_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.tmp")
        val target = File(dir, tmp.name.removeSuffix(".tmp") + ".json")
        tmp.writeText(payload.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(payload.toString())
            tmp.delete()
        }
    }

    companion object {
        private const val MAX_PENDING_FILES = 5

        fun pendingDir(context: Context): File =
            File(context.noBackupFilesDir, "pending_crashes")

        fun install(context: Context, logBuffer: AppLogBuffer, installReporter: InstallReporter) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashReportHandler) return
            val bootstrap = context.getSharedPreferences(PreferencesManager.BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            val handler = CrashReportHandler(
                appContext = context.applicationContext,
                logBuffer = logBuffer,
                installIdProvider = { installReporter.getOrCreateInstallId() },
                isEnabledProvider = {
                    bootstrap.getBoolean(PreferencesManager.BOOTSTRAP_CRASH_REPORTS_KEY, true)
                },
                previousHandler = current
            )
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}
