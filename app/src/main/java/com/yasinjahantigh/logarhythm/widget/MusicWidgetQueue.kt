package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
 * Samsung Music inspired Queue & Playlist widget.
 * Features a now-playing header with full playback controls and a scrollable, resizable list of songs
 * sorted according to the app's library sort preference. Tapping any song plays it immediately.
 */
class MusicWidgetQueue : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            QueueWidgetContent(state)
        }
    }
}

@Composable
private fun QueueWidgetContent(state: MusicWidgetSnapshot) {
    val track = state.track
    val hasTrack = track != null
    val currentTrackId = track?.id

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(QueueBackground)
            .cornerRadius(26.dp)
            .padding(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header: Now Playing Artwork, Title, Artist, and Transport Controls
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork (Left)
                val coverProvider = state.artwork.cover?.let { ImageProvider(it) }
                    ?: state.artwork.strip?.let { ImageProvider(it) }
                    ?: ImageProvider(R.drawable.ic_music_placeholder)

                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(14.dp)
                        .background(QueueCardSurface)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = coverProvider,
                        contentDescription = null,
                        modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = GlanceModifier.width(12.dp))

                // Info & Controls (Right)
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = if (hasTrack) track.title else LocalString(R.string.app_name),
                        style = TextStyle(
                            color = ArtText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = if (hasTrack) track.artist else LocalString(R.string.widget_select_to_play),
                        style = TextStyle(
                            color = ArtSecondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Transport controls: Shuffle, Prev, Play/Pause, Next, Repeat
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        WidgetIconButton(
                            icon = if (state.shuffle) R.drawable.ic_widget_shuffle_active else R.drawable.ic_widget_shuffle,
                            description = LocalString(R.string.cd_shuffle),
                            action = actionRunCallback<ShuffleWidgetAction>(),
                            active = false,
                            size = 28,
                            iconSize = 18,
                            surface = QueueControlSurface
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Previous
                        WidgetIconButton(
                            icon = R.drawable.ic_widget_previous,
                            description = LocalString(R.string.cd_skip_prev),
                            action = actionRunCallback<PreviousActionCallback>(),
                            size = 30,
                            iconSize = 18,
                            surface = QueueControlSurface
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Play/Pause (Prominent white circular button)
                        Box(
                            modifier = GlanceModifier
                                .size(34.dp)
                                .cornerRadius(17.dp)
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
                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Next
                        WidgetIconButton(
                            icon = R.drawable.ic_widget_next,
                            description = LocalString(R.string.cd_skip_next),
                            action = actionRunCallback<NextActionCallback>(),
                            size = 30,
                            iconSize = 18,
                            surface = QueueControlSurface
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))

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
                            active = false,
                            size = 28,
                            iconSize = 18,
                            surface = QueueControlSurface
                        )
                    }
                }
            }

            // Bottom Section: Rounded Inner Card with Scrollable Song List
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(QueueCardSurface)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                if (state.sortedTracks.isEmpty()) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = LocalString(R.string.widget_no_songs),
                            style = TextStyle(color = QueueTextSecondary, fontSize = 13.sp),
                            maxLines = 1
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(state.sortedTracks, itemId = { it.id.hashCode().toLong() }) { item ->
                            val isCurrent = item.id == currentTrackId
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .cornerRadius(10.dp)
                                    .background(if (isCurrent) QueueActiveItemSurface else colorProvider(Color.Transparent))
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .clickable(
                                        actionRunCallback<PlayTrackWidgetAction>(
                                            actionParametersOf(PlayTrackWidgetAction.trackIdKey to item.id)
                                        )
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = item.title,
                                        style = TextStyle(
                                            color = if (isCurrent) QueueActiveTextPrimary else QueueTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Text(
                                        text = item.artist,
                                        style = TextStyle(
                                            color = if (isCurrent) QueueActiveTextSecondary else QueueTextSecondary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        maxLines = 1
                                    )
                                }
                                if (isCurrent) {
                                    Spacer(modifier = GlanceModifier.width(6.dp))
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_note),
                                        contentDescription = null,
                                        modifier = GlanceModifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
