package com.yasinjahantigh.logarhythm.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.feature.library.SortField
import com.yasinjahantigh.logarhythm.playback.PlaybackService
import com.yasinjahantigh.logarhythm.playback.PlayerController
import com.yasinjahantigh.logarhythm.playback.QueuePersistenceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicWidgetEntryPoint {
    fun playerController(): PlayerController
    fun playlistRepository(): PlaylistRepository
    fun musicDao(): MusicDao
    fun trackRepository(): TrackRepository
    fun preferencesManager(): PreferencesManager
    fun queuePersistenceManager(): QueuePersistenceManager
}

internal data class MusicWidgetArtwork(
    val blurred: Bitmap?,
    val cover: Bitmap?,
    val strip: Bitmap?,
    val thumbnails: List<Bitmap?>,
    val queueThumbnails: Map<String, Bitmap?> = emptyMap()
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
    val artwork: MusicWidgetArtwork,
    val queueItems: List<IndexedValue<Track>> = emptyList(),
    val currentQueueIndex: Int = -1,
    val totalQueueSize: Int = 0
)

/**
 * Builds the state the 3 consolidated widgets render and pushes it to the launcher.
 * Persists a lightweight snapshot to SharedPreferences so widgets never lose state across
 * process restarts, and binds the Queue widget directly to the live dynamic playback queue.
 */
object MusicWidgetUpdater {
    private const val TAG = "MusicWidgetUpdater"
    private const val PREFS_NAME = "music_widget_snapshot_cache"
    private const val KEY_SNAPSHOT_JSON = "snapshot_json"

    private const val UPDATE_DEBOUNCE_MS = 100L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshing = AtomicBoolean(false)
    private val fullRefreshPending = AtomicBoolean(false)
    @Volatile
    private var pendingUpdateJob: Job? = null
    @Volatile
    private var pendingFullUpdate = false

    @Volatile
    private var snapshot: MusicWidgetSnapshot? = null
    private var favoriteTrackId: String? = null
    private var favoriteValue: Boolean = false
    private val revisionKey = longPreferencesKey("music_widget_revision")

    private val allWidgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>> = listOf(
        MusicWidgetMediumReceiver::class.java to MusicWidgetMedium(),
        MusicWidgetPlayerReceiver::class.java to MusicWidgetPlayer(),
        MusicWidgetQueueReceiver::class.java to MusicWidgetQueue()
    )

    private val progressWidgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>> = listOf(
        MusicWidgetMediumReceiver::class.java to MusicWidgetMedium(),
        MusicWidgetPlayerReceiver::class.java to MusicWidgetPlayer()
    )

    fun requestUpdate(context: Context) {
        scheduleUpdate(context.applicationContext, full = true)
    }

    /** Only widgets that show a clock or progress bar need the periodic refresh. */
    fun requestProgressUpdate(context: Context) {
        scheduleUpdate(context.applicationContext, full = false)
    }

    private fun scheduleUpdate(appContext: Context, full: Boolean) {
        if (full) pendingFullUpdate = true
        pendingUpdateJob?.cancel()
        pendingUpdateJob = scope.launch {
            delay(UPDATE_DEBOUNCE_MS)
            val widgets = if (pendingFullUpdate) {
                pendingFullUpdate = false
                allWidgets
            } else {
                progressWidgets
            }
            refresh(appContext, widgets)
        }
    }

    suspend fun refresh(context: Context) = refresh(context, allWidgets)

    private data class ActiveWidgetTarget(
        val receiver: Class<out BroadcastReceiver>,
        val widget: GlanceAppWidget,
        val ids: IntArray
    )

    private fun findActiveWidgets(
        context: Context,
        widgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>>
    ): List<ActiveWidgetTarget> {
        val appContext = context.applicationContext
        val appWidgetManager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull()
            ?: return emptyList()
        val active = ArrayList<ActiveWidgetTarget>(widgets.size)
        for ((receiver, widget) in widgets) {
            val ids = runCatching {
                appWidgetManager.getAppWidgetIds(ComponentName(appContext, receiver))
            }.getOrDefault(intArrayOf())
            if (ids.isNotEmpty()) {
                active.add(ActiveWidgetTarget(receiver, widget, ids))
            }
        }
        return active
    }

