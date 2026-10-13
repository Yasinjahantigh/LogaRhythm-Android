package com.example.musicplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.ui.components.ArtworkImage
import com.example.musicplayer.ui.components.cleanMetaOrNull
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The mini player's row: cover, title/artist, a play button wrapped in a progress ring, and next.
 *
 * Gestures replace the old extra buttons: swipe sideways for next/previous, swipe down to dismiss.
 * It carries no background of its own, so it can sit inside the navigation dock or in a
 * standalone [MiniPlayer] card.
 */
@Composable
fun MiniPlayerContent(
    track: Track,
    isPlaying: Boolean,
    progress: () -> Float,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme

    val title = track.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)
    val artSize = if (isCompact) 38.dp else 48.dp
    val ringSize = if (isCompact) 42.dp else 50.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val current = dragOffsetX.value
                            if (current < -100f) onNext() else if (current > 100f) onPrevious()
                            dragOffsetX.animateTo(0f, spring())
                        }
                    },
                    onDragCancel = { scope.launch { dragOffsetX.animateTo(0f, spring()) } },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        scope.launch { dragOffsetX.snapTo((dragOffsetX.value + amount).coerceIn(-180f, 180f)) }
                    }
                )
            }
            .pointerInput(onDismiss) {
                if (onDismiss != null) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > 70f) onDismiss()
                            total = 0f
                        },
                        onDragCancel = { total = 0f },
                        onVerticalDrag = { _, amount -> total += amount }
                    )
                }
            }
            .clickable(onClick = onExpand)
            .padding(start = 10.dp, end = 8.dp, top = if (isCompact) 6.dp else 8.dp, bottom = if (isCompact) 6.dp else 8.dp)
            .testTag("mini_player"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            uri = track.artworkUri,
            fallbackKey = title,
            modifier = Modifier.size(artSize),
            shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp),
            contentDescription = stringResource(R.string.cd_artwork, title)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1500,
                    spacing = MarqueeSpacing(24.dp)
                )
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Transport keeps a fixed left-to-right order in every language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val ringTrack = scheme.onSurface.copy(alpha = 0.12f)
                val ringColor = scheme.primary
                Box(modifier = Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                    // progress() is read in the draw phase only: playback ticks never recompose this.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 2.5.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(inset, inset)
                        drawArc(
                            color = ringTrack,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress().coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(ringSize - 9.dp)
                            .testTag("mini_player_play_pause"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(40.dp).testTag("mini_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.cd_skip_next),
                        tint = scheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** Standalone floating mini player, used where the navigation dock is not shown. */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: () -> Float,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(10.dp, shape, ambientColor = scheme.primary.copy(alpha = 0.25f), spotColor = scheme.primary.copy(alpha = 0.35f)),
        color = scheme.surfaceContainerHigh,
        shape = shape,
        border = BorderStroke(0.8.dp, scheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        MiniPlayerContent(
            track = track,
            isPlaying = isPlaying,
            progress = progress,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onExpand = onExpand,
            isCompact = isCompact,
            onDismiss = onDismiss
        )
    }
}

@Preview(showBackground = true, name = "MiniPlayer")
@Composable
private fun MiniPlayerPreview() {
    MusicPlayerTheme {
        MiniPlayer(
            track = Track(
                id = "local:1",
                title = "Beham Miad",
                artist = "Alireza JJ",
                album = "Yadegari",
                durationMs = 210_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "uri"
            ),
            isPlaying = true,
            progress = { 0.33f },
            onTogglePlayPause = {},
            onNext = {},
            onPrevious = {},
            onExpand = {}
        )
    }
}
