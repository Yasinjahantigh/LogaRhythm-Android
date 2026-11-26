package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.yasinjahantigh.logarhythm.R

/**
 * Transport: shuffle · previous · PLAY · next · repeat.
 *
 * The play button is a living shape (squircle while playing, circle while paused) with a springy
 * press. Skip keys are soft circles; shuffle/repeat light up inside a tonal circle when active.
 * The row is always laid out left-to-right, in every UI language, like a physical deck.
 * The +/-10 s seek moved next to the timeline where it belongs.
 */
@Composable
fun FullPlayerControls(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val playSize = if (compact) 62.dp else 72.dp
    val skipSize = if (compact) 46.dp else 52.dp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            val isRepeatOne = repeatMode == Player.REPEAT_MODE_ONE
            val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF

            ModeToggle(
                icon = Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                active = shuffle,
                onClick = onToggleShuffle,
                modifier = Modifier.testTag("player_shuffle_button")
            )

            SkipKey(
                icon = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.cd_skip_prev),
                onClick = onPrevious,
                size = skipSize,
                modifier = Modifier.testTag("player_previous_button")
            )

            val playInteractionSource = remember { MutableInteractionSource() }
            val isPressed by playInteractionSource.collectIsPressedAsState()
            val playButtonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 320f),
                label = "play_button_press"
            )
            val corner by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else playSize / 2,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 260f),
                label = "play_button_morph"
            )

            FilledIconButton(
                onClick = onTogglePlayPause,
                interactionSource = playInteractionSource,
                shape = RoundedCornerShape(corner),
                modifier = Modifier
                    .size(playSize)
                    .scale(playButtonScale)
                    .testTag("player_play_pause_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (androidx.compose.animation.scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f)) +
                            androidx.compose.animation.fadeIn())
                            .togetherWith(
                                androidx.compose.animation.scaleOut(targetScale = 0.7f) +
                                    androidx.compose.animation.fadeOut()
                            )
                    },
                    label = "full_play_pause_icon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(if (compact) 32.dp else 36.dp)
                    )
                }
            }

            SkipKey(
                icon = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_skip_next),
                onClick = onNext,
                size = skipSize,
                modifier = Modifier.testTag("player_next_button")
            )

            ModeToggle(
                icon = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = stringResource(if (isRepeatOne) R.string.cd_repeat_one else R.string.cd_repeat),
                active = isRepeatActive,
                onClick = onCycleRepeat,
                modifier = Modifier.testTag("player_repeat_button")
            )
            }
        }
    }
}

@Composable
private fun SkipKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = scheme.onSurface,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun ModeToggle(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = if (active) scheme.primary.copy(alpha = 0.16f) else scheme.primary.copy(alpha = 0f),
        label = "mode_toggle_container"
    )
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier
            .size(42.dp)
            .background(container, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) scheme.primary else scheme.onSurface.copy(alpha = 0.58f),
            modifier = Modifier.size(21.dp)
        )
    }
}
