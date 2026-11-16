package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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

/**
 * Consolidated Compact Player widget.
 * Responsively adapts between 4x1 strip, 2x2 square artwork card, and 4x2 full media card.
 */
class MusicWidgetMedium : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 110.dp),
            DpSize(250.dp, 54.dp),
            DpSize(250.dp, 110.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = widgetSnapshot()
            if (state.track == null) {
                WidgetEmptyState()
            } else {
                val size = LocalSize.current
                when {
                    size.height < 85.dp -> StripLayout(state)
                    size.width < 200.dp -> SquareLayout(state)
                    else -> MediumCardLayout(state)
                }
            }
        }
    }
}

@Composable
private fun StripLayout(state: MusicWidgetSnapshot) {
    val track = state.track ?: return
    val isNight = LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val coverProvider = state.artwork.strip?.let { ImageProvider(it) }
        ?: state.artwork.cover?.let { ImageProvider(it) }
        ?: ImageProvider(R.drawable.ic_music_placeholder)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = coverProvider,
            contentDescription = null,
            modifier = GlanceModifier.size(40.dp).cornerRadius(12.dp),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = track.title,
                style = TextStyle(color = WidgetText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = TextStyle(color = WidgetSecondaryText, fontSize = 11.sp),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        WidgetIconButton(
            icon = if (isNight) R.drawable.ic_widget_previous else R.drawable.ic_widget_previous_dark,
            description = LocalString(R.string.cd_skip_prev),
            action = actionRunCallback<PreviousActionCallback>(),
            size = 30,
            iconSize = 16,
            surface = WidgetControlSurface
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Box(
            modifier = GlanceModifier
                .size(34.dp)
                .cornerRadius(17.dp)
                .background(WidgetAccent)
                .clickable(actionRunCallback<PlayPauseActionCallback>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (state.isPlaying) R.drawable.ic_widget_pause_dark else R.drawable.ic_widget_play_dark
                ),
                contentDescription = LocalString(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                modifier = GlanceModifier.size(18.dp)
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        WidgetIconButton(
            icon = if (isNight) R.drawable.ic_widget_next else R.drawable.ic_widget_next_dark,
            description = LocalString(R.string.cd_skip_next),
            action = actionRunCallback<NextActionCallback>(),
            size = 30,
            iconSize = 16,
            surface = WidgetControlSurface
        )
    }
}

@Composable
private fun SquareLayout(state: MusicWidgetSnapshot) {
    val track = state.track ?: return
    val coverProvider = state.artwork.cover?.let { ImageProvider(it) }
        ?: state.artwork.strip?.let { ImageProvider(it) }
        ?: ImageProvider(R.drawable.ic_music_placeholder)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ArtBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Image(
            provider = coverProvider,
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
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.Vertical.Bottom
        ) {
            Text(
                text = track.title,
                style = TextStyle(color = ArtText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = TextStyle(color = ArtSecondaryText, fontSize = 11.sp),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WidgetIconButton(
                    icon = R.drawable.ic_widget_previous,
                    description = LocalString(R.string.cd_skip_prev),
                    action = actionRunCallback<PreviousActionCallback>(),
                    size = 30,
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
                    size = 30,
                    iconSize = 16,
                    surface = ArtControlSurface
                )
            }
        }
    }
}

@Composable
private fun MediumCardLayout(state: MusicWidgetSnapshot) {
    val track = state.track ?: return
    val isNight = LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(26.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverProvider = state.artwork.cover?.let { ImageProvider(it) }
                ?: state.artwork.strip?.let { ImageProvider(it) }
                ?: ImageProvider(R.drawable.ic_music_placeholder)

            Box(
                modifier = GlanceModifier
                    .size(86.dp)
                    .cornerRadius(18.dp)
                    .background(WidgetCardBackground),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = coverProvider,
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(18.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = track.title,
                            style = TextStyle(color = WidgetText, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Text(
                            text = track.artist,
                            style = TextStyle(color = WidgetSecondaryText, fontSize = 12.sp),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    WidgetIconButton(
                        icon = if (state.favorite) R.drawable.ic_widget_favorite_active
                        else if (isNight) R.drawable.ic_widget_favorite
                        else R.drawable.ic_widget_favorite_dark,
                        description = LocalString(if (state.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                        action = actionRunCallback<FavoriteWidgetAction>(),
                        size = 28,
                        iconSize = 16,
                        surface = WidgetControlSurface
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                if (track.album.isNotBlank()) {
                    Text(
                        text = track.album,
                        style = TextStyle(color = WidgetAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                } else {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                }

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WidgetIconButton(
                        icon = if (state.shuffle) R.drawable.ic_widget_shuffle_active
                        else if (isNight) R.drawable.ic_widget_shuffle
                        else R.drawable.ic_widget_shuffle_dark,
                        description = LocalString(R.string.cd_shuffle),
                        action = actionRunCallback<ShuffleWidgetAction>(),
                        size = 28,
                        iconSize = 16,
                        surface = WidgetControlSurface
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    WidgetIconButton(
                        icon = if (isNight) R.drawable.ic_widget_previous else R.drawable.ic_widget_previous_dark,
                        description = LocalString(R.string.cd_skip_prev),
                        action = actionRunCallback<PreviousActionCallback>(),
                        size = 30,
                        iconSize = 18,
                        surface = WidgetControlSurface
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .size(36.dp)
                            .cornerRadius(18.dp)
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
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    WidgetIconButton(
                        icon = if (isNight) R.drawable.ic_widget_next else R.drawable.ic_widget_next_dark,
                        description = LocalString(R.string.cd_skip_next),
                        action = actionRunCallback<NextActionCallback>(),
                        size = 30,
                        iconSize = 18,
                        surface = WidgetControlSurface
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    val repeatIcon = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_widget_repeat_one_active
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_widget_repeat_active
                        else -> if (isNight) R.drawable.ic_widget_repeat else R.drawable.ic_widget_repeat_dark
                    }
                    WidgetIconButton(
                        icon = repeatIcon,
                        description = LocalString(R.string.cd_repeat),
                        action = actionRunCallback<RepeatWidgetAction>(),
                        size = 28,
                        iconSize = 16,
                        surface = WidgetControlSurface
                    )
                }
            }
        }
    }
}
