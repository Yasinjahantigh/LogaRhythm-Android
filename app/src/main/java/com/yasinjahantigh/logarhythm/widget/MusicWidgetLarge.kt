package com.yasinjahantigh.logarhythm.widget

import android.appwidget.AppWidgetManager
import android.content.Context
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

/**
 * 4x4 Complete Player widget with now-playing controls and up-next queue preview.
 */
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

    private fun queueRows(context: Context, id: GlanceId): Int {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val reportedHeight = runCatching {
            appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        }.getOrDefault(0)
        val declaredMinHeight = appWidgetManager.getAppWidgetInfo(appWidgetId)?.minHeight ?: 0
        val maxHeight = if (reportedHeight in 1 until declaredMinHeight) 0 else reportedHeight
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
            .cornerRadius(26.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        // Blurred backdrop
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

        // Scrim
        Image(
            provider = ImageProvider(R.drawable.widget_scrim),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header Row: Track Info + Favorite
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val coverProvider = state.artwork.cover?.let { ImageProvider(it) }
                    ?: ImageProvider(R.drawable.ic_music_placeholder)

                Box(
                    modifier = GlanceModifier
                        .size(46.dp)
                        .cornerRadius(12.dp)
                        .background(ArtControlSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = coverProvider,
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = track.title,
                        style = TextStyle(color = ArtText, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = track.artist,
                        style = TextStyle(color = ArtSecondaryText, fontSize = 12.sp),
                        maxLines = 1
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                WidgetIconButton(
                    icon = if (state.favorite) R.drawable.ic_widget_favorite_active else R.drawable.ic_widget_favorite,
                    description = LocalString(if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    action = actionRunCallback<FavoriteWidgetAction>(),
                    size = 32,
                    iconSize = 18,
                    surface = ArtControlSurface
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Progress Bar
            val progress = if (state.duration > 0L) state.position.toFloat() / state.duration.toFloat() else 0f
            LinearProgressBar(
                progress = progress,
                height = 3,
                trackColor = ArtProgressTrack,
                progressColor = WidgetAccent
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Transport Row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shuffle
                WidgetIconButton(
                    icon = if (state.shuffle) R.drawable.ic_widget_shuffle_active else R.drawable.ic_widget_shuffle,
                    description = LocalString(R.string.cd_shuffle),
                    action = actionRunCallback<ShuffleWidgetAction>(),
                    size = 32,
                    iconSize = 18,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(8.dp))

                // Previous
                WidgetIconButton(
                    icon = R.drawable.ic_widget_previous,
                    description = LocalString(R.string.cd_skip_prev),
                    action = actionRunCallback<PreviousActionCallback>(),
                    size = 34,
                    iconSize = 20,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(8.dp))

                // Play/Pause (Prominent White Button)
                Box(
                    modifier = GlanceModifier
                        .size(42.dp)
                        .cornerRadius(21.dp)
                        .background(ArtText)
                        .clickable(actionRunCallback<PlayPauseActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(
                            if (state.isPlaying) R.drawable.ic_widget_pause_dark else R.drawable.ic_widget_play_dark
                        ),
                        contentDescription = LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = GlanceModifier.size(24.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.width(8.dp))

                // Next
                WidgetIconButton(
                    icon = R.drawable.ic_widget_next,
                    description = LocalString(R.string.cd_skip_next),
                    action = actionRunCallback<NextActionCallback>(),
                    size = 34,
                    iconSize = 20,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(8.dp))

                // Repeat
                val repeatIcon = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_widget_repeat_one_active
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_widget_repeat_active
                    else -> R.drawable.ic_widget_repeat
                }
                WidgetIconButton(
                    icon = repeatIcon,
                    description = LocalString(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) R.string.cd_repeat_one else R.string.cd_repeat
                    ),
                    action = actionRunCallback<RepeatWidgetAction>(),
                    size = 32,
                    iconSize = 18,
                    surface = ArtControlSurface
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Queue Section Container
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(QueueCardSurface)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LocalString(R.string.queue_up_next).uppercase(),
                            style = TextStyle(color = WidgetAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    if (state.upcoming.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = LocalString(R.string.queue_empty),
                                style = TextStyle(color = ArtSecondaryText, fontSize = 12.sp),
                                maxLines = 1
                            )
                        }
                    } else {
                        state.upcoming.take(queueRows).forEachIndexed { position, indexedTrack ->
                            QueueTrackRow(
                                track = indexedTrack,
                                thumbnail = state.artwork.thumbnails.getOrNull(position),
                                position = position
                            )
                        }
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
            .height(30.dp)
            .cornerRadius(8.dp)
            .clickable(
                actionRunCallback<PlayQueueWidgetAction>(
                    actionParametersOf(
                        PlayQueueWidgetAction.indexKey to track.index,
                        PlayQueueWidgetAction.trackIdKey to track.value.id
                    )
                )
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(Locale.US, "%02d", position + 1),
            style = TextStyle(color = WidgetAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        val provider = thumbnail?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_placeholder)
        Image(
            provider = provider,
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp).cornerRadius(6.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(track.value.title, style = TextStyle(color = ArtText, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(track.value.artist, style = TextStyle(color = ArtSecondaryText, fontSize = 10.sp), maxLines = 1)
        }
    }
}
