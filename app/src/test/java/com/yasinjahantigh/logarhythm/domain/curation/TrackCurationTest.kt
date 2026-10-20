package com.yasinjahantigh.logarhythm.domain.curation

import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackCurationTest {
    private val tracks = (1..8).map { index ->
        Track(
            id = "local:$index",
            title = "Song $index",
            artist = if (index % 2 == 0) "Artist A" else "Artist B",
            album = "Album ${index / 3}",
            durationMs = 180_000L + index * 1_000L,
            artworkUri = null,
            sourceType = SourceType.LOCAL,
            uri = "content://audio/$index",
            folder = "Music",
            genre = if (index % 2 == 0) "Rock" else "Acoustic"
        )
    }

    @Test
    fun generateMoodPlaylist_emptyLibrary_returnsEmptyList() {
        assertTrue(
            TrackCuration.generateMoodPlaylist(
                TrackCuration.Mood.DEEP_FOCUS,
                emptyList()
            ).isEmpty()
        )
    }

    @Test
    fun generateMoodPlaylist_respectsRequestedLimitWithoutDuplicates() {
        val result = TrackCuration.generateMoodPlaylist(
            mood = TrackCuration.Mood.DEEP_FOCUS,
            allTracks = tracks,
            count = 4
        )

        assertEquals(4, result.size)
        assertEquals(4, result.map { it.id }.toSet().size)
        assertTrue(result.all { candidate -> tracks.any { it.id == candidate.id } })
    }

    @Test
    fun generateMoodPlaylist_neverReturnsMoreTracksThanAvailable() {
        val result = TrackCuration.generateMoodPlaylist(
            mood = TrackCuration.Mood.ACOUSTIC_MORNING,
            allTracks = tracks.take(3),
            count = 15
        )

        assertEquals(3, result.size)
    }
    @Test
    fun analyzeTrackDna_recognizesPersianMoodKeywords() {
        val neutral = sampleTrack(id = "local:neutral", title = "Instrumental")
        val melancholic = sampleTrack(id = "local:sad", title = "آهنگ غمگین و بارانی")
        val upbeat = sampleTrack(id = "local:upbeat", title = "ریمیکس تند برای رقص", genre = "رقص")
        val calm = sampleTrack(id = "local:calm", title = "آرامش برای خواب")

        val neutralValence = TrackCuration.analyzeTrackDna(neutral).moodValence
        val melancholicValence = TrackCuration.analyzeTrackDna(melancholic).moodValence
        val upbeatEnergy = TrackCuration.analyzeTrackDna(upbeat).energyScore
        val calmEnergy = TrackCuration.analyzeTrackDna(calm).energyScore

        assertTrue(melancholicValence < neutralValence)
        assertTrue(upbeatEnergy > calmEnergy)
    }

    @Test
    fun analyzeTrackDna_recognizesAcousticTextAndGenre() {
        val acoustic = sampleTrack(id = "acoustic", title = "Quiet Evening", genre = "Acoustic")
        val electronic = sampleTrack(id = "electronic", title = "Quiet Evening", genre = "Electronic")

        assertTrue(
            TrackCuration.analyzeTrackDna(acoustic).acousticScore >
                TrackCuration.analyzeTrackDna(electronic).acousticScore
        )
    }

    @Test
    fun acousticMorning_prefersAcousticMetadataOverElectronicMetadata() {
        val acoustic = sampleTrack(id = "acoustic-morning", title = "Morning", genre = "Acoustic")
        val electronic = sampleTrack(id = "electronic-morning", title = "Morning", genre = "Electronic")

        val result = TrackCuration.generateMoodPlaylist(
            mood = TrackCuration.Mood.ACOUSTIC_MORNING,
            allTracks = listOf(electronic, acoustic),
            count = 1
        )

        assertEquals("acoustic-morning", result.single().id)
    }

    @Test
    fun recommendNextTracks_excludesExplicitlyExcludedTrackIds() {
        val current = tracks.first()
        val excluded = tracks[1]
        val result = TrackCuration.recommendNextTracks(
            currentTrack = current,
            allTracks = tracks,
            limit = 10,
            excludedTrackIds = setOf(excluded.id)
        )

        assertTrue(result.none { it.id == excluded.id })
    }

    @Test
    fun generateMoodPlaylist_zeroOrNegativeLimit_returnsEmptyList() {
        assertTrue(
            TrackCuration.generateMoodPlaylist(TrackCuration.Mood.DEEP_FOCUS, tracks, count = 0).isEmpty()
        )
        assertTrue(
            TrackCuration.generateMoodPlaylist(TrackCuration.Mood.DEEP_FOCUS, tracks, count = -2).isEmpty()
        )
    }

    @Test
    fun recommendNextTracks_excludesCurrentTrackAndRespectsLimit() {
        val current = tracks.first()
        val result = TrackCuration.recommendNextTracks(current, tracks, limit = 3)

        assertEquals(3, result.size)
        assertTrue(result.none { it.id == current.id })
    }

    @Test
    fun recommendNextTracks_avoidsConsecutiveArtistRepeatsWhenAlternativesExist() {
        val seed = sampleTrack(id = "radio-seed", title = "Seed", genre = "Rock").copy(artist = "Artist A")
        val candidates = listOf(
            sampleTrack(id = "radio-a1", title = "Song A1", genre = "Rock").copy(artist = "Artist A", album = "Album A"),
            sampleTrack(id = "radio-a2", title = "Song A2", genre = "Rock").copy(artist = "Artist A", album = "Album A"),
            sampleTrack(id = "radio-b", title = "Song B", genre = "Rock").copy(artist = "Artist B", album = "Album B"),
            sampleTrack(id = "radio-c", title = "Song C", genre = "Rock").copy(artist = "Artist C", album = "Album C")
        )

        val result = TrackCuration.recommendNextTracks(seed, candidates, limit = 3)

        assertTrue(result.zipWithNext().all { (left, right) -> left.artist != right.artist })
    }

    @Test
    fun recommendNextTracks_nonPositiveLimit_returnsEmptyList() {
        assertTrue(TrackCuration.recommendNextTracks(tracks.first(), tracks, limit = 0).isEmpty())
        assertTrue(TrackCuration.recommendNextTracks(tracks.first(), tracks, limit = -1).isEmpty())
    }

    private fun sampleTrack(
        id: String,
        title: String,
        genre: String = "",
    ) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 240_000L,
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://audio/$id",
        genre = genre
    )

}
