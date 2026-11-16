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

        assertEquals(listOf("daily_mix_1", "daily_mix_2", "daily_mix_3"), mixes.map { it.id })
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
        assertTrue(mixes.all { mix -> mix.tracks.all { track -> mix.trackReasons.containsKey(track.id) } })
    }

    @Test
    fun generateMixes_exposureMemoryRotatesTracksAcrossDays() {
        val largeLibrary = (1..80).map { i ->
            Track(
                id = "local:$i",
                title = "Track $i",
                artist = "Artist ${i % 15}",
                album = "Album ${i % 10}",
                durationMs = 190_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "content://audio/$i",
                genre = if (i % 2 == 0) "Pop" else "Rock"
            )
        }
        val day1 = generator.generateMixes(largeLibrary, emptyList(), seedDate = 10_000L)
        val exposure = day1.flatMap { it.tracks }.associate { it.id to 10_000L }
        val day2WithoutExposure = generator.generateMixes(largeLibrary, emptyList(), seedDate = 10_000L, recentExposureDays = exposure)
        assertEquals(day1.map { it.tracks.map { t -> t.id } }, day2WithoutExposure.map { it.tracks.map { t -> t.id } })

        val nextDay = generator.generateMixes(largeLibrary, emptyList(), seedDate = 10_001L, recentExposureDays = exposure)
        val day1Ids = day1.flatMap { it.tracks }.map { it.id }.toSet()
        val nextDayIds = nextDay.flatMap { it.tracks }.map { it.id }.toSet()
        // A fresh day should swap out some of yesterday's exposed tracks rather than repeating the exact 60.
        assertTrue(nextDayIds != day1Ids)
    }

    @Test
    fun generateMixes_discoveryMixPrefersTasteAlignedUnplayedTracksAndSequencesSmoothly() {
        val seedDay = 20_000L
        val recentTs = seedDay * 86_400_000L - 3L * 86_400_000L
        val library = (1..75).map { i ->
            val artist = when {
                i <= 30 -> "FavoritePersian_${i % 6}"
                i <= 55 -> "PersianSinger_${i % 8}"
                else -> "MetalBand_${i % 5}"
            }
            val genre = when {
                i <= 55 -> if (i % 2 == 0) "پاپ" else "Pop"
                else -> "Metal"
            }
            Track(
                id = "local:$i",
                title = "Song $i",
                artist = artist,
                album = "Album $i",
                durationMs = 200_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "content://audio/$i",
                genre = genre,
                year = if (i <= 55) 2021 else 1988
            )
        }
        // User repeatedly plays and loves tracks 1..15 (پاپ / Pop), leaving 31..75 unplayed.
        val events = (1..15).flatMap { i ->
            listOf(
                PlayEvent(i.toLong() * 2, "local:$i", recentTs, 195_000L, skipped = false),
                PlayEvent(i.toLong() * 2 + 1, "local:$i", recentTs + 60_000L, 195_000L, skipped = false)
            )
        }
        val mixes = generator.generateMixes(
            allTracks = library,
            playEvents = events,
            favoriteTrackIds = (1..5).map { "local:$it" }.toSet(),
            seedDate = seedDay
        )

        val mix3 = mixes[2]
        assertEquals(DailyMixGenerator.MIX_SIZE, mix3.tracks.size)
        val popCountInDiscovery = mix3.tracks.count { it.genre == "پاپ" || it.genre == "Pop" }
        assertTrue("Expected Mix 3 to strongly favor taste-matched Pop/پاپ over Metal, got $popCountInDiscovery", popCountInDiscovery >= 14)

        // Check no consecutive tracks share the same artist in Mix 1 when diverse artists exist.
        val mix1 = mixes[0]
        val consecutiveSameArtist = mix1.tracks.zipWithNext().count { (a, b) -> a.artist == b.artist }
        assertEquals(0, consecutiveSameArtist)
    }
}
