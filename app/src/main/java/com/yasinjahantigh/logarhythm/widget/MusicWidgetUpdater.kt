package com.yasinjahantigh.logarhythm.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.playback.PlaybackService
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicWidgetEntryPoint {
    fun playerController(): PlayerController
    fun playlistRepository(): PlaylistRepository
    fun musicDao(): MusicDao
}

internal data class MusicWidgetArtwork(
    val blurred: Bitmap?,
    val cover: Bitmap?,
    val strip: Bitmap?,
    val thumbnails: List<Bitmap?>
)

internal data class MusicWidgetSnapshot(
    val track: Track?,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val favorite: Boolean,
    val upcoming: List<IndexedValue<Track>>,
    val artwork: MusicWidgetArtwork
)

/**
 * Builds the state the widgets render and pushes it to the launcher.
 *
 * The Glance composition only reads the prepared snapshot, so no database query, artwork decoding
 * or service lookup ever runs inside the widget session. A blocking call there keeps the session
 * locked and the widgets stop updating.
 */
object MusicWidgetUpdater {
    private const val TAG = "MusicWidgetUpdater"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshing = AtomicBoolean(false)
    private val fullRefreshPending = AtomicBoolean(false)

    @Volatile
    private var snapshot: MusicWidgetSnapshot? = null
    private var favoriteTrackId: String? = null
    private var favoriteValue: Boolean = false

    private val allWidgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>> = listOf(
        MusicWidgetSmallReceiver::class.java to MusicWidgetSmall(),
        MusicWidgetStripReceiver::class.java to MusicWidgetStrip(),
        MusicWidgetMediumReceiver::class.java to MusicWidgetMedium(),
        MusicWidgetLargeReceiver::class.java to MusicWidgetLarge()
    )

    private val progressWidgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>> = listOf(
        MusicWidgetMediumReceiver::class.java to MusicWidgetMedium(),
        MusicWidgetLargeReceiver::class.java to MusicWidgetLarge()
    )

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext, allWidgets) }
    }

    /** Only the widgets that show a clock or a progress bar need the periodic refresh. */
    fun requestProgressUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch { refresh(appContext, progressWidgets) }
    }

    suspend fun refresh(context: Context) = refresh(context, allWidgets)

    private suspend fun refresh(
        context: Context,
        widgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>>
    ) {
        // A dropped refresh is better than a queue of refreshes that pile up behind a slow render,
        // but a full refresh must never be dropped, so it is retried by the running one.
        if (!refreshing.compareAndSet(false, true)) {
            if (widgets === allWidgets) fullRefreshPending.set(true)
            return
        }
        try {
            var target = widgets
            while (true) {
                publishState(context)
                render(context, target)
                if (!fullRefreshPending.getAndSet(false)) break
                target = allWidgets
            }
        } finally {
            refreshing.set(false)
        }
    }

    private suspend fun publishState(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MusicWidgetEntryPoint::class.java
        )
        val controller = entryPoint.playerController()
        val queue = controller.queue.value
        val livePlayer = PlaybackService.livePlayer

        val liveTrackId = livePlayer?.currentMediaItem?.mediaId
        val currentIndex = when {
            liveTrackId != null -> queue.indexOfFirst { it.id == liveTrackId }
                .takeIf { it >= 0 }
                ?: livePlayer?.currentMediaItemIndex?.takeIf { it in queue.indices }
                ?: -1
            else -> controller.currentQueueIndex().takeIf { it in queue.indices }
                ?: queue.indexOfFirst { it.id == controller.currentTrack.value?.id }
        }
        val track = queue.getOrNull(currentIndex) ?: controller.currentTrack.value
        val upcoming = if (currentIndex >= 0) {
            queue.withIndex().drop(currentIndex + 1).take(4)
        } else {
            emptyList()
        }

        val duration = livePlayer?.duration?.takeIf { it > 0L }
            ?: controller.duration.value.takeIf { it > 0L }
            ?: track?.durationMs
            ?: 0L
        val position = livePlayer?.currentPosition?.coerceAtLeast(0L)
            ?: controller.position.value.coerceAtLeast(0L)
        val isPlaying = livePlayer?.isPlaying ?: controller.isPlaying.value

        if (track?.id != favoriteTrackId) {
            favoriteTrackId = track?.id
            favoriteValue = track?.let {
                withTimeoutOrNull(2_000L) {
                    entryPoint.playlistRepository().isTrackFavorite(it.id).first()
                } ?: false
            } ?: false
        }

        val artworkUri = track?.artworkUri
        val artwork = MusicWidgetArtwork(
            blurred = WidgetArtworkCache.blurred(context, artworkUri),
            cover = WidgetArtworkCache.cover(context, artworkUri),
            strip = WidgetArtworkCache.cover(context, artworkUri, 160),
            thumbnails = upcoming.map { WidgetArtworkCache.thumbnail(context, it.value.artworkUri) }
        )

        snapshot = MusicWidgetSnapshot(
            track = track,
            isPlaying = isPlaying,
            position = if (duration > 0L) position.coerceIn(0L, duration) else position,
            duration = duration,
            shuffle = livePlayer?.shuffleModeEnabled ?: controller.shuffle.value,
            repeatMode = livePlayer?.repeatMode ?: controller.repeatMode.value,
            favorite = favoriteValue,
            upcoming = upcoming,
            artwork = artwork
        )
    }

    private suspend fun render(
        context: Context,
        widgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>>
    ) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val glanceManager = GlanceAppWidgetManager(appContext)
        widgets.forEach { (receiver, widget) ->
            val ids = runCatching {
                appWidgetManager.getAppWidgetIds(ComponentName(appContext, receiver))
            }.getOrDefault(intArrayOf())
            ids.forEach { appWidgetId ->
                val finished = withTimeoutOrNull(5_000L) {
                    runCatching { widget.update(appContext, glanceManager.getGlanceIdBy(appWidgetId)) }
                        .onFailure { Log.w(TAG, "update failed for widget $appWidgetId", it) }
                        .isSuccess
                }
                if (finished == null) Log.w(TAG, "update timed out for widget $appWidgetId")
            }
        }
    }

    /** The snapshot the widgets render. Never touches the database or the player. */
    internal fun currentState(): MusicWidgetSnapshot = snapshot ?: MusicWidgetSnapshot(
        track = null,
        isPlaying = false,
        position = 0L,
        duration = 0L,
        shuffle = false,
        repeatMode = 0,
        favorite = false,
        upcoming = emptyList(),
        artwork = MusicWidgetArtwork(null, null, null, emptyList())
    )
}
