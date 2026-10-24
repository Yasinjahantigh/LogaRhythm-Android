package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistIndexTest {

    private fun track(
        id: String,
        artist: String,
        album: String,
        albumArtist: String = "",
        title: String = id
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 180_000,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://track/$id",
        albumArtist = albumArtist
    )

    @Test
    fun splitsCollaborationsIntoSeparateArtists() {
        val tracks = listOf(
            track("1", "A, B", "Together"),
            track("2", "A", "Solo")
        )
        val index = ArtistIndex.build(tracks, ArtistSplitConfig())
        assertEquals(listOf("A", "B"), index.artists.map { it.name })
        assertEquals(2, index.artistOf("A")?.trackCount)
        assertEquals(1, index.artistOf("B")?.trackCount)
        assertEquals(listOf("1"), index.appearsOn("B").map { it.id })
        assertEquals(emptyList<String>(), index.appearsOn("A").map { it.id })
    }

    @Test
    fun protectedNameStaysOneArtist() {
        val tracks = listOf(track("1", "Tyler, The Creator & Kali Uchis", "Album"))
        val index = ArtistIndex.build(tracks, ArtistSplitConfig())
        assertEquals(listOf("Kali Uchis", "Tyler, The Creator"), index.artists.map { it.name })
        assertNull(index.artistOf("Tyler"))
    }

    @Test
    fun disabledSplittingKeepsRawArtist() {
        val tracks = listOf(track("1", "A, B", "Album"))
        val index = ArtistIndex.build(tracks, ArtistSplitConfig.OFF)
        assertEquals(listOf("A, B"), index.artists.map { it.name })
    }

    @Test
    fun albumsAreGroupedByAlbumArtist() {
        val tracks = listOf(
            track("1", "A, B", "Together", albumArtist = "A & B"),
            track("2", "A, B", "Together", albumArtist = "A & B")
        )
        val index = ArtistIndex.build(tracks, ArtistSplitConfig())
        val album = index.albumsOf("A").single()
        assertEquals("A & B", album.artist)
        assertEquals(2, album.trackCount)
        assertEquals(1, index.artistOf("A")?.albumCount)
    }
}
