package com.yasinjahantigh.logarhythm.feature.player

import kotlin.math.abs

internal fun coverPeekTarget(
    dragPx: Float, widthPx: Float, currentIndex: Int, queueSize: Int, released: Boolean
): Int? {
    if (widthPx <= 0 || currentIndex !in 0 until queueSize || dragPx == 0f) return null
    if (released && abs(dragPx) < widthPx * 0.35f) return null
    // The next cover always waits on the right, so dragging it left advances the queue.
    val next = dragPx < 0
    return (currentIndex + if (next) 1 else -1).takeIf { it in 0 until queueSize }
}
