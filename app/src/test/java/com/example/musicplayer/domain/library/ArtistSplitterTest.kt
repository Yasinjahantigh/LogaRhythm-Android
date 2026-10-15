package com.example.musicplayer.domain.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSplitterTest {
    @Test fun splitsFeaturingAndSemicolons() {
        assertEquals(listOf("A", "B", "C"), ArtistSplitter.split("A ft. B; C"))
        assertEquals(listOf("The Weeknd", "Daft Punk"), ArtistSplitter.split("The Weeknd feat. Daft Punk"))
    }
    @Test fun keepsProtectedNamesWhole() {
        assertEquals(listOf("Simon & Garfunkel"), ArtistSplitter.split("Simon & Garfunkel"))
    }
    @Test fun doesNotCutInsideWords() {
        assertEquals(listOf("AT&T"), ArtistSplitter.split("AT&T"))
    }
    @Test fun dedupesCaseInsensitively() {
        assertEquals(listOf("Adele"), ArtistSplitter.split("Adele & adele"))
    }
    @Test fun customSeparators() {
        assertEquals(listOf("John", "Paul"), ArtistSplitter.split("John, Paul", listOf(", ")))
        assertEquals(ArtistSplitter.DEFAULT_SEPARATORS, ArtistSplitter.parseSeparators(null))
    }
    @Test fun emptyInput() { assertEquals(emptyList<String>(), ArtistSplitter.split("  ")) }
}
