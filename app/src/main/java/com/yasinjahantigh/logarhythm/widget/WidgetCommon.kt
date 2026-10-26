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

internal val ArtText = dayNightColorProvider(day = Color(0xFFE8E4DC), night = Color(0xFFE8E4DC))

internal val ArtSecondaryText = dayNightColorProvider(day = Color(0xFFB8B2A8), night = Color(0xFFB8B2A8))

internal val ArtControlSurface = dayNightColorProvider(day = Color(0x26E8E4DC), night = Color(0x26E8E4DC))

internal val ArtProgressTrack = dayNightColorProvider(day = Color(0x40E8E4DC), night = Color(0x40E8E4DC))

internal val ArtBackground = dayNightColorProvider(day = Color(0xFF15181C), night = Color(0xFF15181C))

internal val WidgetBackground = dayNightColorProvider(day = Color(0xFFFBF8F2), night = Color(0xFF15181C))

internal val WidgetText = dayNightColorProvider(day = Color(0xFF15181C), night = Color(0xFFE8E4DC))

internal val WidgetSecondaryText = dayNightColorProvider(day = Color(0xFF625D55), night = Color(0xFFB8B2A8))

internal val WidgetAccent = dayNightColorProvider(day = Color(0xFF8A4F00), night = Color(0xFFF4B266))

internal val WidgetControlSurface = dayNightColorProvider(day = Color(0x148A4F00), night = Color(0x26E8E4DC))

/** Deterministic bar heights so the meter never jitters between refreshes. */
private val WaveformBars = intArrayOf(34, 58, 84, 46, 100, 64, 92, 72, 86, 50)


@Composable
internal fun WidgetIconButton(
    icon: Int,
    description: String,
    action: Action,
    active: Boolean = false,
    size: Int = 40,
    surface: ColorProvider = ArtControlSurface,
    modifier: GlanceModifier = GlanceModifier
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
            modifier = GlanceModifier.size(24.dp)
        )
    }
}


/**
 * Ember level meter: a waveform silhouette that fills up with the accent colour as the track
 * advances. Ten bars is the maximum a single Glance container may hold.
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
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(18.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = GlanceModifier.width(36.dp).height(3.dp).background(accentColor()).cornerRadius(2.dp)) {}
        Spacer(modifier = GlanceModifier.height(12.dp))
        Image(
            provider = ImageProvider(R.drawable.ic_music_placeholder),
            contentDescription = null,
            modifier = GlanceModifier.size(40.dp)
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = LocalString(R.string.app_name),
            style = TextStyle(color = accentColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = LocalString(R.string.no_track_playing),
            style = TextStyle(color = WidgetText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = LocalString(R.string.widget_open),
            style = TextStyle(color = WidgetSecondaryText, fontSize = 12.sp)
        )
    }
}


internal fun accentColor() = WidgetAccent


@Composable
internal fun LocalString(stringRes: Int): String = androidx.glance.LocalContext.current.getString(stringRes)
