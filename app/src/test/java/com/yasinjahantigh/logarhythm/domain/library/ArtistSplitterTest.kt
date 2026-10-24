package com.yasinjahantigh.logarhythm.domain.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSplitterTest {
    @Test fun splitsFeaturingAndSemicolons() {
        assertEquals(listOf("A", "B", "C"), ArtistSplitter.split("A ft. B; C"))
        assertEquals(listOf("The Weeknd", "Daft Punk"), ArtistSplitter.split("The Weeknd feat. Daft Punk"))
    }
    @Test fun splitsCommasAndPersianCommas() {
        assertEquals(listOf("A", "B"), ArtistSplitter.split("A, B"))
        assertEquals(listOf("A", "B"), ArtistSplitter.split("A، B"))
    }
    @Test fun splitsCrossAndWithCredits() {
        assertEquals(listOf("A", "B"), ArtistSplitter.split("A x B"))
        assertEquals(listOf("A", "B"), ArtistSplitter.split("A × B"))
        assertEquals(listOf("A", "B"), ArtistSplitter.split("A with B"))
    }
    @Test fun keepsProtectedNamesWhole() {
        assertEquals(listOf("Simon & Garfunkel"), ArtistSplitter.split("Simon & Garfunkel"))
        assertEquals(listOf("Tyler, The Creator"), ArtistSplitter.split("Tyler, The Creator"))
        assertEquals(listOf("Earth, Wind & Fire"), ArtistSplitter.split("Earth, Wind & Fire"))
    }
    @Test fun protectsNamesInsideCollaborations() {
        assertEquals(
            listOf("Tyler, The Creator", "Kali Uchis"),
            ArtistSplitter.split("Tyler, The Creator & Kali Uchis")
        )
        assertEquals(
            listOf("Earth, Wind & Fire", "The Emotions"),
            ArtistSplitter.split("Earth, Wind & Fire feat. The Emotions")
        )
    }
    @Test fun userExceptionsAreProtected() {
        assertEquals(
            listOf("Chance, The Rapper"),
            ArtistSplitter.split("Chance, The Rapper", neverSplit = setOf("Chance, The Rapper"))
        )
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
