package com.yasinjahantigh.logarhythm.domain.lyrics

import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchScorerTest {

    private fun candidate(
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long = 0L,
        synced: Boolean = true,
        provider: String = "lrclib"
    ) = LyricsCandidate(
        id = "1",
        trackName = title,
        artistName = artist,
        albumName = album,
        durationMs = durationMs,
        isSynced = synced,
        sourceName = provider,
        providerId = provider,
        lyrics = Lyrics(plainText = if (synced) null else "line")
    )

    @Test
    fun exactMetadataIsConfidentForSyncedAndPlain() {
        val query = LyricsQuery("Bide Bi Majnon", "Mohsen Chavoshi", "Bide Bi Majnon", 197_000L)
        val synced = LyricsMatchScorer.match(query, candidate("Bide Bi Majnon", "Mohsen Chavoshi", "Bide Bi Majnon", 196_000L))
        val plain = LyricsMatchScorer.match(query, candidate("Bide Bi Majnon", "Mohsen Chavoshi", "Bide Bi Majnon", 196_000L, synced = false))
        assertTrue(synced.confident)
        assertTrue(plain.confident)
        assertTrue(synced.total >= 13)
    }

    @Test
    fun wrongArtistIsNotAutoAcceptedEvenWithSameTitle() {
        val query = LyricsQuery("Benzin", "Lenna", "", 180_000L)
        val match = LyricsMatchScorer.match(query, candidate("Benzin", "Rammstein", "", 180_000L))
        assertFalse(match.confident)
    }

    @Test
    fun plainLyricsNeedArtistConfirmation() {
        val query = LyricsQuery("Hello", "Adele", "", 0L)
        val match = LyricsMatchScorer.match(
            query,
            candidate("Hello", "", synced = false)
        )
        assertEquals(6, match.titleScore)
        assertEquals(0, match.artistScore)
        assertFalse(match.confident)
    }

    @Test
    fun persianGlyphVariantsMatch() {
        val query = LyricsQuery("تيتر", "هنرمند", "", 0L)
        val match = LyricsMatchScorer.match(query, candidate("تیتر", "هنرمند", synced = true))
        assertTrue(match.confident)
    }

    @Test
    fun noisyTitleIsStrippedBeforeMatching() {
        assertEquals("Bide Bi Majnon", LyricsTextNormalizer.queryTitle("01 - Bide Bi Majnon (Official Video) [feat. X]"))
        assertEquals("A", LyricsTextNormalizer.queryArtist("A feat. B"))
        assertEquals("", LyricsTextNormalizer.queryArtist("<unknown>"))
    }

    @Test
    fun durationOutsideToleranceDoesNotHelp() {
        val query = LyricsQuery("Song", "Artist", "", 200_000L)
        val match = LyricsMatchScorer.match(query, candidate("Song", "Artist", durationMs = 400_000L))
        assertEquals(0, match.durationScore)
        assertTrue(match.confident)
    }
}
