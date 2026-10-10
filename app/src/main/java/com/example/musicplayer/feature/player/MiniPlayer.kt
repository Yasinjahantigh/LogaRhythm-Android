package com.example.musicplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.ui.designsystem.AppDimensions
import com.example.musicplayer.ui.designsystem.AppShapes
import com.example.musicplayer.ui.designsystem.AppSpacing
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }

    val displayTitle = when {
        track.title.isBlank() ||
            track.title.equals("<unknown>", ignoreCase = true) ||
            track.title.equals("unknown", ignoreCase = true) ||
            track.title.equals("null", ignoreCase = true) -> stringResource(R.string.unknown_title)
        else -> track.title
    }

    val displayArtist = when {
        track.artist.isBlank() ||
            track.artist.equals("<unknown>", ignoreCase = true) ||
            track.artist.equals("unknown", ignoreCase = true) ||
            track.artist.equals("null", ignoreCase = true) -> stringResource(R.string.unknown_artist)
        else -> track.artist
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
            .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val current = dragOffsetX.value
                            if (current < -100f) {
                                onNext()
                            } else if (current > 100f) {
                                onPrevious()
                            }
                            dragOffsetX.animateTo(0f, spring())
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { dragOffsetX.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            dragOffsetX.snapTo((dragOffsetX.value + dragAmount).coerceIn(-180f, 180f))
                        }
                    }
                )
            }
            .clip(AppShapes.roundedLg)
            .clickable(onClick = onExpand)
            .testTag("mini_player"),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
        shape = AppShapes.roundedLg
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = if (isCompact) AppSpacing.xs else AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork thumbnail
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = stringResource(R.string.cd_artwork, displayTitle),
                    placeholder = painterResource(id = R.drawable.ic_music_placeholder),
                    error = painterResource(id = R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (isCompact) 38.dp else AppDimensions.artworkMiniPlayer)
                        .clip(AppShapes.roundedSm)
                )

                Spacer(modifier = Modifier.width(AppSpacing.md))

                // Title and Artist Column with generous width & smooth marquee
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = AppSpacing.xs)
                ) {
                    Text(
                        text = displayTitle,
                        style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1500,
                            spacing = MarqueeSpacing(24.dp)
                        )
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Balanced Controls Row (compact touch targets with 48dp touch bounds)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
                ) {
                    // Previous Track Button
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(AppDimensions.navButtonMini)
                            .testTag("mini_player_previous")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_skip_prev),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppDimensions.iconSm)
                        )
                    }

                    // Play / Pause Button (rebalanced size)
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(AppDimensions.playButtonMini)
                            .testTag("mini_player_play_pause"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                            modifier = Modifier.size(AppDimensions.iconMd)
                        )
                    }

                    // Next Track Button
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(AppDimensions.navButtonMini)
                            .testTag("mini_player_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.cd_skip_next),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(AppDimensions.iconSm)
                        )
                    }

                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(AppDimensions.navButtonMini)
                                .testTag("mini_player_dismiss")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close_player),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(AppDimensions.iconXs)
                            )
                        }
                    }
                }
            }

            // Subtle linear progress bar
            Box(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "MiniPlayer Normal Light")
@Composable
private fun MiniPlayerPreview() {
    MusicPlayerTheme {
        MiniPlayer(
            track = Track(
                id = "local:1",
                title = "Beham Miad (feat. Mehrad Hidden)",
                artist = "Alireza JJ, Mehrad Hidden",
                album = "Yadegari",
                durationMs = 210_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "uri"
            ),
            isPlaying = true,
            positionMs = 70_000L,
            durationMs = 210_000L,
            onTogglePlayPause = {},
            onNext = {},
            onPrevious = {},
            onExpand = {}
        )
    }
}

@Preview(showBackground = true, name = "MiniPlayer Dark RTL")
@Composable
private fun MiniPlayerDarkPreview() {
    MusicPlayerTheme(darkTheme = true) {
        MiniPlayer(
            track = Track(
                id = "local:2",
                title = "عنوان بسیار طولانی یک آهنگ زیبا برای بررسی رفتار مارکی و فونت",
                artist = "خواننده محبوب",
                album = "آلبوم نامشخص",
                durationMs = 180_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "uri"
            ),
            isPlaying = false,
            positionMs = 30_000L,
            durationMs = 180_000L,
            onTogglePlayPause = {},
            onNext = {},
            onPrevious = {},
            onExpand = {}
        )
    }
}
