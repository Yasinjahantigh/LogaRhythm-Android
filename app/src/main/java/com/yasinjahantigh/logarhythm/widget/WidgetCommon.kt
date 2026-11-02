package com.yasinjahantigh.logarhythm.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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

// Crisp text and surface colors for artwork/dark backgrounds
internal val ArtText = ColorProvider(Color.White)
internal val ArtSecondaryText = ColorProvider(Color(0xFFB0B3B8))
internal val ArtControlSurface = ColorProvider(Color(0x26FFFFFF))
internal val ArtControlSurfaceActive = ColorProvider(Color(0x40FFFFFF))
internal val ArtProgressTrack = ColorProvider(Color(0x33FFFFFF))
internal val ArtBackground = ColorProvider(Color(0xFF101215))

// Queue / Samsung Music style widget colors
internal val QueueBackground = ColorProvider(Color(0xFF101215))
internal val QueueCardSurface = ColorProvider(Color(0xFF1E2127))
internal val QueueControlSurface = ColorProvider(Color(0x1AFFFFFF))
internal val QueueActiveItemSurface = ColorProvider(Color(0x2EF4B266))
internal val QueueTextPrimary = ColorProvider(Color(0xFFF5F5F7))
internal val QueueTextSecondary = ColorProvider(Color(0xFF8E9298))
internal val QueueActiveTextPrimary = ColorProvider(Color(0xFFF4B266))
internal val QueueActiveTextSecondary = ColorProvider(Color(0xCCF4B266))

// Adaptive Day/Night widget colors for Card & Strip widgets
internal val WidgetBackground = dayNightColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF14161A))
internal val WidgetCardBackground = dayNightColorProvider(day = Color(0xFFF3F4F6), night = Color(0xFF1E2126))
internal val WidgetText = dayNightColorProvider(day = Color(0xFF121418), night = Color(0xFFFFFFFF))
internal val WidgetSecondaryText = dayNightColorProvider(day = Color(0xFF6B7280), night = Color(0xFF9E9EA4))
internal val WidgetAccent = dayNightColorProvider(day = Color(0xFFE07A00), night = Color(0xFFF4B266))
internal val WidgetControlSurface = dayNightColorProvider(day = Color(0x14000000), night = Color(0x26FFFFFF))
internal val WidgetProgressTrack = dayNightColorProvider(day = Color(0x1F000000), night = Color(0x33FFFFFF))

internal fun colorProvider(color: Color): ColorProvider = ColorProvider(color)

@Composable
internal fun WidgetIconButton(
    icon: Int,
    description: String,
    action: Action,
    active: Boolean = false,
    size: Int = 40,
    surface: ColorProvider = ArtControlSurface,
    modifier: GlanceModifier = GlanceModifier,
    iconSize: Int = 24
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .cornerRadius((size / 2).dp)
            .background(if (active) accentColor() else surface)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(iconSize.dp)
        )
    }
}

/** Smooth, modern linear progress bar for widgets. */
@Composable
internal fun LinearProgressBar(
    progress: Float,
    modifier: GlanceModifier = GlanceModifier,
    height: Int = 4,
    trackColor: ColorProvider = WidgetProgressTrack,
    progressColor: ColorProvider = WidgetAccent
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth().height(height.dp).cornerRadius((height / 2).dp),
        color = progressColor,
        backgroundColor = trackColor
    )
}

/** Deterministic bar heights so the meter never jitters between refreshes. */
private val WaveformBars = intArrayOf(34, 58, 84, 46, 100, 64, 92, 72, 86, 50)

/**
 * Waveform progress visualizer that fills with accent color as playback advances.
 */
@Composable
internal fun WaveformProgress(progress: Float, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(12.dp),
        verticalAlignment = Alignment.Vertical.Bottom
    ) {
        WaveformBars.forEachIndexed { index, level ->
            val fraction = (index + 0.5f) / WaveformBars.size
            Box(
                modifier = GlanceModifier.defaultWeight().height(12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(4.dp)
                        .height((level / 100f * 12f).dp)
                        .background(if (fraction <= progress) accentColor() else ArtProgressTrack)
                        .cornerRadius(2.dp)
                ) {}
            }
        }
    }
}

@Composable
internal fun WidgetEmptyState() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .cornerRadius(24.dp)
                .background(WidgetCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_music_placeholder),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp)
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = LocalString(R.string.app_name),
            style = TextStyle(color = WidgetAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = LocalString(R.string.no_track_playing),
            style = TextStyle(color = WidgetText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = LocalString(R.string.widget_open),
            style = TextStyle(color = WidgetSecondaryText, fontSize = 11.sp)
        )
    }
}

internal fun accentColor() = WidgetAccent

@Composable
internal fun widgetSnapshot(): MusicWidgetSnapshot {
    androidx.glance.currentState<androidx.datastore.preferences.core.Preferences>()
    return MusicWidgetUpdater.currentState()
}

@Composable
internal fun LocalString(stringRes: Int): String = androidx.glance.LocalContext.current.getString(stringRes)
