package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

private val VINYL_GROOVE_RADII = floatArrayOf(0.95f, 0.89f, 0.83f, 0.77f, 0.71f, 0.65f, 0.59f, 0.52f)

private enum class VinylGestureMode { UNDECIDED, SWIPE, SCRUB }

private fun easeOutCubic(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    val inv = 1f - t
    return 1f - inv * inv * inv
}

private fun easeInCubic(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * t
}

private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Animated equalizer bars. Reads phase only inside Canvas draw phase (zero recompositions). */
@Composable
fun EqualizerVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    maxHeight: Dp = 20.dp,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val phaseState = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq_anim")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.283185f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "eq_phase"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val barWidthDp = 3.5.dp
    val spacingDp = 3.dp
    val totalWidth = (barWidthDp * barCount) + (spacingDp * (barCount - 1))

    Box(
        modifier = modifier
            .size(width = totalWidth, height = maxHeight)
            .drawWithCache {
                val barWidthPx = barWidthDp.toPx()
                val spacingPx = spacingDp.toPx()
                val stepPx = barWidthPx + spacingPx
                val maxHPx = size.height
                val minHPx = 3.dp.toPx()
                val cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                val barBrush = Brush.verticalGradient(
                    colors = listOf(barColor, tertiaryColor),
                    startY = 0f,
                    endY = maxHPx
                )
                onDrawBehind {
                    val phase = phaseState.value
                    for (i in 0 until barCount) {
                        val factor = if (isPlaying) {
                            val wave = sin(phase.toDouble() * (1.0 + i * 0.4) + i * 1.2).toFloat()
                            ((wave + 1f) / 2f).coerceIn(0.18f, 1.0f)
                        } else {
                            0.20f
                        }

                        val barHeight = (maxHPx * factor).coerceAtLeast(minHPx)
                        val left = i * stepPx
                        val top = maxHPx - barHeight

                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(left, top),
                            size = Size(barWidthPx, barHeight),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
    )
}

/**
 * Interactive vinyl turntable stage with zero-conflict gestures and calm, unhurried sleeve unboxing:
 * - Horizontal swipe left/right: peeks the next/previous record sleeve and switches tracks smoothly.
 * - Touch-and-hold (190ms) on the vinyl OR vertical/curved drag along the outer grooves:
 *   stops the platter under your finger so you can rotate clockwise/counter-clockwise to scrub.
 * - Calm 1.3s track-change choreography: outgoing disc glides off, the new cardboard album sleeve
 *   enters and rests briefly, the black vinyl slides smoothly out of the sleeve onto the spindle,
 *   the sleeve glides away, and the tonearm lowers into the groove.
 */
@Composable
fun VinylSleeveStage(
    track: Track?,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    queue: List<Track> = emptyList(),
    currentIndex: Int = -1,
    onCommit: ((Int, Int) -> Unit)? = null,
    onArtworkClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var displayedTrack by remember { mutableStateOf(track) }
    var outgoingTrack by remember { mutableStateOf<Track?>(null) }
    var lastTrackId by remember { mutableStateOf(track?.id) }
    var lastQueueIndex by remember { mutableIntStateOf(currentIndex) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    var pendingSwipeDir by remember { mutableIntStateOf(0) }

    // 1f = settled on turntable; 0f..1f = calm sleeve unboxing choreography
    val unboxProgress = remember { Animatable(1f) }
    val swipeDragX = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubBasePositionMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var isUnboxingState by remember { mutableStateOf(false) }

    val latestPositionProvider by rememberUpdatedState(positionProvider)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnCommit by rememberUpdatedState(onCommit)
    val latestOnArtworkClick by rememberUpdatedState(onArtworkClick)
    val latestQueue by rememberUpdatedState(queue)
    val latestCurrentIndex by rememberUpdatedState(currentIndex)

    LaunchedEffect(track?.id) {
        val nextId = track?.id
        if (nextId != null && lastTrackId != null && nextId != lastTrackId) {
            val dir = when {
                pendingSwipeDir != 0 -> pendingSwipeDir
                lastQueueIndex >= 0 && currentIndex >= 0 && currentIndex != lastQueueIndex ->
                    if (currentIndex > lastQueueIndex) 1 else -1
                else -> 1
            }
            pendingSwipeDir = 0
            transitionDirection = dir
            outgoingTrack = displayedTrack
            displayedTrack = track
            isUnboxingState = true
            swipeDragX.snapTo(0f)
            unboxProgress.snapTo(0f)
            unboxProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1320, easing = LinearEasing)
            )
            outgoingTrack = null
            isUnboxingState = false
        } else {
            displayedTrack = track
        }
        lastTrackId = nextId
        lastQueueIndex = currentIndex
    }

    // Continuous turntable spin — updates Animatable without recomposing layout
    LaunchedEffect(isPlaying, isScrubbing, isUnboxingState) {
        while (isPlaying && !isScrubbing && !isUnboxingState) {
            val current = ((rotation.value % 360f) + 360f) % 360f
            if (current != rotation.value) rotation.snapTo(current)
            val remaining = 360f - current
            val duration = ((remaining / 360f) * 8500f).toInt().coerceAtLeast(16)
            rotation.animateTo(
                targetValue = current + remaining,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
            rotation.snapTo(0f)
        }
    }

    val tonearmAngleState = animateFloatAsState(
        targetValue = when {
            isUnboxingState -> -28f
            isScrubbing -> -8f
            isPlaying -> 0f
            else -> -24f
        },
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 120f),
        label = "tonearm_angle"
    )

    val glowColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier.testTag("vinyl_sleeve_stage"),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val stagePx = with(density) { minOf(maxWidth, maxHeight).toPx().coerceAtLeast(240f) }

        val peekIndex by remember(currentIndex, queue, stagePx) {
            derivedStateOf {
                if (isUnboxingState) null
                else coverPeekTarget(swipeDragX.value, stagePx, currentIndex, queue.size, false)
            }
        }

        var swipeAnimJob by remember { mutableStateOf<Job?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stagePx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        swipeAnimJob?.cancel()
                        swipeAnimJob = null

                        val startPos = down.position
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val minDim = minOf(size.width, size.height).toFloat()
                        val startDist = hypot(startPos.x - cx, startPos.y - cy)

                        var mode = VinylGestureMode.UNDECIDED
                        val swipeStartOffset = swipeDragX.value
                        var swipeDist = swipeStartOffset
                        var lastEventX = startPos.x
                        var lastEventTimeMs = down.uptimeMillis
                        var swipeVelocityX = 0f
                        var lastAngleRad = atan2(startPos.y - cy, startPos.x - cx)
                        var accumulatedDeg = 0f
                        var hapticBucket = 0

                        fun beginScrub(at: Offset) {
                            mode = VinylGestureMode.SCRUB
                            lastAngleRad = atan2(at.y - cy, at.x - cx)
                            accumulatedDeg = 0f
                            hapticBucket = 0
                            scrubBasePositionMs = latestPositionProvider()
                            scrubDeltaMs = 0L
                            isScrubbing = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        // Hold briefly (~260ms) on the vinyl platter to grab & scrub like a real turntable
                        var holdJob: Job? = if (startDist >= minDim * 0.10f) {
                            scope.launch {
                                delay(260L)
                                if (mode == VinylGestureMode.UNDECIDED) {
                                    beginScrub(startPos)
                                }
                            }
                        } else null

                        var pointerId = down.id
                        var totalMoved = 0f
                        var cancelled = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: break
                            pointerId = change.id
                            if (!change.pressed) {
                                break
                            }
                            if (change.isConsumed) {
                                cancelled = true
                                break
                            }

                            val pos = change.position
                            val totalDx = pos.x - startPos.x
                            val totalDy = pos.y - startPos.y
                            totalMoved = hypot(totalDx, totalDy)

                            val nowMs = change.uptimeMillis
                            val dtMs = (nowMs - lastEventTimeMs).coerceAtLeast(1L)
                            val instVelX = ((pos.x - lastEventX) / dtMs.toFloat()) * 1000f
                            if (pos.x != lastEventX) {
                                swipeVelocityX = swipeVelocityX * 0.45f + instVelX * 0.55f
                                lastEventX = pos.x
                                lastEventTimeMs = nowMs
                            }

                            if (mode == VinylGestureMode.UNDECIDED && totalMoved > viewConfiguration.touchSlop) {
                                holdJob?.cancel()
                                holdJob = null
                                val canSwipe = latestOnCommit != null && latestQueue.size > 1
                                mode = when {
                                    canSwipe && abs(totalDx) >= abs(totalDy) * 0.85f -> {
                                        // Allow rapid swiping even while the previous sleeve animation is finishing
                                        if (isUnboxingState) {
                                            isUnboxingState = false
                                            outgoingTrack = null
                                            scope.launch { unboxProgress.snapTo(1f) }
                                        }
                                        VinylGestureMode.SWIPE
                                    }
                                    startDist >= minDim * 0.14f -> {
                                        beginScrub(pos)
                                        VinylGestureMode.SCRUB
                                    }
                                    canSwipe -> VinylGestureMode.SWIPE
                                    else -> {
                                        beginScrub(pos)
                                        VinylGestureMode.SCRUB
                                    }
                                }
                            }

                            when (mode) {
                                VinylGestureMode.SWIPE -> {
                                    // Compute displacement from startPos BEFORE calling change.consume()!
                                    val proposed = (swipeStartOffset + (pos.x - startPos.x))
                                        .coerceIn(-stagePx, stagePx)
                                    change.consume()
                                    val hasNeighbour = coverPeekTarget(
                                        proposed,
                                        stagePx,
                                        latestCurrentIndex,
                                        latestQueue.size,
                                        false
                                    ) != null
                                    swipeDist = if (hasNeighbour) {
                                        proposed
                                    } else {
                                        (proposed * 0.18f).coerceIn(-stagePx * 0.22f, stagePx * 0.22f)
                                    }
                                    scope.launch { swipeDragX.snapTo(swipeDist) }
                                }
                                VinylGestureMode.SCRUB -> {
                                    change.consume()
                                    if (hypot(pos.x - cx, pos.y - cy) >= minDim * 0.07f) {
                                        val angleRad = atan2(pos.y - cy, pos.x - cx)
                                        var deltaRad = angleRad - lastAngleRad
                                        if (deltaRad > PI) deltaRad -= (2.0 * PI).toFloat()
                                        if (deltaRad < -PI) deltaRad += (2.0 * PI).toFloat()
                                        lastAngleRad = angleRad

                                        val deltaDeg = (deltaRad * 180f / PI.toFloat()).coerceIn(-40f, 40f)
                                        accumulatedDeg += deltaDeg
                                        scope.launch {
                                            rotation.snapTo(rotation.value + deltaDeg)
                                        }

                                        val rawDelta = (accumulatedDeg * 50f).toLong()
                                        val dur = latestDurationMs.coerceAtLeast(1L)
                                        val clamped = (scrubBasePositionMs + rawDelta).coerceIn(0L, dur)
                                        scrubDeltaMs = clamped - scrubBasePositionMs

                                        val bucket = (accumulatedDeg / 22f).toInt()
                                        if (bucket != hapticBucket) {
                                            hapticBucket = bucket
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                                VinylGestureMode.UNDECIDED -> Unit
                            }
                        }

                        holdJob?.cancel()

                        when (mode) {
                            VinylGestureMode.UNDECIDED -> {
                                if (!cancelled && totalMoved <= viewConfiguration.touchSlop) {
                                    latestOnArtworkClick?.invoke()
                                }
                            }
                            VinylGestureMode.SWIPE -> {
                                val distThreshold = abs(swipeDist) >= stagePx * 0.22f
                                val flickThreshold = abs(swipeDist) >= stagePx * 0.08f &&
                                    abs(swipeVelocityX) >= 420f &&
                                    (swipeDist * swipeVelocityX > 0f)
                                val commitIndex = latestCurrentIndex
                                val target = if (!cancelled && (distThreshold || flickThreshold)) {
                                    coverPeekTarget(
                                        swipeDist,
                                        stagePx,
                                        commitIndex,
                                        latestQueue.size,
                                        released = false
                                    )
                                } else null
                                if (target != null && latestOnCommit != null) {
                                    pendingSwipeDir = if (swipeDist < 0f) 1 else -1
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swipeAnimJob = scope.launch {
                                        swipeDragX.animateTo(
                                            targetValue = if (swipeDist > 0f) stagePx * 0.95f else -stagePx * 0.95f,
                                            animationSpec = tween(150)
                                        )
                                        latestOnCommit?.invoke(commitIndex, target)
                                        swipeDragX.snapTo(0f)
                                    }
                                } else {
                                    swipeAnimJob = scope.launch {
                                        swipeDragX.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 360f))
                                    }
                                }
                            }
                            VinylGestureMode.SCRUB -> {
                                if (!cancelled) {
                                    val dur = latestDurationMs.coerceAtLeast(0L)
                                    val target = (scrubBasePositionMs + scrubDeltaMs).coerceIn(0L, dur)
                                    latestOnSeek(target)
                                }
                                isScrubbing = false
                                scrubDeltaMs = 0L
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Soft feathered radial glow behind the platter — cached Brush, zero hard edges
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val radius = size.minDimension * 0.52f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = if (isPlaying) 0.22f else 0.10f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        )
                        onDrawBehind { drawCircle(brush = brush) }
                    }
            )

            // Peeking next/previous album sleeve when user drags horizontally in Vinyl mode
            if (peekIndex != null) {
                val neighbour = queue.getOrNull(peekIndex!!)
                if (neighbour != null) {
                    VinylRecordSleeve(
                        track = neighbour,
                        modifier = Modifier
                            .fillMaxSize(0.76f)
                            .graphicsLayer {
                                val dx = swipeDragX.value
                                val incomingFromRight = dx < 0f
                                val baseShift = if (incomingFromRight) stagePx * 0.92f else -stagePx * 0.92f
                                translationX = dx + baseShift
                                val p = (abs(dx) / (stagePx * 0.45f)).coerceIn(0f, 1f)
                                alpha = 0.35f + 0.65f * p
                                scaleX = 0.90f + 0.10f * p
                                scaleY = 0.90f + 0.10f * p
                                rotationZ = if (incomingFromRight) (1f - p) * 4f else -(1f - p) * 4f
                            }
                    )
                }
            }

            // Outgoing vinyl gliding cleanly away (Beat 1: 0.00 -> 0.26)
            if (outgoingTrack != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.92f)
                        .graphicsLayer {
                            val p = unboxProgress.value
                            if (p >= 0.28f) {
                                alpha = 0f
                            } else {
                                val exitT = smoothStep(p / 0.26f)
                                translationX = -transitionDirection * stagePx * 0.92f * exitT
                                alpha = (1f - exitT).coerceIn(0f, 1f)
                                val s = 1f - 0.10f * exitT
                                scaleX = s
                                scaleY = s
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    VinylDiscArtwork(
                        track = outgoingTrack,
                        rotationProvider = {
                            val exitT = smoothStep(unboxProgress.value / 0.26f)
                            -transitionDirection * 42f * exitT
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Active vinyl disc — slides horizontally out of the sleeve right opening (Beat 3: 0.36 -> 0.88)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .graphicsLayer {
                        val p = unboxProgress.value
                        if (p < 0.999f) {
                            val discOut = easeOutCubic((p - 0.38f) / 0.50f)
                            val discFade = smoothStep((p - 0.34f) / 0.10f)
                            // Starts tucked neatly inside the sleeve at -0.14*stagePx and glides smoothly to center (0f)
                            translationX = -stagePx * 0.16f * (1f - discOut)
                            val s = 0.80f + 0.20f * discOut
                            scaleX = s
                            scaleY = s
                            alpha = discFade
                        } else {
                            translationX = swipeDragX.value
                            val dragFactor = (abs(swipeDragX.value) / stagePx).coerceIn(0f, 1f)
                            val s = 1f - 0.06f * dragFactor
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.35f * dragFactor
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                VinylDiscArtwork(
                    track = displayedTrack,
                    rotationProvider = {
                        val p = unboxProgress.value
                        val emergeSpin = if (p < 0.999f) {
                            val discOut = easeOutCubic((p - 0.38f) / 0.50f)
                            (1f - discOut) * -75f
                        } else 0f
                        rotation.value + emergeSpin
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Cardboard album sleeve ("پک") drawn in front of the left side so the vinyl visibly slides OUT of it!
            // Beat 2: enters (0.14 -> 0.42), rests cleanly (0.42 -> 0.68), withdraws smoothly to left (0.68 -> 0.96)
            if (isUnboxingState && displayedTrack != null) {
                VinylRecordSleeve(
                    track = displayedTrack,
                    modifier = Modifier
                        .fillMaxSize(0.75f)
                        .graphicsLayer {
                            val p = unboxProgress.value
                            val sleeveIn = easeOutCubic((p - 0.14f) / 0.28f)
                            val sleeveOut = easeInCubic((p - 0.68f) / 0.28f)
                            val restX = -stagePx * 0.15f
                            val startX = transitionDirection * stagePx * 0.72f
                            val exitX = -stagePx * 0.98f
                            translationX = when {
                                p < 0.42f -> startX + (restX - startX) * sleeveIn
                                p < 0.68f -> restX
                                else -> restX + (exitX - restX) * sleeveOut
                            }
                            rotationZ = when {
                                p < 0.42f -> (transitionDirection * 4f) * (1f - sleeveIn) - 1.5f * sleeveIn
                                p < 0.68f -> -1.5f
                                else -> -1.5f - 5.5f * sleeveOut
                            }
                            alpha = (smoothStep((p - 0.12f) / 0.12f) * (1f - sleeveOut)).coerceIn(0f, 1f)
                        }
                )
            }

            // Realistic pivoted tonearm overlay in top-right corner (zero recompositions)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp, top = 8.dp)
                    .size(width = 50.dp, height = 96.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.74f, 0.12f)
                        rotationZ = tonearmAngleState.value
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pivot = Offset(size.width * 0.74f, 12f)
                    drawCircle(color = Color(0xFF42424A), radius = 12f, center = pivot)
                    drawCircle(color = Color(0xFF9C9CA8), radius = 7f, center = pivot)
                    drawLine(
                        color = Color(0xFFDADAE4),
                        start = pivot,
                        end = Offset(14f, size.height - 14f),
                        strokeWidth = 4.2f
                    )
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = 6.5f,
                        center = Offset(14f, size.height - 14f)
                    )
                }
            }

            // Live scrub HUD pill while user rotates the vinyl record
            AnimatedVisibility(
                visible = isScrubbing,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            ) {
                val targetMs = (scrubBasePositionMs + scrubDeltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
                val signPrefix = when {
                    scrubDeltaMs > 400L -> "+"
                    scrubDeltaMs < -400L -> "−"
                    else -> ""
                }
                val deltaLabel = "$signPrefix${TimeUtils.formatDuration(abs(scrubDeltaMs))}"
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.80f),
                    contentColor = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = "$deltaLabel  ·  ${TimeUtils.formatDuration(targetMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

private val VinylSleeveShape = RoundedCornerShape(
    topStart = 12.dp,
    bottomStart = 12.dp,
    topEnd = 5.dp,
    bottomEnd = 5.dp
)

private val SleeveSpineBrush = Brush.horizontalGradient(
    listOf(
        Color.White.copy(alpha = 0.18f),
        Color.Black.copy(alpha = 0.28f),
        Color.Transparent
    )
)

private val SleeveOpeningBrush = Brush.horizontalGradient(
    listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.52f)
    )
)

@Composable
private fun VinylRecordSleeve(
    track: Track?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = VinylSleeveShape,
        shadowElevation = 12.dp,
        color = Color(0xFF1A1A20)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.16f), VinylSleeveShape)
        ) {
            ArtworkImage(
                uri = track?.artworkUri,
                fallbackKey = track?.title.orEmpty(),
                shape = VinylSleeveShape,
                modifier = Modifier.fillMaxSize(),
                contentDescription = null
            )
            // Left spine crease
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .align(Alignment.CenterStart)
                    .background(SleeveSpineBrush)
            )
            // Right sleeve opening depth shadow
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
                    .align(Alignment.CenterEnd)
                    .background(SleeveOpeningBrush)
            )
        }
    }
}

/**
 * Lightweight 60/120fps spinning vinyl disc.
 * Reads [rotationProvider] strictly inside [graphicsLayer] and caches all gradient Brushes via
 * [drawWithCache] so zero objects are allocated and zero recompositions occur while spinning.
 */
@Composable
private fun VinylDiscArtwork(
    track: Track?,
    rotationProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotationProvider()
            },
        contentAlignment = Alignment.Center
    ) {
        // Vinyl matte disc + grooves + specular sheen cached once per size change
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f
                    val bodyBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E1E24), Color(0xFF0E0E12), Color(0xFF09090C)),
                        center = center,
                        radius = maxR
                    )
                    val sheenBrush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.11f),
                            Color.Transparent
                        ),
                        center = center
                    )
                    val grooveStroke = Stroke(width = 1.1f)
                    val grooveColor = Color.White.copy(alpha = 0.055f)
                    val notchColor = Color.White.copy(alpha = 0.22f)
                    val notchOffset = Offset(center.x, center.y - maxR * 0.87f)
                    onDrawBehind {
                        drawCircle(brush = bodyBrush, radius = maxR, center = center)
                        for (factor in VINYL_GROOVE_RADII) {
                            drawCircle(
                                color = grooveColor,
                                radius = maxR * factor,
                                center = center,
                                style = grooveStroke
                            )
                        }
                        drawCircle(brush = sheenBrush, radius = maxR * 0.96f, center = center)
                        drawCircle(color = notchColor, radius = 3.2f, center = notchOffset)
                    }
                }
        )

        // Center circular album label (inherits parent graphicsLayer rotation automatically!)
        Box(
            modifier = Modifier
                .fillMaxSize(0.45f)
                .clip(CircleShape)
                .border(3.dp, Color(0xFF232329), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = track?.artworkUri,
                contentDescription = stringResource(R.string.cd_artwork, track?.title.orEmpty()),
                placeholder = painterResource(id = R.drawable.ic_music_placeholder),
                error = painterResource(id = R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Metallic spindle hole
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1F))
                    .border(2.dp, Color(0xFFD0D0DA), CircleShape)
            )
        }
    }
}
