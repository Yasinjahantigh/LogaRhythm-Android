package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.yasinjahantigh.logarhythm.playback.PlaybackService

internal fun startPlaybackAction(context: Context, action: String) {
    val intent = Intent(context, PlaybackService::class.java).setAction(action)
    try {
        ContextCompat.startForegroundService(context, intent)
    } catch (_: Exception) {
        context.startService(intent)
    }
}

class PlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        startPlaybackAction(context, PlaybackService.ACTION_PLAY_PAUSE)
    }
}

class NextActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        startPlaybackAction(context, PlaybackService.ACTION_NEXT)
    }
}

class PreviousActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        startPlaybackAction(context, PlaybackService.ACTION_PREVIOUS)
    }
}

class ShuffleWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MusicWidgetActions.entryPoint(context).playerController().toggleShuffle()
        MusicWidgetUpdater.refresh(context)
    }
}

class RepeatWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        MusicWidgetActions.entryPoint(context).playerController().cycleRepeat()
        MusicWidgetUpdater.refresh(context)
    }
}

class FavoriteWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = MusicWidgetActions.entryPoint(context)
        entryPoint.playerController().currentTrack.value?.let {
            entryPoint.playlistRepository().toggleFavorite(it.id)
        }
        MusicWidgetUpdater.refresh(context)
    }
}

class PlayQueueWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[indexKey] ?: return
        val trackId = parameters[trackIdKey] ?: return
        val controller = MusicWidgetActions.entryPoint(context).playerController()
        if (controller.queue.value.getOrNull(index)?.id == trackId) controller.playQueueItem(index)
        MusicWidgetUpdater.refresh(context)
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
