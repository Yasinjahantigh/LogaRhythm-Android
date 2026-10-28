package com.yasinjahantigh.logarhythm.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.Player
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import java.util.Locale

class MusicWidgetLarge : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            if (state.track == null) {
                WidgetEmptyState()
            } else {
                LargeWidgetContent(state, queueRows(context, id))
            }
        }
    }

    /**
     * This launcher reports a height smaller than the widget it hosts, so an implausible value is
     * treated as unknown and the full list is shown.
     */
    private fun queueRows(context: Context, id: GlanceId): Int {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val reportedHeight = runCatching {
            appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        }.getOrDefault(0)
        val declaredMinHeight = appWidgetManager.getAppWidgetInfo(appWidgetId)?.minHeight ?: 0
        val maxHeight = if (reportedHeight in 1 until declaredMinHeight) 0 else reportedHeight
        Log.d("MusicWidgetLarge", "reported=$reportedHeight min=$declaredMinHeight")
        return when {
            maxHeight <= 0 -> 4
            maxHeight >= 710 -> 4
            maxHeight >= 630 -> 3
            maxHeight >= 555 -> 2
            else -> 1
        }
    }
}


@Composable
private fun LargeWidgetContent(state: MusicWidgetSnapshot, queueRows: Int) {
    val track = state.track ?: return
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ArtBackground)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        state.artwork.blurred?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Image(
            provider = ImageProvider(R.drawable.ic_music_placeholder),
            contentDescription = null,
            modifier = GlanceModifier.size(72.dp)
        )
        Image(
            provider = ImageProvider(R.drawable.widget_scrim),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(modifier = GlanceModifier.fillMaxSize().padding(10.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = track.title,
                        style = TextStyle(color = ArtText, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = TextStyle(color = ArtSecondaryText, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetIconButton(
                    icon = if (state.favorite) R.drawable.ic_widget_favorite_filled else R.drawable.ic_widget_favorite,
                    description = LocalString(stringRes = if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    action = actionRunCallback<FavoriteWidgetAction>(),
                    active = state.favorite,
                    size = 34
                )
            }
            Spacer(modifier = GlanceModifier.height(3.dp))
            val progress = if (state.duration > 0L) state.position.toFloat() / state.duration.toFloat() else 0f
            WaveformProgress(progress.coerceIn(0f, 1f))
            Spacer(modifier = GlanceModifier.height(7.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetIconButton(R.drawable.ic_widget_shuffle, LocalString(R.string.cd_shuffle), actionRunCallback<ShuffleWidgetAction>(), state.shuffle, size = 34)
                Spacer(modifier = GlanceModifier.width(4.dp))
                WidgetIconButton(R.drawable.ic_widget_previous, LocalString(R.string.cd_skip_prev), actionRunCallback<PreviousActionCallback>(), size = 34)
                Spacer(modifier = GlanceModifier.width(4.dp))
                WidgetIconButton(
                    if (state.isPlaying) R.drawable.ic_widget_pause_ember else R.drawable.ic_widget_play_ember,
                    LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    actionRunCallback<PlayPauseActionCallback>(),
                    active = true,
                    size = 40
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                WidgetIconButton(R.drawable.ic_widget_next, LocalString(R.string.cd_skip_next), actionRunCallback<NextActionCallback>(), size = 34)
                Spacer(modifier = GlanceModifier.width(4.dp))
                WidgetIconButton(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat,
                    LocalString(if (state.repeatMode == Player.REPEAT_MODE_ONE) R.string.cd_repeat_one else R.string.cd_repeat),
                    actionRunCallback<RepeatWidgetAction>(),
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    size = 34
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = LocalString(R.string.queue_up_next).uppercase(),
                style = TextStyle(color = accentColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                if (state.upcoming.isEmpty()) {
                    Text(
                        text = LocalString(R.string.queue_empty),
                        style = TextStyle(color = ArtSecondaryText, fontSize = 11.sp),
                        maxLines = 1
                    )
                } else {
                    state.upcoming.take(queueRows).forEachIndexed { position, indexedTrack ->
                        QueueTrackRow(indexedTrack, state.artwork.thumbnails.getOrNull(position), position)
                    }
                }
            }
        }
    }
}


@Composable
private fun QueueTrackRow(track: IndexedValue<Track>, thumbnail: android.graphics.Bitmap?, position: Int) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(28.dp)
            .clickable(
                actionRunCallback<PlayQueueWidgetAction>(
                    actionParametersOf(
                        PlayQueueWidgetAction.indexKey to track.index,
                        PlayQueueWidgetAction.trackIdKey to track.value.id
                    )
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(Locale.US, "%02d", position + 1),
            style = TextStyle(color = accentColor(), fontSize = 10.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        val provider = thumbnail?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_placeholder)
        Image(
            provider = provider,
            contentDescription = null,
            modifier = GlanceModifier.size(26.dp).cornerRadius(6.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(track.value.title, style = TextStyle(color = ArtText, fontSize = 12.sp), maxLines = 1)
            Text(track.value.artist, style = TextStyle(color = ArtSecondaryText, fontSize = 10.sp), maxLines = 1)
        }
    }
}
