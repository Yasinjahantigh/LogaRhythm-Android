package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateFinderTest {
    private fun t(
        id: String,
        title: String,
        artist: String,
        dur: Long,
        album: String = "Al",
        folder: String = "Music",
        filePath: String = "/storage/$folder/$id.mp3"
    ) = Track(id, title, artist, album, dur, null, SourceType.LOCAL, "u$id", folder = folder, filePath = filePath)

    @Test
    fun groupsSameSongWithinTolerance() {
        val groups = DuplicateFinder.find(
            listOf(
                t("1", "Hello!", "Adele", 295_000),
                t("2", "hello", "ADELE", 296_500),
                t("3", "Hello", "Adele", 200_000)
            )
        )
        assertEquals(1, groups.size)
        assertEquals(setOf("1", "2"), groups[0].tracks.map { it.id }.toSet())
    }

    @Test
    fun doesNotGroupDifferentSongsWithIdenticalFileSizeAndDuration() {
        val groups = DuplicateFinder.find(
            tracks = listOf(
                t("1", "Song Alpha", "Artist One", 210_000),
                t("2", "Song Beta", "Artist Two", 210_200)
            ),
            fileSizes = mapOf("1" to 8_400_000L, "2" to 8_400_000L),
            audioFingerprints = mapOf("1" to "hash_aaa", "2" to "hash_bbb")
        )
        assertEquals(0, groups.size)
    }

    @Test
    fun groupsRenamedFilesWithMatchingAudioFingerprint() {
        val groups = DuplicateFinder.find(
            tracks = listOf(
                t("1", "Track 09", "Unknown Artist", 195_000, folder = "Telegram"),
                t("2", "Completely Renamed", "Some Channel", 195_800, folder = "Music")
            ),
            fileSizes = mapOf("1" to 5_120_000L, "2" to 5_420_000L),
            audioFingerprints = mapOf("1" to "same_audio_sha256", "2" to "same_audio_sha256")
        )
        assertEquals(1, groups.size)
        assertEquals(DuplicateMatchReason.IDENTICAL_AUDIO, groups[0].reason)
        assertEquals(setOf("1", "2"), groups[0].tracks.map { it.id }.toSet())
    }

    @Test
    fun doesNotGroupDifferentArtistsWithGenericOrSameTitle() {
        val groups = DuplicateFinder.find(
            listOf(
                t("1", "Intro", "Artist A", 120_000),
                t("2", "Intro", "Artist B", 120_500),
                t("3", "Memories", "Artist C", 200_000),
                t("4", "Memories", "Artist D", 200_400)
            )
        )
        assertEquals(0, groups.size)
    }

    @Test
    fun doesNotGroupOriginalWithRemixOrLiveVersion() {
        val groups = DuplicateFinder.find(
            listOf(
                t("1", "Gole Yakh", "Kourosh Yaghmaei", 240_000),
                t("2", "Gole Yakh (Remix)", "Kourosh Yaghmaei", 241_000),
                t("3", "Gole Yakh (Live)", "Kourosh Yaghmaei", 241_500)
            )
        )
        assertEquals(0, groups.size)
    }

    @Test
    fun ignoresUnknownTitles() {
        assertEquals(0, DuplicateFinder.find(listOf(t("1", "Unknown Title", "A", 1000), t("2", "Unknown Title", "A", 1000))).size)
    }

    @Test
    fun missingTagsFindsPlaceholders() {
        val list = DuplicateFinder.missingTags(listOf(t("1", "X", "Unknown Artist", 1), t("2", "X", "Y", 1)))
        assertEquals(listOf("1"), list.map { it.id })
    }
}
