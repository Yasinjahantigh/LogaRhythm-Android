package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Scratch space for one download; reused across WorkManager retries so `--continue` resumes. */
@Singleton
class DownloadWorkspace @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun dirFor(downloadId: String): File =
        File(File(context.cacheDir, "downloads"), downloadId).apply { mkdirs() }

    fun cleanup(downloadId: String) {
        runCatching { File(File(context.cacheDir, "downloads"), downloadId).deleteRecursively() }
    }

    fun hasRoom(requiredBytes: Long = MIN_FREE_BYTES): Boolean = runCatching {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBytes >= requiredBytes
    }.getOrDefault(true)

    /** Old leftover partial downloads should not fill the cache forever. */
    fun prune() {
        runCatching {
            val root = File(context.cacheDir, "downloads")
            val cutoff = System.currentTimeMillis() - PRUNE_AGE_MS
            root.listFiles()?.forEach { dir ->
                if (dir.lastModified() < cutoff) dir.deleteRecursively()
            }
        }
    }

    private companion object {
        const val MIN_FREE_BYTES = 80L * 1024 * 1024
        const val PRUNE_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
