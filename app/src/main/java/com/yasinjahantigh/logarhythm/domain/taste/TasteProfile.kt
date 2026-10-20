package com.yasinjahantigh.logarhythm.domain.taste

import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import java.util.TimeZone
import kotlin.math.pow

/**
 * What the listener's own behavior says about their taste, computed entirely on-device from
 * play history. Unlike title/genre keyword guessing, this learns from three real signals:
 *
 *  - affinity: recency-weighted plays (positive) versus skips (negative), plus favorites;
 *  - co-listening: tracks played back-to-back in the same sitting tend to belong together;
 *  - time of day: which tracks the listener actually reaches for at a given hour.
 */
class TasteProfile private constructor(
    private val affinities: Map<String, Double>,
    private val coListen: Map<String, Map<String, Double>>,
    private val hourly: Map<String, DoubleArray>,
    private val playCounts: Map<String, Int>,
    private val skipCounts: Map<String, Int>,
    val hasHistory: Boolean
) {
    /** Positive when liked and recently played, negative when habitually skipped. */
    fun affinity(trackId: String): Double = affinities[trackId] ?: 0.0

    /** 0..1 — how strongly two tracks have been heard together in the same sitting. */
    fun coListenStrength(a: String, b: String): Double {
        val raw = coListen[a]?.get(b) ?: return 0.0
        return raw / (raw + CO_LISTEN_SATURATION)
    }

    /** Recency-weighted amount the listener has played [trackId] around [hour] (0..23). */
    fun hourWeight(trackId: String, hour: Int): Double =
        hourly[trackId]?.getOrNull(((hour % 24) + 24) % 24) ?: 0.0

    fun playCount(trackId: String): Int = playCounts[trackId] ?: 0

    fun skipCount(trackId: String): Int = skipCounts[trackId] ?: 0

    /** Passed over repeatedly and rarely finished: safe to keep out of suggestions. */
    fun isDisliked(trackId: String): Boolean {
        val skips = skipCount(trackId)
        return skips >= 2 && skips > playCount(trackId) * 2
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val HOUR_MS = 60L * 60 * 1000
        private const val HALF_LIFE_DAYS = 45.0
        private const val SITTING_GAP_MS = 20L * 60 * 1000
        private const val CO_LISTEN_SATURATION = 1.5
        private const val SKIP_WEIGHT = 1.5
        private const val FAVORITE_BONUS = 2.0

        val EMPTY = TasteProfile(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), false)

        fun localHour(timestampMs: Long, utcOffsetMs: Int): Int =
            ((((timestampMs + utcOffsetMs) / HOUR_MS) % 24 + 24) % 24).toInt()

        fun build(
            events: List<PlayEvent>,
            favoriteTrackIds: Set<String> = emptySet(),
            nowMs: Long = System.currentTimeMillis(),
            utcOffsetMs: Int = TimeZone.getDefault().getOffset(nowMs)
        ): TasteProfile {
            if (events.isEmpty() && favoriteTrackIds.isEmpty()) return EMPTY

            val affinities = HashMap<String, Double>()
            val plays = HashMap<String, Int>()
            val skips = HashMap<String, Int>()
            val hourly = HashMap<String, DoubleArray>()

            fun recencyWeight(timestamp: Long): Double {
                val ageDays = ((nowMs - timestamp).coerceAtLeast(0L)).toDouble() / DAY_MS
                return 0.5.pow(ageDays / HALF_LIFE_DAYS)
            }

            for (event in events) {
                val w = recencyWeight(event.timestamp)
                if (event.skipped) {
                    skips[event.trackId] = (skips[event.trackId] ?: 0) + 1
                    affinities[event.trackId] = (affinities[event.trackId] ?: 0.0) - SKIP_WEIGHT * w
                } else {
                    plays[event.trackId] = (plays[event.trackId] ?: 0) + 1
                    affinities[event.trackId] = (affinities[event.trackId] ?: 0.0) + w
                    val buckets = hourly.getOrPut(event.trackId) { DoubleArray(24) }
                    val h = localHour(event.timestamp, utcOffsetMs)
                    buckets[h] += w
                    buckets[(h + 1) % 24] += w * 0.5
                    buckets[(h + 23) % 24] += w * 0.5
                }
            }
            for (id in favoriteTrackIds) affinities[id] = (affinities[id] ?: 0.0) + FAVORITE_BONUS

            // Co-listening: consecutive completed plays within one sitting.
            val coListen = HashMap<String, HashMap<String, Double>>()
            val ordered = events.filter { !it.skipped }.sortedBy { it.timestamp }
            for (i in 1 until ordered.size) {
                val prev = ordered[i - 1]
                val next = ordered[i]
                if (prev.trackId == next.trackId) continue
                if (next.timestamp - prev.timestamp > SITTING_GAP_MS) continue
                val w = recencyWeight(next.timestamp)
                coListen.getOrPut(prev.trackId) { HashMap() }.merge(next.trackId, w, Double::plus)
                coListen.getOrPut(next.trackId) { HashMap() }.merge(prev.trackId, w, Double::plus)
            }

            return TasteProfile(affinities, coListen, hourly, plays, skips, hasHistory = events.isNotEmpty())
        }
    }
}
