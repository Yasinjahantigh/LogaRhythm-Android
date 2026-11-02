package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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

/**
 * 4x2 Media Card widget with prominent album art, track details, linear progress, and transport controls.
 */
class MusicWidgetMedium : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            if (state.track == null) WidgetEmptyState() else MediumWidgetContent(state)
        }
    }
}

@Composable
private fun MediumWidgetContent(state: MusicWidgetSnapshot) {
    val track = state.track ?: return
    val isNight = LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Artwork (Left)
            val coverProvider = state.artwork.cover?.let { ImageProvider(it) }
                ?: state.artwork.strip?.let { ImageProvider(it) }
                ?: ImageProvider(R.drawable.ic_music_placeholder)

            Box(
                modifier = GlanceModifier
                    .size(80.dp)
                    .cornerRadius(16.dp)
                    .background(WidgetCardBackground),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = coverProvider,
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            // Details, Progress & Controls (Right)
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Title and Favorite
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.title,
                        style = TextStyle(color = WidgetText, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    WidgetIconButton(
                        icon = if (state.favorite) R.drawable.ic_widget_favorite_active
                        else if (isNight) R.drawable.ic_widget_favorite
                        else R.drawable.ic_widget_favorite_dark,
                        description = LocalString(if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                        action = actionRunCallback<FavoriteWidgetAction>(),
                        size = 28,
                        iconSize = 16,
                        surface = WidgetControlSurface
                    )
                }

                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = track.artist,
                    style = TextStyle(color = WidgetSecondaryText, fontSize = 12.sp),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Progress Bar
                val progress = if (state.duration > 0L) state.position.toFloat() / state.duration.toFloat() else 0f
                LinearProgressBar(
                    progress = progress,
                    height = 3,
                    trackColor = WidgetProgressTrack,
                    progressColor = WidgetAccent
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // Transport Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shuffle
                    val shuffleIcon = if (state.shuffle) R.drawable.ic_widget_shuffle_active
                    else if (isNight) R.drawable.ic_widget_shuffle
                    else R.drawable.ic_widget_shuffle_dark
                    WidgetIconButton(
                        icon = shuffleIcon,
                        description = LocalString(R.string.cd_shuffle),
                        action = actionRunCallback<ShuffleWidgetAction>(),
                        size = 28,
                        iconSize = 16,
                        surface = WidgetControlSurface
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Previous
                    WidgetIconButton(
                        icon = if (isNight) R.drawable.ic_widget_previous else R.drawable.ic_widget_previous_dark,
                        description = LocalString(R.string.cd_skip_prev),
                        action = actionRunCallback<PreviousActionCallback>(),
                        size = 30,
                        iconSize = 18,
                        surface = WidgetControlSurface
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Play/Pause
                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .cornerRadius(18.dp)
                            .background(WidgetAccent)
                            .clickable(actionRunCallback<PlayPauseActionCallback>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(
                                if (state.isPlaying) R.drawable.ic_widget_pause_dark else R.drawable.ic_widget_play_dark
                            ),
                            contentDescription = LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                            modifier = GlanceModifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Next
                    WidgetIconButton(
                        icon = if (isNight) R.drawable.ic_widget_next else R.drawable.ic_widget_next_dark,
                        description = LocalString(R.string.cd_skip_next),
                        action = actionRunCallback<NextActionCallback>(),
                        size = 30,
                        iconSize = 18,
                        surface = WidgetControlSurface
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Repeat
                    val repeatIcon = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_widget_repeat_one_active
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_widget_repeat_active
                        else -> if (isNight) R.drawable.ic_widget_repeat else R.drawable.ic_widget_repeat_dark
                    }
                    WidgetIconButton(
                        icon = repeatIcon,
                        description = LocalString(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) R.string.cd_repeat_one else R.string.cd_repeat
                        ),
                        action = actionRunCallback<RepeatWidgetAction>(),
                        size = 28,
                        iconSize = 16,
                        surface = WidgetControlSurface
                    )
                }
            }
        }
    }
}