    private suspend fun refresh(
        context: Context,
        widgets: List<Pair<Class<out BroadcastReceiver>, GlanceAppWidget>>
    ) {
        // Fast path: if no widgets of the requested type are placed on the home screen,
        // skip all bitmap decoding, blurring, Room queries, and SharedPreferences writes.
        val initialActive = findActiveWidgets(context, widgets)
        if (initialActive.isEmpty()) return

        if (!refreshing.compareAndSet(false, true)) {
            if (widgets === allWidgets) fullRefreshPending.set(true)
            return
        }
        try {
            var activeTargets = initialActive
            while (true) {
                val needsQueueThumbs = activeTargets.any { it.receiver == MusicWidgetQueueReceiver::class.java }
                publishState(context, includeQueueThumbs = needsQueueThumbs)
                render(context, activeTargets)
                if (!fullRefreshPending.getAndSet(false)) break
                activeTargets = findActiveWidgets(context, allWidgets)
                if (activeTargets.isEmpty()) break
            }
        } finally {
            refreshing.set(false)
        }
    }

    private data class LivePlayer(
        val trackId: String?,
        val index: Int,
        val position: Long,
        val duration: Long,
        val isPlaying: Boolean,
        val shuffle: Boolean,
        val repeatMode: Int
    )

    private suspend fun readLivePlayer(controller: PlayerController): Pair<LivePlayer?, Int> =
        withContext(Dispatchers.Main.immediate) {
            val controllerIndex = runCatching { controller.currentQueueIndex() }.getOrDefault(-1)
            val player = PlaybackService.livePlayer ?: return@withContext null to controllerIndex
            LivePlayer(
                trackId = player.currentMediaItem?.mediaId,
                index = player.currentMediaItemIndex,
                position = player.currentPosition,
                duration = player.duration,
                isPlaying = player.isPlaying,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            ) to controllerIndex
        }

