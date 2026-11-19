package com.yasinjahantigh.logarhythm.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Applies a lightweight tactile spring scale on press, reading the animated state strictly
 * inside the graphicsLayer draw/layer phase so parent layouts never recompose.
 */
fun Modifier.bounceOnPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 460f),
        label = "press_bounce"
    )
    graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
    }
}

/**
 * Lightweight single-driver staggered entrance (fade + gentle upward glide) using one
 * Animatable read strictly inside graphicsLayer so screens stay smooth on all devices.
 */
fun Modifier.staggeredEntrance(
    index: Int = 0,
    delayStepMs: Int = 28,
    slideDistancePx: Float = 18f
): Modifier = composed {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val wait = (index.coerceIn(0, 4) * delayStepMs).toLong()
        if (wait > 0L) delay(wait)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
    }

    graphicsLayer {
        val t = progress.value
        alpha = t
        translationY = slideDistancePx * (1f - t)
        val s = 0.985f + 0.015f * t
        scaleX = s
        scaleY = s
    }
}
