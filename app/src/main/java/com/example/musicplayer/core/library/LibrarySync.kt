package com.example.musicplayer.core.library

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.MediaStore
import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.core.utils.PermissionUtils
import com.example.musicplayer.domain.repository.TrackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the app's library index in step with the device, independent of which screen is open.
 *
 * Before this existed the library was only scanned while the Library tab was on screen, so a fresh
 * install showed an empty Home after granting permission, and new downloads stayed invisible until
 * the user happened to visit that tab. Now the app scans whenever it comes to the foreground and
 * whenever Android's media index changes while it is visible.
 */
@Singleton
class LibrarySync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: TrackRepository,
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var waiting: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            requestRefresh(debounceMs = 1_500)
        }
    }

    /** Call when the UI becomes visible. Registers for media changes and refreshes once. */
    @Synchronized
    fun onForeground() {
        if (!observing) {
            runCatching {
                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
                )
                observing = true
            }
        }
        requestRefresh()
    }

    /** Call when the UI is no longer visible. */
    @Synchronized
    fun onBackground() {
        if (observing) {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
            observing = false
        }
    }

    /** Schedules a scan; bursts of requests collapse into one. A no-op without audio permission. */
    fun requestRefresh(debounceMs: Long = 0L, force: Boolean = false) {
        waiting?.cancel()
        waiting = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            mutex.withLock {
                if (!PermissionUtils.hasAudioPermission(context)) return@withLock
                try {
                    // Coming back to the app with an unchanged media index is the common case; the
                    // full MediaStore query + diff is skipped then (Android 11+ exposes a counter).
                    val stamp = currentStamp()
                    if (!force && stamp != null && stamp == preferencesManager.libraryGeneration.first()) {
                        return@withLock
                    }
                    trackRepository.refreshTracks()
                    if (stamp != null) preferencesManager.setLibraryGeneration(stamp)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the last good index; the Library screen reports scan failures to the user.
                }
            }
        }
    }

    /**
     * Identifies the state a scan would see: media index version/generation plus the app settings
     * that filter it. Null (always scan) before Android 11 or if the platform call fails.
     */
    private suspend fun currentStamp(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val version = MediaStore.getVersion(context)
            val generation = MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL)
            val minSec = preferencesManager.minTrackLengthSec.first()
            val excluded = preferencesManager.excludedFolders.first().sorted().hashCode()
            "$SCHEMA_TAG|$version|$generation|$minSec|$excluded"
        }.getOrNull()
    }

    private companion object {
        /** Bump when a scan must repopulate new columns even if the media index is unchanged. */
        const val SCHEMA_TAG = "v8"
    }
}
