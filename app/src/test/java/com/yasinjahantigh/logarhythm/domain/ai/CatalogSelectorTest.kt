package com.yasinjahantigh.logarhythm.domain.ai

import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSelectorTest {

    private fun track(
        id: String,
        title: String = "Song $id",
        artist: String = "Artist $id",
        genre: String = "Pop",
        year: Int = 2020,
        album: String = "Album"
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 180_000L,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/$id",
        genre = genre,
        year = year
    )

    @Test
    fun playlistSelection_neverSendsTheWholeLargeLibrary() {
        val library = buildList {
            add(track("target", title = "Saat Divari", artist = "Mohsen Chavoshi"))
            repeat(2_000) { index ->
                add(track("t$index", artist = "Artist ${index % 200}"))
            }
        }

        val selected = CatalogSelector.forPlaylist(library, "mohsen chavoshi calm songs")

        assertEquals(CatalogSelector.MAX_PROMPT_TRACKS, selected.size)
        assertTrue(selected.any { it.id == "target" })
        val perArtist = selected.groupingBy { it.artist }.eachCount()
        assertTrue(perArtist.values.all { it <= 3 })
    }

    @Test
    fun playlistSelection_keepsEveryTrackWhenLibraryIsSmall() {
        val library = (0 until 40).map { track("s$it") }
        assertEquals(library, CatalogSelector.forPlaylist(library, "anything"))
    }

    @Test
    fun mixSelection_excludesBaseAndPrefersTheSameArtistAndGenre() {
        val base = (0 until 15).map {
            track("base$it", artist = "Favorite", genre = "Persian Pop", year = 2018)
        }
        val library = base + buildList {
            repeat(20) { add(track("same$it", artist = "Favorite", genre = "Persian Pop", year = 2019)) }
            repeat(200) { add(track("other$it", artist = "Someone $it", genre = "Metal", year = 1990)) }
        }

        val selected = CatalogSelector.forMix(library, base)

        assertEquals(CatalogSelector.MAX_MIX_CANDIDATES, selected.size)
        assertTrue(selected.none { it.id.startsWith("base") })
        assertTrue(selected.take(3).all { it.artist == "Favorite" })
        assertEquals(3, selected.count { it.artist == "Favorite" })
    }

    @Test
    fun mixSelection_isStableAndReturnsAllRemainingTracksWhenSmall() {
        val base = listOf(track("base", artist = "A"))
        val library = base + (0 until 30).map { track("x$it") }
        val first = CatalogSelector.forMix(library, base)
        val second = CatalogSelector.forMix(library, base)

        assertEquals(30, first.size)
        assertEquals(first, second)
        assertFalse(first.any { it.id == "base" })
    }

    @Test
    fun playlistSelection_usesTasteProfileAndAvoidsDislikedAndRecentTracks() {
        val now = 100L * 86_400_000L
        val library = buildList {
            repeat(5) { i -> add(track("fav$i", artist = "LovedArtist_$i", genre = "پاپ")) }
            repeat(5) { i -> add(track("dislike$i", artist = "HatedArtist_$i", genre = "Metal")) }
            repeat(5) { i -> add(track("recent$i", artist = "RecentArtist_$i", genre = "Pop")) }
            repeat(200) { i -> add(track("pool$i", artist = "PoolArtist_$i", genre = "Rock")) }
        }
        val events = buildList {
            repeat(5) { i ->
                add(PlayEvent(trackId = "fav$i", timestamp = now - 5L * 86_400_000L, listenedMs = 175_000L))
            }
            repeat(5) { i ->
                add(PlayEvent(trackId = "dislike$i", timestamp = now - 10_000L, listenedMs = 3_000L, skipped = true))
                add(PlayEvent(trackId = "dislike$i", timestamp = now - 5_000L, listenedMs = 3_000L, skipped = true))
            }
        }
        val profile = TasteProfile.build(events, favoriteTrackIds = setOf("fav0", "fav1"), nowMs = now, library = library)
        val recent = (0 until 5).map { "recent$it" }.toSet()

        val picked = CatalogSelector.forPlaylist(
            library = library,
            prompt = "something good for evening",
            limit = 40,
            profile = profile,
            recentTrackIds = recent,
            seed = 42L
        )
        val pickedIds = picked.map { it.id }.toSet()
        assertEquals(40, picked.size)
        assertTrue(pickedIds.contains("fav0"))
        assertTrue(pickedIds.none { it.startsWith("dislike") })
        assertTrue(pickedIds.none { it.startsWith("recent") })
    }
}
