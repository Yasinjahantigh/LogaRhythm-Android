package com.yasinjahantigh.logarhythm.feature.player

import kotlin.math.abs

internal fun coverPeekTarget(
    dragPx: Float, widthPx: Float, currentIndex: Int, queueSize: Int,
    swipeRightForNext: Boolean, isRtl: Boolean, released: Boolean
): Int? {
    if (widthPx <= 0 || currentIndex !in 0 until queueSize || dragPx == 0f) return null
    if (released && abs(dragPx) < widthPx * 0.35f) return null
    val logicalRight = if (isRtl) dragPx < 0 else dragPx > 0
    val next = logicalRight == swipeRightForNext
    return (currentIndex + if (next) 1 else -1).takeIf { it in 0 until queueSize }
}
