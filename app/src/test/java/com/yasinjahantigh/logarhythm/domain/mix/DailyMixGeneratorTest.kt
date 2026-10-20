package com.yasinjahantigh.logarhythm.domain.mix

import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DailyMixGeneratorTest {

    private lateinit var generator: DailyMixGenerator
    private lateinit var sampleTracks: List<Track>

    @Before
    fun setUp() {
        generator = DailyMixGenerator()
        sampleTracks = (1..30).map { i ->
            val artistName = when {
                i <= 10 -> "Artist A"
                i <= 20 -> "Artist B"
                else -> "Artist C"
            }
            Track(
                id = "local:$i",
                title = "Song $i",
                artist = artistName,
                album = "Album ${i / 5}",
                durationMs = 180_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "content://audio/$i",
                folder = "Music"
            )
        }
    }

    @Test
    fun generateMixes_emptyTracks_returnsEmptyList() {
        val mixes = generator.generateMixes(
            allTracks = emptyList(),
            playEvents = emptyList(),
            seedDate = 100L
        )
        assertTrue(mixes.isEmpty())
    }

    @Test
    fun generateMixes_sameSeedDate_isDeterministicAndStable() {
        val seedDate = 19700L
        val events = listOf(
            PlayEvent(1L, "local:1", 1000L, 60000L),
            PlayEvent(2L, "local:2", 2000L, 60000L)
        )

        val mixesRun1 = generator.generateMixes(sampleTracks, events, seedDate = seedDate)
        val mixesRun2 = generator.generateMixes(sampleTracks, events, seedDate = seedDate)

        assertEquals(3, mixesRun1.size)
        assertEquals(3, mixesRun2.size)

        for (i in 0 until 3) {
            val tracks1 = mixesRun1[i].tracks.map { it.id }
            val tracks2 = mixesRun2[i].tracks.map { it.id }
            assertEquals(tracks1, tracks2)
        }
    }

    @Test
    fun generateMixes_differentSeedDate_changesMixes() {
        val events = listOf(
            PlayEvent(1L, "local:1", 1000L, 60000L),
            PlayEvent(2L, "local:2", 2000L, 60000L)
        )

        val mixesDay1 = generator.generateMixes(sampleTracks, events, seedDate = 100L)
        val mixesDay2 = generator.generateMixes(sampleTracks, events, seedDate = 200L)

        val day1TrackIds = mixesDay1[0].tracks.map { it.id }
        val day2TrackIds = mixesDay2[0].tracks.map { it.id }
        assertNotEquals(day1TrackIds, day2TrackIds)
    }

    @Test
    fun generateMixes_coldStartWithoutEvents_returnsThreeValidMixes() {
        val mixes = generator.generateMixes(
            allTracks = sampleTracks,
            playEvents = emptyList(),
            favoriteTrackIds = emptySet(),
            seedDate = 12345L
        )

        assertEquals(3, mixes.size)
        for (mix in mixes) {
            assertTrue(mix.tracks.isNotEmpty())
            assertTrue(mix.description.isNotBlank())
        }
    }

    @Test
    fun generateMixes_haveDistinctIntentsAndAvoidOverlapWhenLibraryIsLargeEnough() {
        val largerLibrary = (1..60).map { i ->
            Track(
                id = "local:$i",
                title = "Song $i",
                artist = "Artist ${i % 7}",
                album = "Album ${i / 4}",
                durationMs = 180_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "content://audio/$i"
            )
        }
        val mixes = generator.generateMixes(
            allTracks = largerLibrary,
            playEvents = emptyList(),
            seedDate = 8888L
        )

        assertEquals(listOf("In Your Rotation", "Time to Revisit", "Off the Beaten Track"), mixes.map { it.title })
        assertTrue(mixes.all { it.tracks.size == DailyMixGenerator.MIX_SIZE })
        val firstIds = mixes[0].tracks.map { it.id }.toSet()
        val secondIds = mixes[1].tracks.map { it.id }.toSet()
        val thirdIds = mixes[2].tracks.map { it.id }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty())
        assertTrue(firstIds.intersect(thirdIds).isEmpty())
        assertTrue(secondIds.intersect(thirdIds).isEmpty())
    }

    @Test
    fun generateMixes_prioritizesFrequentAndForgottenLikedTracks() {
        val oldTime = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
        val recentTime = System.currentTimeMillis() - (1000L)

        val events = listOf(
            PlayEvent(1L, "local:1", recentTime, 90000L),
            PlayEvent(2L, "local:1", recentTime, 90000L),
            PlayEvent(3L, "local:1", recentTime, 90000L),
            PlayEvent(4L, "local:2", oldTime, 90000L) // Played long ago
        )
        val favorites = setOf("local:2", "local:3")

        val mixes = generator.generateMixes(
            allTracks = sampleTracks,
            playEvents = events,
            favoriteTrackIds = favorites,
            seedDate = 555L
        )

        val allMixTrackIds = mixes.flatMap { it.tracks }.map { it.id }.toSet()
        assertTrue(allMixTrackIds.contains("local:1"))
        assertTrue(allMixTrackIds.contains("local:2") || allMixTrackIds.contains("local:3"))
    }
}
