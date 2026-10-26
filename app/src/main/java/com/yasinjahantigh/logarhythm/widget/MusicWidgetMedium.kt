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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.R

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
    Box(
        modifier = GlanceModifier.fillMaxSize().background(ArtBackground).cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        state.artwork.blurred?.let {
            Image(ImageProvider(it), null, GlanceModifier.fillMaxSize(), ContentScale.Crop)
        }
        Image(ImageProvider(R.drawable.widget_scrim), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
            Text(track.title, style = TextStyle(color = ArtText, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(track.artist, style = TextStyle(color = ArtSecondaryText, fontSize = 12.sp), maxLines = 1)
            Spacer(modifier = GlanceModifier.height(6.dp))
            val progress = if (state.duration > 0L) state.position.toFloat() / state.duration.toFloat() else 0f
            WaveformProgress(progress.coerceIn(0f, 1f))
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetIconButton(R.drawable.ic_widget_previous, LocalString(R.string.cd_skip_prev), actionRunCallback<PreviousActionCallback>(), size = 36)
                Spacer(modifier = GlanceModifier.width(10.dp))
                WidgetIconButton(
                    if (state.isPlaying) R.drawable.ic_widget_pause_ember else R.drawable.ic_widget_play_ember,
                    LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    actionRunCallback<PlayPauseActionCallback>(), active = true, size = 42
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
                WidgetIconButton(R.drawable.ic_widget_next, LocalString(R.string.cd_skip_next), actionRunCallback<NextActionCallback>(), size = 36)
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}
