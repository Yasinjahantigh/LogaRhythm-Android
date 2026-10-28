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
import androidx.glance.appwidget.LinearProgressIndicator
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
import java.util.Locale

/** Large artwork background with title, progress and the full transport row. */
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
        modifier = GlanceModifier.fillMaxSize().background(ArtBackground).cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        (state.artwork.cover ?: state.artwork.blurred)?.let {
            Image(ImageProvider(it), null, GlanceModifier.fillMaxSize(), ContentScale.Crop)
        } ?: Image(
            ImageProvider(R.drawable.ic_music_placeholder),
            null,
            GlanceModifier.size(80.dp),
            ContentScale.Crop
        )
        Image(ImageProvider(R.drawable.widget_scrim), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_widget_note), null, GlanceModifier.size(13.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = LocalString(R.string.now_playing),
                    style = TextStyle(color = ArtSecondaryText, fontSize = 11.sp),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(5.dp))
            Text(
                text = track.title,
                style = TextStyle(color = ArtText, fontSize = 21.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = TextStyle(color = ArtSecondaryText, fontSize = 13.sp),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(8.dp))
            val progress = if (state.duration > 0L) {
                (state.position.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp).cornerRadius(2.dp),
                color = ArtText,
                backgroundColor = ArtProgressTrack
            )
            Spacer(GlanceModifier.height(2.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = formatTime(state.position),
                    style = TextStyle(color = ArtSecondaryText, fontSize = 10.sp),
                    maxLines = 1
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = formatTime(state.duration),
                    style = TextStyle(color = ArtSecondaryText, fontSize = 10.sp),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WidgetIconButton(
                    R.drawable.ic_widget_shuffle,
                    LocalString(R.string.cd_shuffle),
                    actionRunCallback<ShuffleWidgetAction>(),
                    active = state.shuffle,
                    size = 30,
                    surface = ArtTransparent
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetIconButton(
                    R.drawable.ic_widget_previous,
                    LocalString(R.string.cd_skip_prev),
                    actionRunCallback<PreviousActionCallback>(),
                    size = 34,
                    surface = ArtTransparent
                )
                Spacer(GlanceModifier.width(12.dp))
                WidgetIconButton(
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    actionRunCallback<PlayPauseActionCallback>(),
                    size = 42,
                    surface = ArtTransparent
                )
                Spacer(GlanceModifier.width(12.dp))
                WidgetIconButton(
                    R.drawable.ic_widget_next,
                    LocalString(R.string.cd_skip_next),
                    actionRunCallback<NextActionCallback>(),
                    size = 34,
                    surface = ArtTransparent
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetIconButton(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_widget_repeat_one else R.drawable.ic_widget_repeat,
                    LocalString(if (state.repeatMode == Player.REPEAT_MODE_ONE) R.string.cd_repeat_one else R.string.cd_repeat),
                    actionRunCallback<RepeatWidgetAction>(),
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    size = 30,
                    surface = ArtTransparent
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
