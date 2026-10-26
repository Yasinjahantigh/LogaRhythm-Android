package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext) }
    }

    suspend fun refresh(context: Context) = mutex.withLock {
        val appContext = context.applicationContext
        MusicWidgetSmall().updateAll(appContext)
        MusicWidgetStrip().updateAll(appContext)
        MusicWidgetMedium().updateAll(appContext)
        MusicWidgetLarge().updateAll(appContext)
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
        return MusicWidgetSnapshot(
            track = track,
            isPlaying = controller.isPlaying.value,
            position = if (duration > 0L) controller.position.value.coerceIn(0L, duration) else controller.position.value.coerceAtLeast(0L),
            duration = duration,
            shuffle = controller.shuffle.value,
            repeatMode = controller.repeatMode.value,
            favorite = track?.let { entryPoint.playlistRepository().isTrackFavorite(it.id).first() } ?: false,
            upcoming = upcoming
        )
    }
}
