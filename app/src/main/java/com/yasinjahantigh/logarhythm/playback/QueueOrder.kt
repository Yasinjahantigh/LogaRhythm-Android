package com.yasinjahantigh.logarhythm.playback

/** Keeps the playing item at [index] while the rest of the queue is reordered. */
internal fun <T> pinQueueItem(order: List<T>, current: T, index: Int): List<T> =
    order.toMutableList().apply {
        remove(current)
        add(index.coerceIn(0, size), current)
    }
