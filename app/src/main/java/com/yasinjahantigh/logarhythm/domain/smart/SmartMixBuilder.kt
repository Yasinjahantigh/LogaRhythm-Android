package com.yasinjahantigh.logarhythm.domain.smart

import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import java.util.concurrent.TimeUnit

/**
 * Turns the library into the built-in mixes. Pure and side-effect free so the rules stay easy to
 * test and the UI never has to re-implement them.
 */
object SmartMixBuilder {

    const val MAX_TRACKS = 50

    /** Five minutes is the "long song" line: pop songs sit well below it. */
    const val LONG_SONG_MS = 5 * 60_000L

    private val FORGOTTEN_AFTER_MS = TimeUnit.DAYS.toMillis(60)

    fun build(
        type: SmartMixType,
        tracks: List<Track>,
        playStats: Map<String, TrackPlayStat>,
        favoriteIds: Set<String>,
        downloadedUris: Set<String>,
        lyricsTrackIds: Set<String>,
        now: Long = System.currentTimeMillis()
    ): List<Track> = when (type) {
        SmartMixType.RECENTLY_ADDED -> tracks
            .sortedWith(
                compareByDescending<Track> { if (it.dateAdded > 0L) it.dateAdded else it.dateModified }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .take(MAX_TRACKS)

        SmartMixType.RECENTLY_PLAYED -> tracks
            .filter { (playStats[it.id]?.lastPlayedAt ?: 0L) > 0L }
            .sortedWith(
                compareByDescending<Track> { playStats[it.id]?.lastPlayedAt ?: 0L }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .take(MAX_TRACKS)

        SmartMixType.MOST_PLAYED -> tracks
            .filter { (playStats[it.id]?.playCount ?: 0) > 0 }
            .sortedWith(
                compareByDescending<Track> { playStats[it.id]?.playCount ?: 0 }
                    .thenByDescending { playStats[it.id]?.lastPlayedAt ?: 0L }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .take(MAX_TRACKS)

        SmartMixType.FORGOTTEN_FAVORITES -> {
            val cutoff = now - FORGOTTEN_AFTER_MS
            tracks
                .filter { it.id in favoriteIds }
                .filter { (playStats[it.id]?.lastPlayedAt ?: 0L) < cutoff }
                .sortedWith(
                    compareBy<Track> { playStats[it.id]?.lastPlayedAt ?: 0L }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                )
                .take(MAX_TRACKS)
        }

        SmartMixType.NO_LYRICS -> tracks
            .filter { it.id !in lyricsTrackIds }
            .sortedBy { it.title.lowercase() }
            .take(MAX_TRACKS)

        SmartMixType.LONG_SONGS -> tracks
            .filter { it.durationMs >= LONG_SONG_MS }
            .sortedByDescending { it.durationMs }
            .take(MAX_TRACKS)

        SmartMixType.DOWNLOADED -> tracks
            .filter { it.uri in downloadedUris }
            .sortedWith(
                compareByDescending<Track> { if (it.dateAdded > 0L) it.dateAdded else it.dateModified }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            .take(MAX_TRACKS)
    }

    fun artworkOf(tracks: List<Track>, limit: Int = 4): List<String> =
        tracks.asSequence()
            .mapNotNull { it.artworkUri?.takeIf { uri -> uri.isNotBlank() } }
            .distinct()
            .take(limit)
            .toList()
}
