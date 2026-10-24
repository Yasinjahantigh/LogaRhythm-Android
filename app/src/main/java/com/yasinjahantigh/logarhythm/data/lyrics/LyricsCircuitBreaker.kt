package com.yasinjahantigh.logarhythm.data.lyrics

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops a blocked/slow provider from costing several seconds on every song. Three failures open
 * the circuit for ten minutes; a success closes it again. Purely in-memory: a restart re-probes.
 */
@Singleton
class LyricsCircuitBreaker @Inject constructor() {

    private data class State(val failures: Int, val openUntil: Long)

    private val states = ConcurrentHashMap<String, State>()

    fun isOpen(providerId: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = states[providerId] ?: return false
        if (state.openUntil <= 0L) return false
        if (state.openUntil > now) return true
        // Half-open: allow one probe, keeping the failure count so a quick failure reopens it.
        states[providerId] = state.copy(openUntil = 0L)
        return false
    }

    fun recordSuccess(providerId: String) {
        states.remove(providerId)
    }

    fun recordFailure(providerId: String, now: Long = System.currentTimeMillis()) {
        val failures = (states[providerId]?.failures ?: 0) + 1
        val openUntil = if (failures >= FAILURE_THRESHOLD) now + OPEN_MS else 0L
        states[providerId] = State(failures, openUntil)
    }

    fun reset(providerId: String? = null) {
        if (providerId == null) states.clear() else states.remove(providerId)
    }

    companion object {
        const val FAILURE_THRESHOLD = 3
        const val OPEN_MS = 10 * 60_000L
    }
}