    private suspend fun publishState(context: Context, includeQueueThumbs: Boolean = true) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MusicWidgetEntryPoint::class.java
        )
        val controller = entryPoint.playerController()
        var queue = controller.queue.value
        val (live, controllerIndex) = readLivePlayer(controller)

        // When launched cold (e.g., after process death), hydrate from QueuePersistenceManager.
        var restoredIndex = -1
        var restoredPos = 0L
        var restoredShuffle = false
        if (queue.isEmpty()) {
            val restored = withTimeoutOrNull(2_000L) {
                entryPoint.queuePersistenceManager().restoreQueue()
            }
            if (restored != null) {
                queue = restored.tracks
                restoredIndex = restored.currentIndex
                restoredPos = restored.positionMs
                restoredShuffle = restored.shuffle
            }
        }

        val liveTrackId = live?.trackId
        val currentIndex = when {
            liveTrackId != null -> queue.indexOfFirst { it.id == liveTrackId }
                .takeIf { it >= 0 }
                ?: live.index.takeIf { it in queue.indices }
                ?: -1
            controllerIndex in queue.indices -> controllerIndex
            restoredIndex in queue.indices -> restoredIndex
            else -> queue.indexOfFirst { it.id == controller.currentTrack.value?.id }
        }
        val track = queue.getOrNull(currentIndex) ?: controller.currentTrack.value
        val upcoming = if (currentIndex >= 0 && currentIndex + 1 < queue.size) {
            val end = minOf(currentIndex + 5, queue.size)
            ArrayList<IndexedValue<Track>>(end - (currentIndex + 1)).apply {
                for (i in (currentIndex + 1) until end) add(IndexedValue(i, queue[i]))
            }
        } else {
            emptyList()
        }
        val queueWindow = buildQueueDisplayWindow(queue, currentIndex, limit = 40)

        val duration = live?.duration?.takeIf { it > 0L }
            ?: controller.duration.value.takeIf { it > 0L }
            ?: track?.durationMs
            ?: 0L
        val position = live?.position?.coerceAtLeast(0L)
            ?: controller.position.value.takeIf { it > 0L }
            ?: restoredPos.coerceAtLeast(0L)
        val isPlaying = live?.isPlaying ?: controller.isPlaying.value
        val shuffle = live?.shuffle ?: if (queue.isNotEmpty() && controller.queue.value.isEmpty()) {
            restoredShuffle
        } else {
            controller.shuffle.value
        }
        val repeatMode = live?.repeatMode ?: controller.repeatMode.value

        if (track?.id != favoriteTrackId) {
            favoriteTrackId = track?.id
            favoriteValue = track?.let {
                withTimeoutOrNull(2_000L) {
                    entryPoint.playlistRepository().isTrackFavorite(it.id).first()
                } ?: false
            } ?: false
        }

        val artworkUri = track?.artworkUri
        val queueThumbs = if (includeQueueThumbs) {
            LinkedHashMap<String, Bitmap?>(24).also { map ->
                val count = minOf(15, queueWindow.size)
                for (i in 0 until count) {
                    val t = queueWindow[i].value
                    if (!map.containsKey(t.id)) {
                        map[t.id] = WidgetArtworkCache.thumbnail(context, t.artworkUri)
                    }
                }
            }
        } else {
            snapshot?.artwork?.queueThumbnails ?: emptyMap()
        }
        val artwork = MusicWidgetArtwork(
            blurred = WidgetArtworkCache.blurred(context, artworkUri),
            cover = WidgetArtworkCache.cover(context, artworkUri),
            strip = WidgetArtworkCache.cover(context, artworkUri, 160),
            thumbnails = upcoming.map { WidgetArtworkCache.thumbnail(context, it.value.artworkUri) },
            queueThumbnails = queueThumbs
        )

        val nextSnapshot = MusicWidgetSnapshot(
            track = track,
            isPlaying = isPlaying,
            position = if (duration > 0L) position.coerceIn(0L, duration) else position,
            duration = duration,
            shuffle = shuffle,
            repeatMode = repeatMode,
            favorite = favoriteValue,
            upcoming = upcoming,
            artwork = artwork,
            queueItems = queueWindow,
            currentQueueIndex = currentIndex,
            totalQueueSize = queue.size
        )
        snapshot = nextSnapshot
        persistSnapshot(context, nextSnapshot)
    }

    /**
     * Orders the Queue widget starting from the currently playing item followed by upcoming items
     * in exact playback order, then earlier items wrapped to the end so every queue mutation
     * (Play Next, Shuffle, Reorder, Skip) is immediately visible at the top of the widget.
     */
    internal fun buildQueueDisplayWindow(
        queue: List<Track>,
        currentIndex: Int,
        limit: Int = 40
    ): List<IndexedValue<Track>> {
        if (queue.isEmpty() || limit <= 0) return emptyList()
        val count = minOf(limit, queue.size)
        val result = ArrayList<IndexedValue<Track>>(count)
        if (currentIndex !in queue.indices) {
            for (i in 0 until count) result.add(IndexedValue(i, queue[i]))
            return result
        }
        var i = currentIndex
        while (i < queue.size && result.size < count) {
            result.add(IndexedValue(i, queue[i]))
            i++
        }
        var j = 0
        while (j < currentIndex && result.size < count) {
            result.add(IndexedValue(j, queue[j]))
            j++
        }
        return result
    }

    internal fun sortTracks(tracks: List<Track>, fieldName: String, directionName: String): List<Track> {
        val asc = directionName == "ASCENDING"
        val text = String.CASE_INSENSITIVE_ORDER
        fun <T> List<T>.orderedBy(comparator: Comparator<T>): List<T> =
            if (asc) sortedWith(comparator) else sortedWith(comparator.reversed())

        val field = runCatching { SortField.valueOf(fieldName) }.getOrDefault(SortField.TITLE)
        return when (field) {
            SortField.TITLE -> tracks.orderedBy(compareBy(text) { it.title })
            SortField.ARTIST -> tracks.orderedBy(compareBy(text) { it.artist })
            SortField.ALBUM -> tracks.orderedBy(compareBy(text) { it.album })
            SortField.YEAR -> tracks.orderedBy(compareBy { it.year })
            SortField.DATE_ADDED -> tracks.orderedBy(compareBy { it.dateAdded })
            SortField.DATE_MODIFIED -> tracks.orderedBy(compareBy { it.dateModified })
            SortField.DURATION -> tracks.orderedBy(compareBy { it.durationMs })
            SortField.RANDOM -> tracks.shuffled()
            else -> tracks.orderedBy(compareBy(text) { it.title })
        }
    }

    private fun persistSnapshot(context: Context, snap: MusicWidgetSnapshot) {
        runCatching {
            val root = JSONObject()
                .put("isPlaying", snap.isPlaying)
                .put("position", snap.position)
                .put("duration", snap.duration)
                .put("shuffle", snap.shuffle)
                .put("repeatMode", snap.repeatMode)
                .put("favorite", snap.favorite)
                .put("currentQueueIndex", snap.currentQueueIndex)
                .put("totalQueueSize", snap.totalQueueSize)
            snap.track?.let { t ->
                root.put("track", t.toJson())
            }
            val qArr = JSONArray()
            snap.queueItems.take(25).forEach { item ->
                qArr.put(
                    JSONObject()
                        .put("index", item.index)
                        .put("track", item.value.toJson())
                )
            }
            root.put("queueItems", qArr)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SNAPSHOT_JSON, root.toString())
                .apply()
        }
    }

    private fun loadPersistedSnapshot(context: Context): MusicWidgetSnapshot? {
        return runCatching {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT_JSON, null) ?: return null
            val root = JSONObject(raw)
            val track = root.optJSONObject("track")?.toTrack()
            val qArr = root.optJSONArray("queueItems") ?: JSONArray()
            val queueItems = (0 until qArr.length()).mapNotNull { i ->
                val obj = qArr.optJSONObject(i) ?: return@mapNotNull null
                val tr = obj.optJSONObject("track")?.toTrack() ?: return@mapNotNull null
                IndexedValue(obj.optInt("index", i), tr)
            }
            val artworkUri = track?.artworkUri
            MusicWidgetSnapshot(
                track = track,
                isPlaying = false,
                position = root.optLong("position", 0L),
                duration = root.optLong("duration", 0L),
                shuffle = root.optBoolean("shuffle", false),
                repeatMode = root.optInt("repeatMode", 0),
                favorite = root.optBoolean("favorite", false),
                upcoming = queueItems.drop(1).take(4),
                artwork = MusicWidgetArtwork(
                    blurred = null,
                    cover = null,
                    strip = null,
                    thumbnails = emptyList()
                ),
                queueItems = queueItems,
                currentQueueIndex = root.optInt("currentQueueIndex", -1),
                totalQueueSize = root.optInt("totalQueueSize", queueItems.size)
            )
        }.getOrNull()
    }

    private fun Track.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)
        .put("artworkUri", artworkUri ?: JSONObject.NULL)
        .put("uri", uri)

    private fun JSONObject.toTrack(): Track? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = optString("title"),
            artist = optString("artist"),
            album = optString("album"),
            durationMs = optLong("durationMs", 0L),
            artworkUri = optString("artworkUri").takeIf { it.isNotBlank() && it != "null" },
            sourceType = SourceType.LOCAL,
            uri = optString("uri")
        )
    }

    private suspend fun render(
        context: Context,
        activeTargets: List<ActiveWidgetTarget>
    ) {
        val appContext = context.applicationContext
        val glanceManager = GlanceAppWidgetManager(appContext)
        activeTargets.forEach { target ->
            target.ids.forEach { appWidgetId ->
                val finished = withTimeoutOrNull(5_000L) {
                    runCatching {
                        val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                        updateAppWidgetState(appContext, glanceId) {
                            it[revisionKey] = System.nanoTime()
                        }
                        target.widget.update(appContext, glanceId)
                    }
                        .onFailure { Log.w(TAG, "update failed for widget $appWidgetId", it) }
                        .isSuccess
                }
                if (finished == null) Log.w(TAG, "update timed out for widget $appWidgetId")
            }
        }
    }

    /** The snapshot the widgets render. Restores from persisted cache on cold start. */
    internal fun currentState(context: Context? = null): MusicWidgetSnapshot {
        snapshot?.let { return it }
        if (context != null) {
            loadPersistedSnapshot(context)?.let { restored ->
                snapshot = restored
                requestUpdate(context)
                return restored
            }
        }
        return MusicWidgetSnapshot(
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
}
