package com.yasinjahantigh.logarhythm.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Scrollbar with quick-jump letter bubble.
 * Reads [LazyListState.firstVisibleItemIndex] strictly inside the layout/offset lambda so
 * scrolling never triggers recomposition of the wrapped LazyColumn.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    getItemLetter: (Int) -> String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (itemCount < 10) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var currentLetter by remember { mutableStateOf("") }
    var thumbPositionRatio by remember { mutableFloatStateOf(0f) }
    var maxHeightPx by remember { mutableFloatStateOf(1f) }
    val thumbHeightDp = 44.dp
    val thumbHeightPx = with(LocalDensity.current) { thumbHeightDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { maxHeightPx = it.height.toFloat().coerceAtLeast(1f) }
    ) {
        content()

        fun computeThumbOffsetPx(): Float {
            val availableTrackHeight = (maxHeightPx - thumbHeightPx).coerceAtLeast(1f)
            val ratio = if (isDragging) {
                thumbPositionRatio
            } else {
                (listState.firstVisibleItemIndex.toFloat() / itemCount.toFloat()).coerceIn(0f, 1f)
            }
            return ratio * availableTrackHeight
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 16.dp)
                .pointerInput(itemCount, maxHeightPx) {
                    detectTapGestures(
                        onPress = { offset ->
                            val ratio = (offset.y / maxHeightPx).coerceIn(0f, 1f)
                            thumbPositionRatio = ratio
                            val targetIndex = (ratio * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentLetter = getItemLetter(targetIndex)
                            isDragging = true
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                            tryAwaitRelease()
                            isDragging = false
                        }
                    )
                }
                .pointerInput(itemCount, maxHeightPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val ratio = (offset.y / maxHeightPx).coerceIn(0f, 1f)
                            thumbPositionRatio = ratio
                            val targetIndex = (ratio * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentLetter = getItemLetter(targetIndex)
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val ratio = (change.position.y / maxHeightPx).coerceIn(0f, 1f)
                            thumbPositionRatio = ratio
                            val targetIndex = (ratio * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                            currentLetter = getItemLetter(targetIndex)
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, computeThumbOffsetPx().roundToInt()) }
                    .size(width = 6.dp, height = thumbHeightDp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
            )
        }

        AnimatedVisibility(
            visible = isDragging && currentLetter.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = -72,
                        y = (computeThumbOffsetPx() - 16).roundToInt()
                            .coerceIn(20, (maxHeightPx - 80).roundToInt().coerceAtLeast(20))
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentLetter,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
        }
    }
}
