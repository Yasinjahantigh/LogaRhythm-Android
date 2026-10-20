package com.yasinjahantigh.logarhythm.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing, sizing, shape and motion tokens shared across the UI. */
object AppSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val normal: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
}

object AppDimensions {
    val minTouchTarget: Dp = 48.dp
    val artworkRow: Dp = 52.dp
    val artworkMiniPlayer: Dp = 40.dp
    val artworkLarge: Dp = 80.dp
    val miniPlayerHeight: Dp = 72.dp
    val bottomBarHeight: Dp = 80.dp
    val playButtonMini: Dp = 36.dp
    val navButtonMini: Dp = 32.dp
    val iconXs: Dp = 16.dp
    val iconSm: Dp = 20.dp
    val iconMd: Dp = 24.dp
    val iconLg: Dp = 28.dp
    val iconHero: Dp = 48.dp
}

object AppShapes {
    val roundedXs = RoundedCornerShape(4.dp)
    val roundedSm = RoundedCornerShape(8.dp)
    val roundedMd = RoundedCornerShape(12.dp)
    val roundedLg = RoundedCornerShape(16.dp)
    val roundedXl = RoundedCornerShape(24.dp)
    val roundedFull = CircleShape
}

object AppMotion {
    const val durationFast = 150
    const val durationNormal = 250
    const val durationSlow = 350
    val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val fastOutSlowEasing = FastOutSlowInEasing
}

/** Height of the mini player plus navigation bar, used as bottom content padding. */
val LocalPlaybackBottomPadding = compositionLocalOf { 0.dp }
