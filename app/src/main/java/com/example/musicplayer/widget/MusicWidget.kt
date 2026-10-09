package com.example.musicplayer.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.musicplayer.MainActivity

class MusicWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialPrefs = try {
            getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        } catch (_: Exception) {
            null
        }

        val artworkUriString = initialPrefs?.get(stringPreferencesKey("artwork_uri"))
        val artworkBitmap = if (!artworkUriString.isNullOrBlank()) {
            WidgetArtworkUtils.loadThumbnail(context, Uri.parse(artworkUriString))
        } else null

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val isPlaying = prefs[booleanPreferencesKey("is_playing")] ?: false
                val title = prefs[stringPreferencesKey("title")].orEmpty()
                val artist = prefs[stringPreferencesKey("artist")].orEmpty()

                WidgetContent(
                    context = context,
                    title = title,
                    artist = artist,
                    artworkBitmap = artworkBitmap,
                    isPlaying = isPlaying
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        title: String,
        artist: String,
        artworkBitmap: Bitmap?,
        isPlaying: Boolean
    ) {
        val displayTitle = if (title.isBlank()) context.getString(R.string.no_track_playing) else title
        val openAppAction = actionStartActivity<MainActivity>()

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork thumbnail (tappable to open app)
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(12.dp)
                        .clickable(openAppAction)
                ) {
                    val provider = if (artworkBitmap != null) {
                        ImageProvider(artworkBitmap)
                    } else {
                        ImageProvider(R.drawable.ic_music_placeholder)
                    }
                    Image(
                        provider = provider,
                        contentDescription = context.getString(R.string.cd_artwork, displayTitle),
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                // Track title & artist (tappable to open app)
                Column(
                    modifier = GlanceModifier.defaultWeight().clickable(openAppAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    if (artist.isNotBlank()) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = artist,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                // Playback controls: Previous, Play/Pause, Next
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .cornerRadius(18.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<PreviousActionCallback>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_previous),
                            contentDescription = context.getString(R.string.cd_widget_prev),
                            modifier = GlanceModifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    Box(
                        modifier = GlanceModifier
                            .size(44.dp)
                            .cornerRadius(22.dp)
                            .background(GlanceTheme.colors.primary)
                            .clickable(actionRunCallback<PlayPauseActionCallback>()),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                        Image(
                            provider = ImageProvider(iconRes),
                            contentDescription = context.getString(R.string.cd_widget_play_pause),
                            modifier = GlanceModifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .cornerRadius(18.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<NextActionCallback>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_next),
                            contentDescription = context.getString(R.string.cd_widget_next),
                            modifier = GlanceModifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
