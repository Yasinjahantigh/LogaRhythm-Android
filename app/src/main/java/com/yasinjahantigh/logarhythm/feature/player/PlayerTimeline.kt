package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import kotlin.random.Random
import androidx.compose.runtime.produceState
import com.yasinjahantigh.logarhythm.data.waveform.WaveformStore

private const val WAVE_SAMPLES = 160

/**
 * A stable, song-specific "fingerprint" waveform. It is not decoded audio (that would be costly
 * and unnecessary): it is smoothed noise seeded by the track id, so each song always shows the
 * same distinctive shape, which makes the scrubber feel like part of the song instead of a
 * generic slider.
 */
private fun fingerprint(seed: Int): FloatArray {
    val rnd = Random(seed)
    val raw = FloatArray(WAVE_SAMPLES + 2) { rnd.nextFloat() }
    return FloatArray(WAVE_SAMPLES) { i ->
        val smooth = (raw[i] + 2f * raw[i + 1] + raw[i + 2]) / 4f
        (0.2f + 0.8f * smooth).coerceIn(0.2f, 1f)
    }
}

/**
 * Waveform scrubber with elapsed / remaining time and the +/-10 s keys on the same line.
 *
 * It owns its drag state and reads [positionState] only in the draw phase (bars) and in a tiny
 * local recomposition (clock), so playback ticks never recompose the surrounding player.
 * Always laid out left-to-right like a tape, in every UI language.
 */
@Composable
fun PlaybackTimeline(
    positionState: State<Long>,
    durationMs: Long,
    trackKey: String,
    showRemainingTime: Boolean,
    onRemainingTimeClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val duration = durationMs.coerceAtLeast(1L)
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        val dragging = dragFraction != null
        val swell by animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(),
            label = "timeline_swell"
        )
        val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
        val fallback = remember(trackKey) { fingerprint(trackKey.hashCode()) }
        // The seeded fingerprint shows instantly; the real decoded waveform replaces it once ready.
        val amplitudes by produceState(initialValue = fallback, trackKey) {
            value = fallback
            WaveformStore.load(appContext, trackKey)?.let { value = it }
        }
        val active = MaterialTheme.colorScheme.primary
        val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        val currentOnSeek by rememberUpdatedState(onSeek)
        val haptic = LocalHapticFeedback.current

        val shownMs = dragFraction?.let { (it * duration).toLong() } ?: positionState.value
        val progressForSemantics = (shownMs.toFloat() / duration).coerceIn(0f, 1f)
        val description = stringResource(R.string.cd_seek_bar)

        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("player_seek_bar")
                    .semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(progressForSemantics, 0f..1f)
                        setProgress { target ->
                            currentOnSeek((target.coerceIn(0f, 1f) * duration).toLong())
                            true
                        }
                    }
                    .pointerInput(duration) {
                        detectTapGestures { offset ->
                            val f = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            currentOnSeek((f * duration).toLong())
                        }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragFraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragFraction?.let { currentOnSeek((it * duration).toLong()) }
                                dragFraction = null
                            },
                            onDragCancel = { dragFraction = null },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fraction = dragFraction ?: (positionState.value.toFloat() / duration).coerceIn(0f, 1f)
                    val barWidth = 3.dp.toPx()
                    val slot = 6.dp.toPx()
                    val bars = (size.width / slot).toInt().coerceAtLeast(8)
                    val step = size.width / bars
                    val cy = size.height / 2f
                    val maxBar = size.height * 0.86f
                    val playX = size.width * fraction
                    for (i in 0 until bars) {
                        val x = step * i + step / 2f
                        val amp = amplitudes[(i * amplitudes.size / bars).coerceIn(0, amplitudes.size - 1)]
                        // Bars near the playhead rise a little while scrubbing.
                        val near = 1f - (kotlin.math.abs(x - playX) / (size.width * 0.12f)).coerceIn(0f, 1f)
                        val h = (maxBar * amp * (1f + 0.18f * swell * near)).coerceAtMost(size.height)
                        drawRoundRect(
                            color = if (x <= playX) active else idle,
                            topLeft = Offset(x - barWidth / 2f, cy - h / 2f),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }
                    val headHalf = (size.height / 2f) - 1.dp.toPx()
                    drawLine(
                        color = active,
                        start = Offset(playX, cy - headHalf),
                        end = Offset(playX, cy + headHalf),
                        strokeWidth = 2.5.dp.toPx() + 1.5.dp.toPx() * swell,
                        cap = StrokeCap.Round
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TimeUtils.formatDuration(shownMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (dragging) active else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (dragging) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(start = 2.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRewind10,
                        modifier = Modifier.size(40.dp).testTag("player_rewind_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = stringResource(R.string.cd_rewind_10),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = onForward10,
                        modifier = Modifier.size(40.dp).testTag("player_forward_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = stringResource(R.string.cd_forward_10),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = if (showRemainingTime) {
                        "-${TimeUtils.formatDuration((durationMs - shownMs).coerceAtLeast(0L))}"
                    } else {
                        TimeUtils.formatDuration(durationMs)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onRemainingTimeClick)
                        .padding(end = 2.dp)
                )
            }
        }
    }
}
