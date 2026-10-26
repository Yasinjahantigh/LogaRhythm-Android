package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.yasinjahantigh.logarhythm.playback.PlaybackService
import kotlinx.coroutines.delay

internal fun startPlaybackAction(context: Context, action: String) {
    val intent = Intent(context, PlaybackService::class.java).setAction(action)
    try {
        ContextCompat.startForegroundService(context, intent)
    } catch (_: Exception) {
        context.startService(intent)
    }
}

/**
 * The service applies the change asynchronously, so the widget is refreshed after a short beat to
 * make sure it renders the new state instead of the one captured before the action.
 */
private suspend fun refreshAfterAction(context: Context, delayMillis: Long = 450L) {
    delay(delayMillis)
    MusicWidgetUpdater.refresh(context)
}

class PlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        startPlaybackAction(context, PlaybackService.ACTION_PLAY_PAUSE)
        refreshAfterAction(context)
    }
}

class NextActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        startPlaybackAction(context, PlaybackService.ACTION_NEXT)
        refreshAfterAction(context)
    }
}

class PreviousActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        startPlaybackAction(context, PlaybackService.ACTION_PREVIOUS)
        refreshAfterAction(context)
    }
}

class ShuffleWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MusicWidgetActions.entryPoint(context).playerController().toggleShuffle()
        refreshAfterAction(context, 300L)
    }
}

class RepeatWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MusicWidgetActions.entryPoint(context).playerController().cycleRepeat()
        refreshAfterAction(context, 300L)
    }
}

class FavoriteWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = MusicWidgetActions.entryPoint(context)
        entryPoint.playerController().currentTrack.value?.let {
            entryPoint.playlistRepository().toggleFavorite(it.id)
        }
        refreshAfterAction(context, 300L)
    }
}

class PlayQueueWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[indexKey] ?: return
        val trackId = parameters[trackIdKey] ?: return
        val controller = MusicWidgetActions.entryPoint(context).playerController()
        if (controller.queue.value.getOrNull(index)?.id == trackId) controller.playQueueItem(index)
        refreshAfterAction(context)
    }

    companion object {
        val indexKey = ActionParameters.Key<Int>("queue_index")
        val trackIdKey = ActionParameters.Key<String>("queue_track_id")
    }
}

internal object MusicWidgetActions {
    fun entryPoint(context: Context) = dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        MusicWidgetEntryPoint::class.java
    )
}
