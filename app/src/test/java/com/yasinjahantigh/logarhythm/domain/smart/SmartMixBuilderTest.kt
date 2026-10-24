package com.yasinjahantigh.logarhythm.domain.smart

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SmartMixBuilderTest {

    private fun track(
        id: String,
        title: String = id,
        artist: String = "Artist",
        durationMs: Long = 3 * 60_000L,
        dateAdded: Long = 0L,
        artworkUri: String? = null
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        artworkUri = artworkUri,
        sourceType = SourceType.LOCAL,
        uri = "content://media/$id",
        dateAdded = dateAdded
    )

    @Test
    fun recently_added_orders_by_date_and_caps() {
        val tracks = (1..60).map { track("id$it", dateAdded = it.toLong()) }
        val mix = SmartMixBuilder.build(
            SmartMixType.RECENTLY_ADDED, tracks, emptyMap(), emptySet(), emptySet(), emptySet()
        )
        assertEquals(SmartMixBuilder.MAX_TRACKS, mix.size)
        assertEquals("id60", mix.first().id)
    }

    @Test
    fun most_played_skips_unplayed_and_sorts_by_count() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val stats = mapOf(
            "a" to TrackPlayStat("a", 3, 10L),
            "b" to TrackPlayStat("b", 9, 5L),
            "c" to TrackPlayStat("c", 0, 0L)
        )
        val mix = SmartMixBuilder.build(
            SmartMixType.MOST_PLAYED, tracks, stats, emptySet(), emptySet(), emptySet()
        )
        assertEquals(listOf("b", "a"), mix.map { it.id })
    }

    @Test
    fun forgotten_favorites_ignores_recent_plays_and_non_favorites() {
        val now = TimeUnit.DAYS.toMillis(100)
        val tracks = listOf(track("old"), track("never"), track("recent"), track("not_favorite"))
        val stats = mapOf(
            "old" to TrackPlayStat("old", 20, now - TimeUnit.DAYS.toMillis(90)),
            "recent" to TrackPlayStat("recent", 3, now - TimeUnit.DAYS.toMillis(2)),
            "not_favorite" to TrackPlayStat("not_favorite", 1, 0L)
        )
        val mix = SmartMixBuilder.build(
            SmartMixType.FORGOTTEN_FAVORITES,
            tracks,
            stats,
            favoriteIds = setOf("old", "never", "recent"),
            downloadedUris = emptySet(),
            lyricsTrackIds = emptySet(),
            now = now
        )
        assertEquals(listOf("never", "old"), mix.map { it.id })
    }

    @Test
    fun no_lyrics_excludes_songs_that_have_lyrics() {
        val tracks = listOf(track("a", title = "Zed"), track("b", title = "Alpha"))
        val mix = SmartMixBuilder.build(
            SmartMixType.NO_LYRICS,
            tracks,
            emptyMap(),
            emptySet(),
            emptySet(),
            lyricsTrackIds = setOf("a")
        )
        assertEquals(listOf("b"), mix.map { it.id })
    }

    @Test
    fun long_songs_use_the_five_minute_line() {
        val tracks = listOf(
            track("short", durationMs = 4 * 60_000L),
            track("exact", durationMs = 5 * 60_000L),
            track("long", durationMs = 9 * 60_000L)
        )
        val mix = SmartMixBuilder.build(
            SmartMixType.LONG_SONGS, tracks, emptyMap(), emptySet(), emptySet(), emptySet()
        )
        assertEquals(listOf("long", "exact"), mix.map { it.id })
    }

    @Test
    fun downloaded_matches_saved_uris() {
        val tracks = listOf(track("a"), track("b"))
        val mix = SmartMixBuilder.build(
            SmartMixType.DOWNLOADED,
            tracks,
            emptyMap(),
            emptySet(),
            downloadedUris = setOf("content://media/b"),
            lyricsTrackIds = emptySet()
        )
        assertEquals(listOf("b"), mix.map { it.id })
    }

    @Test
    fun artwork_is_deduplicated_and_limited() {
        val tracks = (1..5).map { track("$it", artworkUri = "art://${it % 2}") }
        val art = SmartMixBuilder.artworkOf(tracks)
        assertEquals(listOf("art://1", "art://0"), art)
        assertTrue(art.size <= 4)
    }
}
