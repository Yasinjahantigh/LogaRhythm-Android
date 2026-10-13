package com.example.musicplayer.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.player.CuteEqualizerVisualizer
import com.example.musicplayer.ui.components.ArtworkImage
import com.example.musicplayer.ui.components.cleanMetaOrNull

/**
 * One row of the playing queue. It looks like every other track row in the app (gradient cover,
 * accent colour and live equaliser for the current song) plus a drag handle to reorder and
 * swipe-either-way to remove. Songs that already played are dimmed so "what is next" stands out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueItem(
    track: Track,
    isCurrent: Boolean,
    index: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onDragMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isPlayed: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(shape),
        backgroundContent = {
            val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (active) scheme.errorContainer else Color.Transparent)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_remove_from_queue),
                    tint = scheme.onErrorContainer
                )
            }
        }
    ) {
        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        val title = track.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
        val artist = track.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isPlayed) 0.55f else 1f)
                .background(if (isCurrent) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surface)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                .testTag("queue_item_${track.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                ArtworkImage(
                    uri = track.artworkUri,
                    fallbackKey = title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(15.dp),
                    contentDescription = stringResource(R.string.cd_artwork, title)
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.Black.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CuteEqualizerVisualizer(
                            isPlaying = isPlaying,
                            barColor = Color.White,
                            maxHeight = 20.dp,
                            barCount = 4
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$artist · ${TimeUtils.formatDuration(track.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragStart = { dragAccumulator = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount.y
                                val itemHeightPx = 64.dp.toPx()
                                if (dragAccumulator > itemHeightPx) {
                                    onDragMove(index, index + 1)
                                    dragAccumulator -= itemHeightPx
                                } else if (dragAccumulator < -itemHeightPx) {
                                    onDragMove(index, index - 1)
                                    dragAccumulator += itemHeightPx
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    tint = scheme.onSurfaceVariant
                )
            }
        }
    }
}
