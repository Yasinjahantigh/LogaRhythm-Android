package com.example.musicplayer.feature.home

import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTrackCollectionsTest {
    private fun track(id: String, title: String, dateAdded: Long) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://music/$id",
        dateAdded = dateAdded
    )

    @Test
    fun recentlyAddedSortsNewestFirstAndUsesTitleForTies() {
        val tracks = listOf(
            track("1", "Old", 100),
            track("2", "Zulu", 300),
            track("3", "Alpha", 300),
            track("4", "Middle", 200)
        )

        val result = HomeTrackCollections.recentlyAdded(tracks)

        assertEquals(listOf("Alpha", "Zulu", "Middle", "Old"), result.map { it.title })
    }

    @Test
    fun recentlyAddedHonorsLimitAndRejectsNonPositiveLimits() {
        val tracks = (1L..3L).map { track("$it", "Track $it", it) }

        assertEquals(2, HomeTrackCollections.recentlyAdded(tracks, limit = 2).size)
        assertTrue(HomeTrackCollections.recentlyAdded(tracks, limit = 0).isEmpty())
        assertTrue(HomeTrackCollections.recentlyAdded(tracks, limit = -5).isEmpty())
    }
}
