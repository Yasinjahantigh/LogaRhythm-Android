package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.download.DownloadErrorClassifier
import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

sealed interface YtdlpUpdateState {
    data object Idle : YtdlpUpdateState
    data object Updating : YtdlpUpdateState
    data class Ready(val version: String) : YtdlpUpdateState
    data class UpToDate(val version: String?) : YtdlpUpdateState
    data class Failed(val kind: DownloadErrorKind) : YtdlpUpdateState
    data class RolledBack(val kind: DownloadErrorKind) : YtdlpUpdateState
}

/**
 * One gate for everything that touches the embedded yt-dlp binary:
 * first-run init, updates (with verification + rollback), and process execution.
 */
@Singleton
class YtDlpRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) {
    private val initMutex = Mutex()
    private val updateMutex = Mutex()
    private val activeProcesses = AtomicInteger(0)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firstUpdateRequested = AtomicBoolean(false)

    @Volatile
    private var initialized = false

    @Volatile
    private var updating = false

    private val _updateState = MutableStateFlow<YtdlpUpdateState>(YtdlpUpdateState.Idle)
    val updateState: StateFlow<YtdlpUpdateState> = _updateState.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    /** Initializes the engine and, once, tries to update yt-dlp without blocking the caller. */
    suspend fun ensureReady() {
        initialize()
        if (!prefs.ytdlpFirstUpdateDone.first()) {
            if (firstUpdateRequested.compareAndSet(false, true)) {
                backgroundScope.launch {
                    try {
                        updateAndVerify()
                    } finally {
                        runCatching { prefs.setYtdlpFirstUpdateDone(true) }
                    }
                }
            }
        } else if (_version.value == null) {
            currentVersion()
        }
    }

    private suspend fun initialize() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return@withLock
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(context)
                    initialized = true
                } catch (error: Throwable) {
                    throw DownloadPipelineException(
                        DownloadErrorKind.RUNTIME,
                        "The downloader could not start on this device.",
                        error
                    )
                }
            }
        }
    }

    /** Runs [block] with the engine, waiting for any in-flight update to finish first. */
    suspend fun <T> engineUse(block: suspend () -> T): T {
        initialize()
        while (updating) delay(200)
        activeProcesses.incrementAndGet()
        try {
            return block()
        } finally {
            activeProcesses.decrementAndGet()
        }
    }

    /** Runs a blocking yt-dlp call on an interruptible IO thread with a hard timeout. */
    suspend fun <T> executeWithTimeout(timeoutMs: Long, processId: String?, block: () -> T): T {
        return try {
            withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) { block() }
            }
        } catch (timeout: TimeoutCancellationException) {
            cancelProcess(processId)
            throw DownloadPipelineException(
                DownloadErrorKind.NETWORK,
                "The download timed out.",
                timeout
            )
        }
    }

    fun cancelProcess(processId: String?) {
        if (processId.isNullOrBlank()) return
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /** Executes `--version`; the library's cached tag is not trustworthy after a rollback. */
    suspend fun currentVersion(): String? {
        initialize()
        val value = withContext(Dispatchers.IO) {
            try {
                val request = YoutubeDLRequest("").apply { addOption("--version") }
                val response = withTimeout(VERSION_TIMEOUT_MS) {
                    runInterruptible(Dispatchers.IO) { YoutubeDL.getInstance().execute(request) }
                }
                response.out.trim().lineSequence().lastOrNull { it.isNotBlank() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
        if (!value.isNullOrBlank()) _version.value = value
        return value
    }

    /** Downloads the latest stable yt-dlp, verifies it, and restores the snapshot on failure. */
    suspend fun updateAndVerify(): YtdlpUpdateState {
        initialize()
        return updateMutex.withLock {
            while (activeProcesses.get() > 0) delay(250)
            updating = true
            _updateState.value = YtdlpUpdateState.Updating

            val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
            val ytdlpDir = File(baseDir, "yt-dlp")
            val binary = File(ytdlpDir, "yt-dlp")
            val backup = File(baseDir, "yt-dlp-prev")

            try {
                runCatching {
                    backup.deleteRecursively()
                    if (binary.exists()) binary.copyTo(backup, overwrite = true)
                }

                val updateResult = try {
                    withTimeout(UPDATE_TIMEOUT_MS) {
                        runInterruptible(Dispatchers.IO) {
                            YoutubeDL.getInstance().updateYoutubeDL(
                                context,
                                YoutubeDL.UpdateChannel.STABLE
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (cancelled !is TimeoutCancellationException) throw cancelled
                    _updateState.value = rollBack(ytdlpDir, binary, backup, DownloadErrorKind.NETWORK)
                    return@withLock _updateState.value
                } catch (error: Throwable) {
                    val kind = classifyUpdateFailure(error)
                    _updateState.value = rollBack(ytdlpDir, binary, backup, kind)
                    return@withLock _updateState.value
                }

                val verified = currentVersion()
                if (verified.isNullOrBlank()) {
                    _updateState.value = rollBack(ytdlpDir, binary, backup, DownloadErrorKind.RUNTIME)
                    return@withLock _updateState.value
                }

                _updateState.value = if (updateResult == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                    YtdlpUpdateState.UpToDate(verified)
                } else {
                    YtdlpUpdateState.Ready(verified)
                }
                _updateState.value
            } finally {
                updating = false
                runCatching { backup.deleteRecursively() }
            }
        }
    }

    private suspend fun rollBack(
        ytdlpDir: File,
        binary: File,
        backup: File,
        kind: DownloadErrorKind
    ): YtdlpUpdateState {
        val restored = runCatching {
            binary.delete()
            if (backup.exists()) {
                backup.copyTo(binary, overwrite = true)
            } else {
                ytdlpDir.deleteRecursively()
                ytdlpDir.mkdirs()
                YoutubeDL.getInstance().init_ytdlp(context, ytdlpDir)
            }
            !currentVersion().isNullOrBlank()
        }.getOrDefault(false)
        return if (restored) {
            YtdlpUpdateState.RolledBack(kind)
        } else {
            YtdlpUpdateState.Failed(kind)
        }
    }

    private fun classifyUpdateFailure(error: Throwable): DownloadErrorKind {
        val chain = generateSequence(error) { it.cause }.toList()
        if (chain.any {
                it is UnknownHostException || it is SocketTimeoutException ||
                    it is ConnectException || it is SSLException
            }
        ) {
            return DownloadErrorKind.NETWORK
        }
        val text = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
        val networkWords = listOf(
            "failed to connect", "connection", "timed out", "unable to resolve", "unknownhost", "network"
        )
        return if (networkWords.any(text::contains)) DownloadErrorKind.NETWORK else DownloadErrorKind.UPDATE
    }

    private companion object {
        const val UPDATE_TIMEOUT_MS = 60_000L
        const val VERSION_TIMEOUT_MS = 25_000L
    }
}
