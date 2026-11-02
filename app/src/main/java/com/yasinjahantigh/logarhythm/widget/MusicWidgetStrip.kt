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
 * 4x1 Compact Pill / Strip widget for docks and notification bars.
 */
class MusicWidgetStrip : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            if (state.track == null) StripEmptyState() else StripWidgetContent(state)
        }
    }
}

@Composable
private fun StripWidgetContent(state: MusicWidgetSnapshot) {
    val track = state.track ?: return
    val isNight = LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val provider = state.artwork.strip?.let { ImageProvider(it) }
            ?: state.artwork.cover?.let { ImageProvider(it) }
            ?: ImageProvider(R.drawable.ic_music_placeholder)

        // Cover Thumbnail
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .cornerRadius(12.dp)
                .background(WidgetCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = provider,
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = GlanceModifier.width(10.dp))

        // Title & Artist
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = track.title,
                style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = track.artist,
                style = TextStyle(color = WidgetSecondaryText, fontSize = 11.5.sp),
                maxLines = 1
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        // Transport buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetIconButton(
                icon = if (isNight) R.drawable.ic_widget_previous else R.drawable.ic_widget_previous_dark,
                description = LocalString(R.string.cd_skip_prev),
                action = actionRunCallback<PreviousActionCallback>(),
                size = 32,
                iconSize = 18,
                surface = WidgetControlSurface
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Box(
                modifier = GlanceModifier
                    .size(38.dp)
                    .cornerRadius(19.dp)
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
            Spacer(modifier = GlanceModifier.width(4.dp))
            WidgetIconButton(
                icon = if (isNight) R.drawable.ic_widget_next else R.drawable.ic_widget_next_dark,
                description = LocalString(R.string.cd_skip_next),
                action = actionRunCallback<NextActionCallback>(),
                size = 32,
                iconSize = 18,
                surface = WidgetControlSurface
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            WidgetIconButton(
                icon = if (state.favorite) R.drawable.ic_widget_favorite_active
                else if (isNight) R.drawable.ic_widget_favorite
                else R.drawable.ic_widget_favorite_dark,
                description = LocalString(if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                action = actionRunCallback<FavoriteWidgetAction>(),
                size = 30,
                iconSize = 16,
                surface = WidgetControlSurface
            )
        }
    }
}

@Composable
private fun StripEmptyState() {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(36.dp)
                .cornerRadius(10.dp)
                .background(WidgetCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_note),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp)
            )
        }
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = LocalString(R.string.app_name),
                style = TextStyle(color = WidgetText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = LocalString(R.string.no_track_playing),
                style = TextStyle(color = WidgetSecondaryText, fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}
