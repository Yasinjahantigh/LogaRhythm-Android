package com.yasinjahantigh.logarhythm.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R

/**
 * Spotify-style gesture: dragging a row to the right past the threshold queues it as the next
 * track. The accent background and label only exist while the finger is moving, so rows at rest
 * stay completely transparent. Drags are clamped so a long swipe cannot throw the row off-screen.
 */
@Composable
fun SwipeToPlayNext(
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxWidth()) { content() }
        return
    }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 80.dp.toPx() }
    val maxOffset = with(density) { 128.dp.toPx() }
    val playNextLabel = stringResource(R.string.swipe_play_next)
    val accessibilityLabel = stringResource(R.string.swipe_play_next_action)
    val accent = MaterialTheme.colorScheme.primaryContainer
    val onAccent = MaterialTheme.colorScheme.onPrimaryContainer
    var offset by remember { mutableFloatStateOf(0f) }
    var crossedThreshold by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .drawBehind {
                if (offset > 0f) drawRect(accent)
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(accessibilityLabel) {
                        onPlayNext()
                        true
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 22.dp)
                .graphicsLayer { alpha = (offset / threshold).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = playNextLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = onAccent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = rememberDraggableState { delta ->
                        offset = (offset + delta).coerceIn(0f, maxOffset)
                        if (!crossedThreshold && offset >= threshold) {
                            crossedThreshold = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (crossedThreshold && offset < threshold) {
                            crossedThreshold = false
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { crossedThreshold = false },
                    onDragStopped = {
                        if (crossedThreshold) onPlayNext()
                        animate(
                            initialValue = offset,
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) { value, _ -> offset = value }
                        crossedThreshold = false
                    }
                )
                .graphicsLayer { translationX = offset }
        ) {
            content()
        }
    }
}
