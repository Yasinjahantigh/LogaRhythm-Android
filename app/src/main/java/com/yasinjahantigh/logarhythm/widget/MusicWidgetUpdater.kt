package com.yasinjahantigh.logarhythm.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicWidgetEntryPoint {
    fun playerController(): PlayerController
    fun playlistRepository(): PlaylistRepository
    fun musicDao(): MusicDao
}

internal data class MusicWidgetSnapshot(
    val track: Track?,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val favorite: Boolean,
    val upcoming: List<IndexedValue<Track>>
)

object MusicWidgetUpdater {
    private const val TAG = "MusicWidgetUpdater"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val widgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>> = listOf(
        MusicWidgetSmallReceiver::class.java to MusicWidgetSmall(),
        MusicWidgetStripReceiver::class.java to MusicWidgetStrip(),
        MusicWidgetMediumReceiver::class.java to MusicWidgetMedium(),
        MusicWidgetLargeReceiver::class.java to MusicWidgetLarge()
    )

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext) }
    }

    /**
     * Glance's updateAll() resolves widget ids through its own provider-to-receiver datastore, which
     * some launchers leave empty, so the widgets would never re-render. The platform app widget
     * ids are the authoritative source.
     */
    suspend fun refresh(context: Context) = mutex.withLock {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val glanceManager = GlanceAppWidgetManager(appContext)
        widgets.forEach { (receiver, widget) ->
            val ids = runCatching {
                appWidgetManager.getAppWidgetIds(ComponentName(appContext, receiver))
            }.getOrDefault(intArrayOf())
            ids.forEach { appWidgetId ->
                runCatching {
                    widget.update(appContext, glanceManager.getGlanceIdBy(appWidgetId))
                }.onFailure { Log.w(TAG, "update failed for widget $appWidgetId", it) }
            }
        }
    }

    internal suspend fun snapshot(context: Context): MusicWidgetSnapshot {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MusicWidgetEntryPoint::class.java
        )
        val controller = entryPoint.playerController()
        val track = controller.currentTrack.value
        val queue = controller.queue.value
        val controllerIndex = controller.currentQueueIndex()
        val currentIndex = if (controllerIndex in queue.indices && queue[controllerIndex].id == track?.id) {
            controllerIndex
        } else {
            queue.indexOfFirst { it.id == track?.id }
        }
        val upcoming = if (currentIndex >= 0) {
            queue.withIndex().drop(currentIndex + 1).take(4)
        } else {
            emptyList()
        }
        val duration = controller.duration.value.takeIf { it > 0L } ?: track?.durationMs ?: 0L
        val position = controller.position.value
        return MusicWidgetSnapshot(
            track = track,
            isPlaying = controller.isPlaying.value,
            position = if (duration > 0L) position.coerceIn(0L, duration) else position.coerceAtLeast(0L),
            duration = duration,
            shuffle = controller.shuffle.value,
            repeatMode = controller.repeatMode.value,
            favorite = track?.let {
                runCatching { entryPoint.playlistRepository().isTrackFavorite(it.id).first() }.getOrDefault(false)
            } ?: false,
            upcoming = upcoming
        )
    }
}
