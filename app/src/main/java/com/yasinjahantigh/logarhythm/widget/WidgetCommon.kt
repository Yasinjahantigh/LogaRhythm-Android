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

