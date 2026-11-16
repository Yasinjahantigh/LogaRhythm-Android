package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistNavigationTest {

    private fun track(id: String, artist: String) = Track(
        id = id,
        title = "Song $id",
        artist = artist,
        album = "Album",
        durationMs = 180_000L,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/$id"
    )

    @Test
    fun splitsMultiArtistTagsIntoIndexedArtists() {
        val config = ArtistSplitConfig()
        val tracks = listOf(
            track("1", "Artist A, Artist B"),
            track("2", "Artist A feat. Artist C"),
            track("3", "Tyler, The Creator & Kali Uchis")
        )

        val index = ArtistIndex.build(tracks, config)

        assertEquals(listOf("Artist A", "Artist B"), config.namesOf("Artist A, Artist B"))
        assertEquals(listOf("Artist A", "Artist C"), config.namesOf("Artist A feat. Artist C"))
        assertEquals(listOf("Tyler, The Creator", "Kali Uchis"), config.namesOf("Tyler, The Creator & Kali Uchis"))

        assertEquals(2, index.tracksOf("Artist A").size)
        assertEquals(1, index.tracksOf("Artist B").size)
        assertEquals(1, index.tracksOf("Artist C").size)
        assertEquals(1, index.tracksOf("Tyler, The Creator").size)
        assertEquals(1, index.tracksOf("Kali Uchis").size)
        assertTrue(index.tracksOf("Artist A, Artist B").isEmpty())
    }
}
