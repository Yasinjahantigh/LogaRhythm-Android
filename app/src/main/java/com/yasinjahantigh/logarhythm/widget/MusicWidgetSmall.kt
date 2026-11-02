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
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.R

/**
 * 2x2 Compact Album Art widget with track details and full mini playback controls.
 */
class MusicWidgetSmall : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            if (state.track == null) WidgetEmptyState() else SmallWidgetContent(state)
        }
    }
}

@Composable
private fun SmallWidgetContent(state: MusicWidgetSnapshot) {
    val track = state.track ?: return
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ArtBackground)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        val provider = state.artwork.cover?.let { ImageProvider(it) }
            ?: ImageProvider(R.drawable.ic_music_placeholder)

        // Cover Background
        Image(
            provider = provider,
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark gradient scrim for readability
        Image(
            provider = ImageProvider(R.drawable.widget_scrim),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Foreground Content
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Top row: App indicator and Favorite button
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(22.dp)
                        .cornerRadius(11.dp)
                        .background(ArtControlSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_note),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp)
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetIconButton(
                    icon = if (state.favorite) R.drawable.ic_widget_favorite_active else R.drawable.ic_widget_favorite,
                    description = LocalString(if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    action = actionRunCallback<FavoriteWidgetAction>(),
                    active = false,
                    size = 28,
                    iconSize = 16,
                    surface = ArtControlSurface
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Title & Artist
            Text(
                text = track.title,
                style = TextStyle(color = ArtText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = track.artist,
                style = TextStyle(color = ArtSecondaryText, fontSize = 11.sp),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Controls row: Prev, Play/Pause, Next
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WidgetIconButton(
                    icon = R.drawable.ic_widget_previous,
                    description = LocalString(R.string.cd_skip_prev),
                    action = actionRunCallback<PreviousActionCallback>(),
                    size = 28,
                    iconSize = 16,
                    surface = ArtControlSurface
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .cornerRadius(18.dp)
                        .background(ArtText)
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
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetIconButton(
                    icon = R.drawable.ic_widget_next,
                    description = LocalString(R.string.cd_skip_next),
                    action = actionRunCallback<NextActionCallback>(),
                    size = 28,
                    iconSize = 16,
                    surface = ArtControlSurface
                )
            }
        }
    }
}
