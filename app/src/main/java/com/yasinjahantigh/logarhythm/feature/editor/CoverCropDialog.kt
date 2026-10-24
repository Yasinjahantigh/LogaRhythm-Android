package com.yasinjahantigh.logarhythm.feature.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val CROP_VIEW_DP = 300

/** Square crop with pinch-to-zoom and pan; the preview is exactly the pixels that get saved. */
@Composable
fun CoverCropDialog(
    bitmap: Bitmap,
    onConfirm: (ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val containerPx = with(density) { CROP_VIEW_DP.dp.toPx() }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.tag_editor_crop_title)) },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.tag_editor_crop_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(CROP_VIEW_DP.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                zoom = newZoom
                                val (maxX, maxY) = CoverBitmap.maxOffset(bitmap, containerPx, newZoom)
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseScale = max(size.width / bitmap.width, size.height / bitmap.height)
                        val scale = baseScale * zoom
                        val drawnWidth = bitmap.width * scale
                        val drawnHeight = bitmap.height * scale
                        val left = (size.width - drawnWidth) / 2f + offsetX
                        val top = (size.height - drawnHeight) / 2f + offsetY
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
                            filterQuality = FilterQuality.High
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    working = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) {
                            CoverBitmap.cropToSquare(bitmap, containerPx, zoom, offsetX, offsetY)
                        }
                        working = false
                        if (bytes != null) onConfirm(bytes)
                    }
                }
            ) {
                Text(stringResource(if (working) R.string.saving else R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
