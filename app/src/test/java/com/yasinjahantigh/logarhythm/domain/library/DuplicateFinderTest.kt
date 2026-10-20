package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateFinderTest {
    private fun t(id: String, title: String, artist: String, dur: Long, album: String = "Al") =
        Track(id, title, artist, album, dur, null, SourceType.LOCAL, "u$id")

    @Test fun groupsSameSongWithinTolerance() {
        val groups = DuplicateFinder.find(listOf(
            t("1", "Hello!", "Adele", 295_000), t("2", "hello", "ADELE", 296_500), t("3", "Hello", "Adele", 200_000)
        ))
        assertEquals(1, groups.size)
        assertEquals(setOf("1", "2"), groups[0].tracks.map { it.id }.toSet())
    }
    @Test fun ignoresUnknownTitles() {
        assertEquals(0, DuplicateFinder.find(listOf(t("1", "Unknown Title", "A", 1000), t("2", "Unknown Title", "A", 1000))).size)
    }
    @Test fun missingTagsFindsPlaceholders() {
        val list = DuplicateFinder.missingTags(listOf(t("1", "X", "Unknown Artist", 1), t("2", "X", "Y", 1)))
        assertEquals(listOf("1"), list.map { it.id })
    }
}
