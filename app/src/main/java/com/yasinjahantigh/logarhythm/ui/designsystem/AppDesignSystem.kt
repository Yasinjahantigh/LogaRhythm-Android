package com.yasinjahantigh.logarhythm.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

object AppMotion {
    const val durationFast = 150
    const val durationNormal = 250
    const val durationSlow = 350
    val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val fastOutSlowEasing = FastOutSlowInEasing
}

/** Height of the mini player plus navigation bar, used as bottom content padding. */
val LocalPlaybackBottomPadding = compositionLocalOf { 0.dp }
