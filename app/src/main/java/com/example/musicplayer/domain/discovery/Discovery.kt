package com.example.musicplayer.domain.discovery

import com.example.musicplayer.domain.ai.MusicAiEngine
import com.example.musicplayer.domain.model.PlayEvent
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.taste.TasteProfile

/**
 * Home-screen discovery built on the listener's own history. Everything here is deterministic
 * given its inputs, so it is cheap to recompute and straightforward to test.
 */
object Discovery {

    enum class Daypart { MORNING, AFTERNOON, EVENING, NIGHT }

    /** A queue shaped by when the listener is listening. */
    data class RightNow(
        val daypart: Daypart,
        val tracks: List<Track>,
        /** How many leading tracks come from the listener's real habits rather than a mood guess. */
        val habitualCount: Int
    )

    data class BecauseYouPlayed(val seed: Track, val tracks: List<Track>)

    fun daypartOf(hour: Int): Daypart = when (((hour % 24) + 24) % 24) {
        in 5..11 -> Daypart.MORNING
        in 12..17 -> Daypart.AFTERNOON
        in 18..22 -> Daypart.EVENING
        else -> Daypart.NIGHT
    }

    fun moodFor(daypart: Daypart): MusicAiEngine.AiMood = when (daypart) {
        Daypart.MORNING -> MusicAiEngine.AiMood.ACOUSTIC_MORNING
        Daypart.AFTERNOON -> MusicAiEngine.AiMood.DEEP_FOCUS
        Daypart.EVENING -> MusicAiEngine.AiMood.ENERGY_BOOST
        Daypart.NIGHT -> MusicAiEngine.AiMood.LATE_NIGHT_CHILL
    }

    /**
     * Music for this hour: first what the listener really plays around now, then a mood-matched
     * top-up so a new listener still gets something sensible.
     */
    fun rightNow(
        library: List<Track>,
        profile: TasteProfile,
        hour: Int,
        limit: Int = 15
    ): RightNow {
        val daypart = daypartOf(hour)
        if (limit <= 0 || library.isEmpty()) return RightNow(daypart, emptyList(), 0)

        val habitual = library.distinctBy { it.id }
            .asSequence()
            .filter { !profile.isDisliked(it.id) && profile.affinity(it.id) >= 0.0 }
            .map { it to profile.hourWeight(it.id, hour) }
            .filter { (_, weight) -> weight >= MIN_HABIT_WEIGHT }
            .sortedByDescending { (track, weight) -> weight * (1.0 + profile.affinity(track.id).coerceIn(0.0, 4.0) * 0.1) }
            .map { it.first }
            .toList()
        val habitualCapped = capPerArtist(habitual, perArtist = 2).take(limit)

        val taken = habitualCapped.mapTo(HashSet()) { it.id }
        val topUp = if (habitualCapped.size >= limit) emptyList() else {
            MusicAiEngine.generateMoodPlaylist(moodFor(daypart), library, count = limit * 2, profile = profile)
                .filter { it.id !in taken && !profile.isDisliked(it.id) }
                .take(limit - habitualCapped.size)
        }
        return RightNow(daypart, habitualCapped + topUp, habitualCapped.size)
    }

    /** Tracks that have been on heavy rotation lately, most-played first. */
    fun onRepeat(
        library: List<Track>,
        events: List<PlayEvent>,
        nowMs: Long = System.currentTimeMillis(),
        windowDays: Int = 21,
        minPlays: Int = 3,
        limit: Int = 12
    ): List<Track> {
        if (limit <= 0) return emptyList()
        val since = nowMs - windowDays * TasteProfile.DAY_MS
        val counts = events.asSequence()
            .filter { !it.skipped && it.timestamp >= since }
            .groupingBy { it.trackId }
            .eachCount()
        val byId = library.associateBy { it.id }
        return counts.entries
            .filter { it.value >= minPlays && byId.containsKey(it.key) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .mapNotNull { byId[it.key] }
            .take(limit)
    }

    /** A radio seeded from the last song the listener actually enjoyed. */
    fun becauseYouPlayed(
        library: List<Track>,
        events: List<PlayEvent>,
        profile: TasteProfile,
        limit: Int = 12
    ): BecauseYouPlayed? {
        if (limit <= 0 || library.size < 3) return null
        val byId = library.associateBy { it.id }
        val seed = events.asSequence()
            .filter { !it.skipped }
            .sortedByDescending { it.timestamp }
            .mapNotNull { byId[it.trackId] }
            .firstOrNull { profile.affinity(it.id) > 0.0 }
            ?: return null
        val picks = MusicAiEngine.recommendNextTracks(seed, library, limit = limit, profile = profile)
        return if (picks.size >= MIN_SHELF_SIZE) BecauseYouPlayed(seed, picks) else null
    }

    private fun capPerArtist(tracks: List<Track>, perArtist: Int): List<Track> {
        val seen = HashMap<String, Int>()
        return tracks.filter { track ->
            val key = track.artist.trim().lowercase().ifBlank { "track:${track.id}" }
            val n = (seen[key] ?: 0) + 1
            seen[key] = n
            n <= perArtist
        }
    }

    private const val MIN_HABIT_WEIGHT = 0.4
    private const val MIN_SHELF_SIZE = 3
}
