package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.lyrics.LyricsInstrumentalDetector
import com.yasinjahantigh.logarhythm.domain.model.LyricLine
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.smart.ListeningTimeSlot
import com.yasinjahantigh.logarhythm.domain.smart.UserTasteEngine
import com.yasinjahantigh.logarhythm.domain.smart.UserTasteModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewFeaturesUnitTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        genre: String = "Pop",
        durationMs: Long = 200_000L,
        folder: String = "Music",
        filePath: String = "/storage/$folder/$id.mp3"
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/$id",
        folder = folder,
        genre = genre,
        filePath = filePath
    )

    @Test
    fun duplicateFinder_detectsMultiSourceAndFileSizeAndPicksHighestQuality() {
        val t1 = track(
            id = "local:1",
            title = "Gole Yakh [320]",
            artist = "Kourosh Yaghmaei",
            durationMs = 240_000L,
            folder = "MusicMan",
            filePath = "/storage/MusicMan/gole_yakh_320.mp3"
        )
        val t2 = track(
            id = "local:2",
            title = "Gole Yakh (Official Audio) - RadioJavan",
            artist = "Kourosh Yaghmaei",
            durationMs = 242_500L,
            folder = "Downloads",
            filePath = "/storage/Downloads/gole_yakh_128.mp3"
        )
        val t3 = track(
            id = "local:3",
            title = "Track 09",
            artist = "Unknown Artist",
            durationMs = 195_000L,
            folder = "Telegram"
        )
        val t4 = track(
            id = "local:4",
            title = "Completely Renamed Copy",
            artist = "Some Channel",
            durationMs = 195_800L,
            folder = "Music"
        )

        val sizes = mapOf(
            "local:1" to 9_600_000L, // ~320 kbps
            "local:2" to 3_800_000L, // ~128 kbps
            "local:3" to 5_120_000L,
            "local:4" to 5_124_000L  // within 16KB + same duration => file-size duplicate
        )

        val groups = DuplicateFinder.find(listOf(t1, t2, t3, t4), fileSizes = sizes)
        assertEquals(2, groups.size)

        val goleGroup = groups.first { g -> g.tracks.any { it.id == "local:1" } }
        assertEquals(2, goleGroup.tracks.size)
        assertEquals("local:1", goleGroup.recommendedKeepId)
        assertEquals(DuplicateMatchReason.MULTI_SOURCE, goleGroup.reason)

        val sizeGroup = groups.first { g -> g.tracks.any { it.id == "local:3" } }
        assertEquals(2, sizeGroup.tracks.size)
        assertEquals(DuplicateMatchReason.SAME_FILE_SIZE, sizeGroup.reason)

        val extras = DuplicateFinder.extraDuplicatesToRemove(groups)
        assertEquals(2, extras.size)
        assertTrue(extras.any { it.id == "local:2" })
    }

    @Test
    fun lyricsInstrumentalDetector_showsOnlyDuringExactGapAndNeverBeforeOrAfter() {
        val lines = listOf(
            LyricLine(timestampMs = 6_000L, text = "First line"),
            LyricLine(timestampMs = 10_000L, text = "Before break"),
            LyricLine(timestampMs = 22_000L, text = "After break"),
            LyricLine(timestampMs = 25_000L, text = "Quick next line")
        )

        // During intro (before line 0 at 6s): active
        val introGap = LyricsInstrumentalDetector.findActiveGap(lines, activeIndex = -1, effectivePosMs = 3_000L)
        assertNotNull(introGap)
        assertEquals(-1, introGap!!.slotIndex)

        // While singing line 1 at 10.5s: NOT in break yet
        assertNull(LyricsInstrumentalDetector.findActiveGap(lines, activeIndex = 1, effectivePosMs = 10_500L))

        // Inside instrumental break between 10s and 22s (e.g., at 16s): active on slot 1
        val midGap = LyricsInstrumentalDetector.findActiveGap(lines, activeIndex = 1, effectivePosMs = 16_000L)
        assertNotNull(midGap)
        assertEquals(1, midGap!!.slotIndex)

        // Passed the break (at 22.5s on line 2): ZERO trace
        assertNull(LyricsInstrumentalDetector.findActiveGap(lines, activeIndex = 2, effectivePosMs = 22_500L))

        // Short gap between line 2 (22s) and line 3 (25s): never triggers
        assertNull(LyricsInstrumentalDetector.findActiveGap(lines, activeIndex = 2, effectivePosMs = 24_000L))
    }

    @Test
    fun userTasteEngine_learnsRealCompletionsSkipsTimeOfDayAndHiddenGems() {
        val hourMs = 3_600_000L
        val dayMs = 24L * hourMs
        // Fixed reference: 22:00 UTC (NIGHT slot with utcOffsetMs = 0)
        val nowMs = 100L * dayMs + 22L * hourMs

        val lovedTrack = track("t1", "Shab", artist = "Shadmehr", genre = "Persian Pop", durationMs = 200_000L)
        val unplayedSameArtist = track("t2", "Taghdir", artist = "Shadmehr", genre = "Persian Pop", durationMs = 210_000L)
        val skippedTrack = track("t3", "Annoying", artist = "SkipArtist", genre = "Noise", durationMs = 200_000L)
        val morningTrack = track("t4", "Sobh", artist = "MorningBand", genre = "Acoustic", durationMs = 200_000L)
        val nightTrack = track("t5", "Mahtab", artist = "NightArtist", genre = "Ambient", durationMs = 200_000L)

        val library = listOf(lovedTrack, unplayedSameArtist, skippedTrack, morningTrack, nightTrack)
        val events = listOf(
            // Completed plays of lovedTrack 3 days ago
            PlayEvent(trackId = "t1", timestamp = nowMs - 3 * dayMs, listenedMs = 195_000L, skipped = false),
            PlayEvent(trackId = "t1", timestamp = nowMs - 4 * dayMs, listenedMs = 198_000L, skipped = false),
            PlayEvent(trackId = "t1", timestamp = nowMs - 5 * dayMs, listenedMs = 190_000L, skipped = false),
            // Habitually skipped track
            PlayEvent(trackId = "t3", timestamp = nowMs - 2 * dayMs, listenedMs = 10_000L, skipped = true),
            PlayEvent(trackId = "t3", timestamp = nowMs - 3 * dayMs, listenedMs = 15_000L, skipped = true),
            PlayEvent(trackId = "t3", timestamp = nowMs - 4 * dayMs, listenedMs = 8_000L, skipped = true),
            // Morning plays (08:00 UTC)
            PlayEvent(trackId = "t4", timestamp = 97L * dayMs + 8L * hourMs, listenedMs = 195_000L, skipped = false),
            PlayEvent(trackId = "t4", timestamp = 98L * dayMs + 9L * hourMs, listenedMs = 195_000L, skipped = false),
            // Night plays (23:00 UTC)
            PlayEvent(trackId = "t5", timestamp = 97L * dayMs + 23L * hourMs, listenedMs = 195_000L, skipped = false),
            PlayEvent(trackId = "t5", timestamp = 98L * dayMs + 22L * hourMs, listenedMs = 195_000L, skipped = false)
        )

        val model = UserTasteModel.build(
            tracks = library,
            events = events,
            favoriteIds = setOf("t1"),
            nowMs = nowMs,
            utcOffsetMs = 0
        )

        // Habitually skipped track is excluded from For You
        val forYou = UserTasteEngine.buildForYou(library, model)
        assertTrue(forYou.none { it.id == "t3" })
        assertEquals("t1", forYou.first().id)

        // Night time-of-day vibe ranks nightTrack above morningTrack
        val nightMix = UserTasteEngine.buildTimeOfDayVibe(library, model, slot = ListeningTimeSlot.NIGHT)
        val nightIdx = nightMix.indexOfFirst { it.id == "t5" }
        val morningIdx = nightMix.indexOfFirst { it.id == "t4" }
        assertTrue("Expected nightTrack ($nightIdx) ranked above morningTrack ($morningIdx)", nightIdx in 0 until morningIdx)

        // Hidden gems surfaces unplayed track by loved artist ("t2") at the top!
        val gems = UserTasteEngine.buildHiddenGems(library, model)
        assertEquals("t2", gems.first().id)
        assertTrue(gems.none { it.id == "t1" }) // t1 has 3 completed plays -> not a hidden gem
        assertTrue(gems.none { it.id == "t3" }) // skipped -> excluded
    }
}
