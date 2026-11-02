package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils

/**
 * 4x4 Immersive Large Player widget with blurred backdrop, artwork card, progress, and full controls.
 */
class MusicWidgetPlayer : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            if (state.track == null) WidgetEmptyState() else PlayerWidgetContent(state)
        }
    }
}

@Composable
private fun PlayerWidgetContent(state: MusicWidgetSnapshot) {
    val track = state.track ?: return

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ArtBackground)
            .cornerRadius(28.dp)
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
        }

        // Dark gradient scrim
        Image(
            provider = ImageProvider(R.drawable.widget_scrim),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Badge and Favorite
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(ArtControlSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_note),
                            contentDescription = null,
                            modifier = GlanceModifier.size(12.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = LocalString(R.string.now_playing).uppercase(),
                            style = TextStyle(color = ArtText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetIconButton(
                    icon = if (state.favorite) R.drawable.ic_widget_favorite_active else R.drawable.ic_widget_favorite,
                    description = LocalString(if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    action = actionRunCallback<FavoriteWidgetAction>(),
                    size = 32,
                    iconSize = 18,
                    surface = ArtControlSurface
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Center: Album Art Card
            val coverProvider = state.artwork.cover?.let { ImageProvider(it) }
                ?: ImageProvider(R.drawable.ic_music_placeholder)

            Box(
                modifier = GlanceModifier
                    .size(110.dp)
                    .cornerRadius(20.dp)
                    .background(ArtControlSurface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = coverProvider,
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Title & Artist
            Text(
                text = track.title,
                style = TextStyle(color = ArtText, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = if (track.album.isNotBlank()) "${track.artist} · ${track.album}" else track.artist,
                style = TextStyle(color = ArtSecondaryText, fontSize = 12.sp),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Progress Bar & Timestamps
            val progress = if (state.duration > 0L) state.position.toFloat() / state.duration.toFloat() else 0f
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                LinearProgressBar(
                    progress = progress,
                    height = 4,
                    trackColor = ArtProgressTrack,
                    progressColor = WidgetAccent
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = TimeUtils.formatDuration(state.position),
                        style = TextStyle(color = ArtSecondaryText, fontSize = 10.sp)
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = TimeUtils.formatDuration(state.duration),
                        style = TextStyle(color = ArtSecondaryText, fontSize = 10.sp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Transport Controls Row
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
                    size = 36,
                    iconSize = 20,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(10.dp))

                // Previous
                WidgetIconButton(
                    icon = R.drawable.ic_widget_previous,
                    description = LocalString(R.string.cd_skip_prev),
                    action = actionRunCallback<PreviousActionCallback>(),
                    size = 40,
                    iconSize = 22,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(10.dp))

                // Play/Pause (Large Prominent Button)
                Box(
                    modifier = GlanceModifier
                        .size(52.dp)
                        .cornerRadius(26.dp)
                        .background(ArtText)
                        .clickable(actionRunCallback<PlayPauseActionCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(
                            if (state.isPlaying) R.drawable.ic_widget_pause_dark else R.drawable.ic_widget_play_dark
                        ),
                        contentDescription = LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = GlanceModifier.size(28.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.width(10.dp))

                // Next
                WidgetIconButton(
                    icon = R.drawable.ic_widget_next,
                    description = LocalString(R.string.cd_skip_next),
                    action = actionRunCallback<NextActionCallback>(),
                    size = 40,
                    iconSize = 22,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(10.dp))

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
                    size = 36,
                    iconSize = 20,
                    surface = ArtControlSurface
                )
            }
        }
    }
}
