package com.yasinjahantigh.logarhythm.download

import com.yasinjahantigh.logarhythm.domain.download.DownloadPipelineException
import com.yasinjahantigh.logarhythm.domain.download.DownloadSafetyVerifier
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadQuality
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSafetyVerifierTest {

    private fun candidate(
        id: String,
        title: String,
        artist: String,
        durationMs: Long = 210_000L,
        source: DownloadSource = DownloadSource.MUSICMAN,
        hasReadyAudio: Boolean = false
    ) = DownloadSearchResult(
        id = id,
        title = title,
        artist = artist,
        source = source,
        url = "https://example.com/track/$id",
        durationMs = durationMs,
        thumbnailUrl = null,
        hasReadyAudio = hasReadyAudio
    )

    private fun item(
        title: String = "Khodafez",
        artist: String = "Mehrad Hidden",
        durationMs: Long = 210_000L,
        source: DownloadSource = DownloadSource.DEEZER,
        videoId: String = "14389201"
    ) = DownloadItem(
        id = "dl_123",
        title = title,
        artist = artist,
        album = "Toole Arze Zendegi",
        albumArtist = artist,
        trackNumber = 1,
        durationMs = durationMs,
        source = source,
        sourceUrl = "https://www.deezer.com/track/$videoId",
        videoId = videoId,
        quality = DownloadQuality.BEST,
        status = DownloadStatus.SAVING,
        progress = 100,
        etaSeconds = 0,
        artworkUrl = null,
        mediaStoreId = null,
        contentUri = null,
        filePath = null,
        fileSizeBytes = 0,
        errorKind = null,
        errorMessage = null,
        statusDetail = null,
        usedFallback = false,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun isSafeMatch_rejectsKhodayeBinahayatWhenKhodafezIsRequested() {
        val safe = DownloadSafetyVerifier.isSafeMatch(
            expectedSource = DownloadSource.DEEZER,
            expectedTrackId = "14389201",
            expectedTitle = "Khodafez",
            expectedArtist = "Mehrad Hidden",
            expectedDurationMs = 210_000L,
            candidateSource = DownloadSource.MUSICMAN,
            candidateTrackId = "999888",
            candidateTitle = "Khodaye Binahayat",
            candidateArtist = "Farhad Mehrad",
            candidateDurationMs = 210_000L
        )
        assertFalse("Khodaye Binahayat must NEVER be accepted as a match for Khodafez", safe)
    }

    @Test
    fun findBestMatch_rejectsReadyKhodayeBinahayatAndPicksUncrawledKhodafez() {
        val candidates = listOf(
            candidate(
                id = "wrong_1",
                title = "Khodaye Binahayat",
                artist = "Farhad Mehrad",
                durationMs = 210_000L,
                hasReadyAudio = true // Already cached on server
            ),
            candidate(
                id = "right_1",
                title = "Khodafez",
                artist = "Mehrad Hidden",
                durationMs = 212_000L,
                hasReadyAudio = false // Needs server crawling
            )
        )

        val best = DownloadSafetyVerifier.findBestMatch(
            expectedSource = DownloadSource.DEEZER,
            expectedTrackId = "14389201",
            expectedTitle = "Khodafez",
            expectedArtist = "Mehrad Hidden",
            expectedDurationMs = 210_000L,
            candidates = candidates,
            candidateSource = DownloadSource.MUSICMAN,
            trackIdExtractor = { it.id },
            titleExtractor = { it.title },
            artistExtractor = { it.artist },
            durationExtractor = { it.durationMs },
            hasReadyAudioExtractor = { it.hasReadyAudio }
        )

        assertNotNull("Must find the verified match", best)
        assertEquals("right_1", best!!.id)
        assertEquals("Khodafez", best.title)
        assertEquals("Mehrad Hidden", best.artist)
    }

    @Test
    fun findBestMatch_returnsNullWhenAllCandidatesAreMismatched() {
        val candidates = listOf(
            candidate(id = "w1", title = "Khodaye Binahayat", artist = "Farhad Mehrad", hasReadyAudio = true),
            candidate(id = "w2", title = "Khoda Ba Mast", artist = "Unknown", hasReadyAudio = true),
            candidate(id = "w3", title = "Be Yade Khoda", artist = "Ali", hasReadyAudio = true)
        )

        val best = DownloadSafetyVerifier.findBestMatch(
            expectedSource = DownloadSource.DEEZER,
            expectedTrackId = "14389201",
            expectedTitle = "Khodafez",
            expectedArtist = "Mehrad Hidden",
            expectedDurationMs = 210_000L,
            candidates = candidates,
            candidateSource = DownloadSource.MUSICMAN,
            trackIdExtractor = { it.id },
            titleExtractor = { it.title },
            artistExtractor = { it.artist },
            durationExtractor = { it.durationMs },
            hasReadyAudioExtractor = { it.hasReadyAudio }
        )

        assertNull("Must return null when no candidate matches safely", best)
    }

    @Test
    fun isSafeMatch_rejectsDifferentArtistWithSameTitle() {
        val safe = DownloadSafetyVerifier.isSafeMatch(
            expectedSource = DownloadSource.DEEZER,
            expectedTrackId = "14389201",
            expectedTitle = "Khodafez",
            expectedArtist = "Mehrad Hidden",
            expectedDurationMs = 210_000L,
            candidateSource = DownloadSource.MUSICMAN,
            candidateTrackId = "222333",
            candidateTitle = "Khodafez",
            candidateArtist = "Kourosh Yaghmaei",
            candidateDurationMs = 210_000L
        )
        assertFalse("Must reject candidate by a completely different artist", safe)
    }

    @Test
    fun isSafeMatch_rejectsLargeDurationMismatch() {
        val safe = DownloadSafetyVerifier.isSafeMatch(
            expectedSource = DownloadSource.DEEZER,
            expectedTrackId = "14389201",
            expectedTitle = "Khodafez",
            expectedArtist = "Mehrad Hidden",
            expectedDurationMs = 210_000L, // 3m 30s
            candidateSource = DownloadSource.MUSICMAN,
            candidateTrackId = "222333",
            candidateTitle = "Khodafez",
            candidateArtist = "Mehrad Hidden",
            candidateDurationMs = 45_000L // 45s sample
        )
        assertFalse("Must reject candidate with large duration discrepancy", safe)
    }

    @Test
    fun isSafeMatch_acceptsCleanedTagsAndCollaborators() {
        val safe = DownloadSafetyVerifier.isSafeMatch(
            expectedSource = DownloadSource.DEEZER,
            expectedTrackId = "14389201",
            expectedTitle = "Khodafez",
            expectedArtist = "Mehrad Hidden",
            expectedDurationMs = 210_000L,
            candidateSource = DownloadSource.MUSICMAN,
            candidateTrackId = "222333",
            candidateTitle = "Khodafez (feat. Sohrab MJ) [320]",
            candidateArtist = "Mehrad Hidden & Sohrab MJ",
            candidateDurationMs = 214_000L
        )
        assertTrue("Must accept candidate with featured tag, collaborator, and close duration", safe)
    }

    @Test
    fun isSafeMatch_acceptsExactTrackIdOnSameSource() {
        val safe = DownloadSafetyVerifier.isSafeMatch(
            expectedSource = DownloadSource.MUSICMAN,
            expectedTrackId = "it_554433",
            expectedTitle = "Song A",
            expectedArtist = "Artist B",
            expectedDurationMs = 200_000L,
            candidateSource = DownloadSource.MUSICMAN,
            candidateTrackId = "554433",
            candidateTitle = "Different Formatted Title",
            candidateArtist = "Artist B",
            candidateDurationMs = 200_000L
        )
        assertTrue("Must accept when track ID matches on the same source", safe)
    }

    @Test
    fun verifyBeforeSaving_throwsExceptionWhenSongIsMismatched() {
        val requestedItem = item(title = "Khodafez", artist = "Mehrad Hidden")

        val exception = assertThrows(DownloadPipelineException::class.java) {
            DownloadSafetyVerifier.verifyBeforeSaving(
                item = requestedItem,
                candidateSource = DownloadSource.MUSICMAN,
                candidateTrackId = "999888",
                candidateTitle = "Khodaye Binahayat",
                candidateArtist = "Farhad Mehrad",
                candidateDurationMs = 210_000L
            )
        }
        assertTrue(exception.message.orEmpty().contains("Khodafez"))
        assertTrue(exception.message.orEmpty().contains("Khodaye Binahayat"))
    }

    @Test
    fun verifyBeforeSaving_passesWhenCandidateMatches() {
        val requestedItem = item(title = "Khodafez", artist = "Mehrad Hidden", durationMs = 210_000L)

        // Should not throw
        DownloadSafetyVerifier.verifyBeforeSaving(
            item = requestedItem,
            candidateSource = DownloadSource.MUSICMAN,
            candidateTrackId = "554433",
            candidateTitle = "Khodafez",
            candidateArtist = "Mehrad Hidden",
            candidateDurationMs = 212_000L
        )
    }
}
