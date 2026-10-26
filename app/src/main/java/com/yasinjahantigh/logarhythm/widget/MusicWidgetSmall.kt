package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.R

class MusicWidgetSmall : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = MusicWidgetUpdater.currentState()
        provideContent {
            if (state.track == null) WidgetEmptyState() else SmallWidgetContent(state)
        }
    }
}


@Composable
private fun SmallWidgetContent(state: MusicWidgetSnapshot) {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(ArtBackground).cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.BottomEnd
    ) {
        val provider = state.artwork.cover?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_music_placeholder)
        Image(
            provider = provider,
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Image(
            provider = ImageProvider(R.drawable.widget_scrim),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        WidgetIconButton(
            icon = if (state.isPlaying) R.drawable.ic_widget_pause_ember else R.drawable.ic_widget_play_ember,
            description = LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
            action = actionRunCallback<PlayPauseActionCallback>(),
            active = true,
            size = 44,
            modifier = GlanceModifier.padding(12.dp)
        )
    }
}
