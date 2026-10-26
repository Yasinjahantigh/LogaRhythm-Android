package com.yasinjahantigh.logarhythm.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.unit.ColorProvider
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

class MusicWidgetStrip : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = MusicWidgetUpdater.snapshot(context)
        val cover = WidgetArtworkCache.thumbnail(context, snapshot.track?.artworkUri)
        val isNight = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        provideContent {
            if (snapshot.track == null) StripEmptyState() else StripWidgetContent(snapshot, cover, isNight)
        }
    }
}


@Composable
private fun StripWidgetContent(snapshot: MusicWidgetSnapshot, cover: Bitmap?, isNight: Boolean) {
    val track = snapshot.track ?: return
    Row(
        modifier = GlanceModifier.fillMaxSize().background(WidgetBackground).cornerRadius(18.dp)
            .clickable(actionStartActivity<MainActivity>()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val provider = cover?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_placeholder)
        Image(
            provider = provider,
            contentDescription = null,
            modifier = GlanceModifier.size(40.dp).cornerRadius(10.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(track.title, style = TextStyle(color = WidgetText, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(track.artist, style = TextStyle(color = WidgetSecondaryText, fontSize = 11.sp), maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetIconButton(
                if (isNight) R.drawable.ic_widget_previous else R.drawable.ic_widget_previous_dark,
                LocalString(R.string.cd_skip_prev), actionRunCallback<PreviousActionCallback>(), size = 34,
                surface = WidgetControlSurface
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            WidgetIconButton(
                if (snapshot.isPlaying) {
                    if (isNight) R.drawable.ic_widget_pause else R.drawable.ic_widget_pause_dark
                } else {
                    if (isNight) R.drawable.ic_widget_play else R.drawable.ic_widget_play_dark
                },
                LocalString(if (snapshot.isPlaying) R.string.cd_pause else R.string.cd_play),
                actionRunCallback<PlayPauseActionCallback>(), active = true, size = 38,
                surface = WidgetControlSurface
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            WidgetIconButton(
                if (isNight) R.drawable.ic_widget_next else R.drawable.ic_widget_next_dark,
                LocalString(R.string.cd_skip_next), actionRunCallback<NextActionCallback>(), size = 34,
                surface = WidgetControlSurface
            )
        }
    }
}


@Composable
private fun StripEmptyState() {
    Row(
        modifier = GlanceModifier.fillMaxSize().background(WidgetBackground).cornerRadius(18.dp)
            .clickable(actionStartActivity<MainActivity>()).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = GlanceModifier.width(3.dp).height(24.dp).background(accentColor()).cornerRadius(2.dp)) {}
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = LocalString(R.string.app_name) + " · " + LocalString(R.string.no_track_playing),
            style = TextStyle(color = WidgetText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

