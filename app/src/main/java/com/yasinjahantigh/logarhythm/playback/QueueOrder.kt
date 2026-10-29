package com.yasinjahantigh.logarhythm.playback

/**
 * Keeps the played prefix of [visible] intact and rebuilds only the upcoming part from
 * [original], either shuffled or in canonical order.
 */
internal fun <T> shuffleUpcoming(
    visible: List<T>,
    original: List<T>,
    current: T,
    playedCount: Int,
    shuffle: Boolean
): List<T> {
    val played = visible.take(playedCount.coerceIn(0, visible.size))
    val remaining = original.toMutableList().apply {
        played.forEach { remove(it) }
        remove(current)
    }
    return played + current + if (shuffle) remaining.shuffled() else remaining
}
