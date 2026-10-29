package com.yasinjahantigh.logarhythm.feature.queue

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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.player.EqualizerVisualizer
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull

/**
 * A queue row with free continuous drag-to-reorder. Songs that already played are read-only:
 * no drag handle and no swipe-to-delete, because their order is history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueItem(
    track: Track,
    isCurrent: Boolean,
    index: Int,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onDragStart: (index: Int, fingerViewportY: Float?) -> Unit,
    onDragDelta: (dyPx: Float) -> Unit,
    onDragEnd: (commit: Boolean) -> Unit,
    /** Maps a window-Y to the LazyColumn viewport Y; provided by the screen. */
    toViewportY: (windowY: Float) -> Float,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isPlayed: Boolean = false,
    isDragging: Boolean = false,
    dragTranslationY: Float = 0f
) {
    if (isPlayed) {
        QueueItemRow(
            track = track,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            isPlayed = true,
            isDragging = false,
            dragTranslationY = 0f,
            draggable = false,
            index = index,
            onClick = onClick,
            onDragStart = onDragStart,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            toViewportY = toViewportY,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(22.dp))
        )
    } else {
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
                .clip(RoundedCornerShape(22.dp)),
            backgroundContent = {
                val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (active) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_remove_from_queue),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        ) {
            QueueItemRow(
                track = track,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
                isPlayed = false,
                isDragging = isDragging,
                dragTranslationY = dragTranslationY,
                draggable = true,
                index = index,
                onClick = onClick,
                onDragStart = onDragStart,
                onDragDelta = onDragDelta,
                onDragEnd = onDragEnd,
                toViewportY = toViewportY
            )
        }
    }
}

@Composable
private fun QueueItemRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPlayed: Boolean,
    isDragging: Boolean,
    dragTranslationY: Float,
    draggable: Boolean,
    index: Int,
    onClick: () -> Unit,
    onDragStart: (index: Int, fingerViewportY: Float?) -> Unit,
    onDragDelta: (dyPx: Float) -> Unit,
    onDragEnd: (commit: Boolean) -> Unit,
    toViewportY: (windowY: Float) -> Float,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val title = track.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragTranslationY else 0f
                shadowElevation = if (isDragging) 12.dp.toPx() else 0f
            }
            .alpha(if (isPlayed) 0.55f else 1f)
            .background(if (isCurrent) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = if (draggable) 0.dp else 12.dp)
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
                    EqualizerVisualizer(
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

        if (draggable) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .onGloballyPositioned { handleCoords = it }
                    // Keyed on Unit so recomposition never restarts (and kills) the active drag.
                    // The list is untouched until drop, so `index` stays valid for the gesture.
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val fingerY = handleCoords?.let { coords ->
                                    runCatching {
                                        toViewportY(coords.positionInWindow().y + offset.y)
                                    }.getOrNull()
                                }
                                onDragStart(index, fingerY)
                            },
                            onDragEnd = { onDragEnd(true) },
                            onDragCancel = { onDragEnd(false) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
