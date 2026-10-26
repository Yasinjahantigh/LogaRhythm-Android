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
        modifier = GlanceModifier.fillMaxSize().background(WidgetBackground).cornerRadius(18.dp)
            .clickable(actionStartActivity<MainActivity>()).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val provider = state.artwork.strip?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_placeholder)
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
                if (state.isPlaying) R.drawable.ic_widget_pause_ember else R.drawable.ic_widget_play_ember,
                LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
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
