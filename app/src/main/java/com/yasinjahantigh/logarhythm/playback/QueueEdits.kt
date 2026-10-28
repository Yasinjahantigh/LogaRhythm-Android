package com.yasinjahantigh.logarhythm.playback

internal fun <T> originalOccurrence(order: List<T>, visible: List<T>, index: Int): Int {
    val item = visible[index]
    val occurrence = visible.take(index).count { it == item }
    return order.indices.filter { order[it] == item }.getOrElse(occurrence) { -1 }
}

internal fun <T> insertOriginal(order: List<T>, visible: List<T>, index: Int, item: T): List<T> {
    if (visible.isEmpty()) return order + item
    val anchor = if (index == 0) 0 else index - 1
    val original = originalOccurrence(order, visible, anchor)
    val target = if (original < 0) order.size else original + if (index == 0) 0 else 1
    return order.toMutableList().apply { add(target, item) }
}
