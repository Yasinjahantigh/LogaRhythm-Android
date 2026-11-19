package com.yasinjahantigh.logarhythm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yasinjahantigh.logarhythm.core.utils.ArtworkColorExtractor
import com.yasinjahantigh.logarhythm.core.utils.SearchUtils
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDatabase
import com.yasinjahantigh.logarhythm.data.room.TrackEntity
import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.discovery.Discovery
import com.yasinjahantigh.logarhythm.domain.library.ArtistIndex
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.mix.DailyMixGenerator
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import com.yasinjahantigh.logarhythm.playback.MediaItemMapper
import com.yasinjahantigh.logarhythm.playback.QueuePersistenceManager
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull
import com.yasinjahantigh.logarhythm.ui.components.formatCount
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Deterministic performance benchmark across the four core user flows:
 * 1. Cold Startup (queue restore, DataStore preference cascade)
 * 2. Library & Metadata Loading (artist indexing, metadata normalization, tag cleaning, mixes/discovery, search)
 * 3. UI Scrolling & Rendering (per-row text/time formatting, artwork color extraction)
 * 4. Playback Responsiveness (MediaItem batch mapping, radio/endless queue recommendation)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceBaselineBenchmarkTest {

    private val trackCount = 2500
    private val eventCount = 1500

    private fun buildSyntheticLibrary(count: Int = trackCount): List<Track> {
        val rng = Random(42)
        val artists = listOf(
            "Radiohead",
            "Daft Punk feat. Pharrell Williams",
            "Pink Floyd",
            "Coldplay & Rihanna",
            "Simon & Garfunkel",
            "Homayoun Shajarian",
            "Mohsen Chavoshi feat. Sina Sarlak",
            "The Weeknd, Playboi Carti & Madonna",
            "Hans Zimmer",
            "Tame Impala",
            "Billie Eilish",
            "Shadmehr Aghili",
            "Kendrick Lamar ft. SZA",
            "Arctic Monkeys",
            "Sia; David Guetta",
            "Ludovico Einaudi",
            "Linkin Park",
            "Florence + The Machine",
            "Hayedeh",
            "Taylor Swift feat. Bon Iver",
            "Unknown Artist",
            "AC/DC",
            "Of Monsters and Men",
            "Kiasmos / Olafur Arnalds",
            "Mehdi Yarrahi"
        )
        val genres = listOf(
            "Alternative Rock", "Electronic / Synth-Pop", "Persian Pop", "Deep House",
            "Classical Piano", "Hip-Hop/Rap", "Indie Folk", "Metal", "Ambient",
            "Soundtrack", "R&B / Soul", "Jazz Fusion", "Lo-Fi Beats", "پاپ", "سنتی",
            "راک", "الکترونیک", "Unknown", "", "Synthwave"
        )
        val albums = (1..200).map { i -> "Album Volume $i (Deluxe Edition)" }
        val folders = listOf("Music/Downloads", "Music/Rock", "Music/Persian", "Music/FLAC", "Music/Chill")
        val nowSec = 1_730_000_000L

        return List(count) { i ->
            val artist = artists[i % artists.size]
            val genre = genres[i % genres.size]
            val album = albums[i % albums.size]
            val title = when (i % 6) {
                0 -> "Midnight Rain (Remix) [$i]"
                1 -> "Acoustic Morning Light $i"
                2 -> "Energy Workout Pump $i"
                3 -> "باران شبانه و دلتنگی $i"
                4 -> "Deep Focus Piano Study $i"
                else -> "Track Title Number $i (Official Audio)"
            }
            Track(
                id = "local:$i",
                title = title,
                artist = artist,
                album = album,
                durationMs = (120_000L + rng.nextLong(240_000L)),
                artworkUri = if (i % 5 == 0) null else "content://media/external/audio/albumart/${i % 200}",
                sourceType = SourceType.LOCAL,
                uri = "content://media/external/audio/media/$i",
                dateModified = nowSec - i * 60L,
                folder = folders[i % folders.size],
                genre = genre,
                dateAdded = nowSec - i * 120L,
                albumArtist = if (i % 3 == 0) "" else artist.substringBefore(" feat."),
                filePath = "/storage/emulated/0/${folders[i % folders.size]}/track_$i.mp3",
                year = 1985 + (i % 41),
                trackNumber = (i % 14) + 1,
                discNumber = 1
            )
        }
    }

    private fun buildSyntheticPlayEvents(tracks: List<Track>, count: Int = eventCount): List<PlayEvent> {
        val rng = Random(99)
        val nowMs = 1_730_000_000_000L
        return List(count) { i ->
            // Skew plays toward first 400 tracks for realistic listening patterns
            val trackIdx = if (i % 4 == 0) rng.nextInt(tracks.size) else rng.nextInt(minOf(400, tracks.size))
            val t = tracks[trackIdx]
            val skipped = rng.nextFloat() < 0.15f
            PlayEvent(
                id = i.toLong() + 1L,
                trackId = t.id,
                timestamp = nowMs - rng.nextLong(30L * 24 * 60 * 60 * 1000L),
                listenedMs = if (skipped) 12_000L else (t.durationMs * 0.85).toLong(),
                skipped = skipped
            )
        }
    }

    private fun measureMedianMs(warmup: Int = 2, iterations: Int = 5, block: () -> Unit): Double {
        repeat(warmup) { block() }
        val samples = DoubleArray(iterations) {
            measureNanoTime { block() } / 1_000_000.0
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    @Test
    fun runAllPerformanceBenchmarks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tracks = buildSyntheticLibrary(trackCount)
        val events = buildSyntheticPlayEvents(tracks, eventCount)
        val favorites = tracks.take(120).map { it.id }.toSet()
        val nowMs = 1_730_000_000_000L
        val seedDay = nowMs / TasteProfile.DAY_MS

        val results = LinkedHashMap<String, String>()

        // =====================================================================
        // 1. COLD STARTUP FLOWS
        // =====================================================================
        val db = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStoreFile = java.io.File(context.cacheDir, "bench_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile }
        )
        val prefs = PreferencesManager(dataStore, context)
        try {
            val musicDao = db.musicDao()
            // Insert 900 tracks (kept <= 999 so unmodified SQLite IN(:ids) doesn't throw bind error)
            val queueSize = 900
            val queueTracks = tracks.take(queueSize)
            musicDao.insertTracks(
                queueTracks.map { t ->
                    TrackEntity(
                        id = t.id,
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        durationMs = t.durationMs,
                        artworkUri = t.artworkUri,
                        sourceType = t.sourceType.prefix,
                        uri = t.uri,
                        dateModified = t.dateModified,
                        folder = t.folder,
                        genre = t.genre,
                        dateAdded = t.dateAdded,
                        albumArtist = t.albumArtist,
                        filePath = t.filePath,
                        year = t.year,
                        trackNumber = t.trackNumber,
                        discNumber = t.discNumber
                    )
                }
            )

            val queueManager = QueuePersistenceManager(prefs, musicDao)
            val shuffled = queueTracks.shuffled(Random(7))
            queueManager.saveQueue(shuffled, 42, 65_000L, true, queueTracks)

            // Warmup + measure restoreQueue() on 900-track shuffled queue
            repeat(1) {
                val restored = queueManager.restoreQueue()
                assertNotNull(restored)
                assertEquals(queueSize, restored!!.tracks.size)
                assertEquals(queueSize, restored.originalTracks.size)
            }
            val restoreSamples = DoubleArray(5) {
                measureNanoTime {
                    val restored = queueManager.restoreQueue()
                    assertEquals(queueSize, restored!!.originalTracks.size)
                } / 1_000_000.0
            }.also { it.sort() }
            results["1a. Startup: QueuePersistenceManager.restoreQueue (900 shuffled)"] =
                String.format(Locale.US, "%.2f ms", restoreSamples[restoreSamples.size / 2])

            // Measure DataStore preference cascade: 10 unrelated preference flows observing while
            // 5 unrelated writes happen (simulating queue save / mix exposure / setting updates)
            val unrelatedEmissions = AtomicInteger(0)
            val collectorsJob = launch {
                val flows = listOf(
                    prefs.themeMode,
                    prefs.themePresetId,
                    prefs.themeStyle,
                    prefs.colorSource,
                    prefs.appFont,
                    prefs.appLanguage,
                    prefs.hiddenTrackIds,
                    prefs.cornerRoundness,
                    prefs.vinylMode,
                    prefs.splitArtists
                )
                flows.forEach { flow ->
                    launch {
                        var first = true
                        flow.collect {
                            if (first) first = false else unrelatedEmissions.incrementAndGet()
                        }
                    }
                }
            }
            delay(150L)
            repeat(5) { i ->
                prefs.setFadeBetweenSongsSec(1 + i)
                delay(40L)
            }
            delay(150L)
            collectorsJob.cancel()
            results["1b. Startup/Runtime: DataStore spurious emissions (10 flows x 5 writes)"] =
                "${unrelatedEmissions.get()} redundant emissions"

            // Measure QueuePersistenceManager.saveQueue() latency
            val saveSamples = DoubleArray(5) {
                measureNanoTime {
                    queueManager.saveQueue(shuffled, 42 + it, 65_000L + it * 1000L, true, queueTracks)
                } / 1_000_000.0
            }.also { it.sort() }
            results["1c. Playback/Startup: QueuePersistenceManager.saveQueue (900 tracks)"] =
                String.format(Locale.US, "%.2f ms", saveSamples[saveSamples.size / 2])
        } finally {
            db.close()
            dataStoreFile.delete()
        }

        // =====================================================================
        // 2. LIBRARY & METADATA LOADING FLOWS
        // =====================================================================
        val splitConfig = ArtistSplitConfig(
            enabled = true,
            separators = com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter.DEFAULT_SEPARATORS
        )
        val artistIndexMs = measureMedianMs(warmup = 1, iterations = 5) {
            val index = ArtistIndex.build(tracks, splitConfig)
            assertTrue(index.artists.isNotEmpty())
        }
        results["2a. Library: ArtistIndex.build ($trackCount tracks, split=ON)"] =
            String.format(Locale.US, "%.2f ms", artistIndexMs)

        val metaNormMs = measureMedianMs(warmup = 1, iterations = 5) {
            var count = 0
            for (t in tracks) {
                val g = MetadataNormalizer.canonicalizeGenre(t.genre)
                val k = MetadataNormalizer.genreGroupKey(t.genre)
                val clean = MetadataNormalizer.cleanDisplayText(t.title)
                if (g.isNotEmpty() || k.isNotEmpty() || clean.isNotEmpty()) count++
            }
            assertEquals(trackCount, count)
        }
        results["2b. Metadata: MetadataNormalizer ($trackCount tracks x 3 ops)"] =
            String.format(Locale.US, "%.2f ms", metaNormMs)

        val tagCleanerMs = measureMedianMs(warmup = 1, iterations = 3) {
            var count = 0
            for (t in tracks) {
                val cleaned = TagSuggestionCleaner.clean(t.title, t.artist, t.album, t.genre, t.year, t.albumArtist)
                if (cleaned.title.isNotEmpty()) count++
            }
            assertEquals(trackCount, count)
        }
        results["2c. Metadata: TagSuggestionCleaner.clean ($trackCount tracks)"] =
            String.format(Locale.US, "%.2f ms", tagCleanerMs)

        var builtProfile: TasteProfile = TasteProfile.EMPTY
        val tasteProfileMs = measureMedianMs(warmup = 1, iterations = 5) {
            builtProfile = TasteProfile.build(
                events = events,
                favoriteTrackIds = favorites,
                nowMs = nowMs,
                utcOffsetMs = 0,
                library = tracks
            )
            assertTrue(builtProfile.topArtists().isNotEmpty())
        }
        results["2d. Home/Taste: TasteProfile.build ($trackCount tracks, $eventCount events)"] =
            String.format(Locale.US, "%.2f ms", tasteProfileMs)

        val mixGenerator = DailyMixGenerator()
        val dailyMixMs = measureMedianMs(warmup = 1, iterations = 3) {
            val mixes = mixGenerator.generateMixes(
                allTracks = tracks,
                playEvents = events,
                favoriteTrackIds = favorites,
                seedDate = seedDay,
                tasteProfile = builtProfile
            )
            assertTrue(mixes.isNotEmpty())
        }
        results["2e. Home/Mixes: DailyMixGenerator.generateMixes ($trackCount tracks)"] =
            String.format(Locale.US, "%.2f ms", dailyMixMs)

        val discoveryMs = measureMedianMs(warmup = 1, iterations = 5) {
            val rn = Discovery.rightNow(tracks, builtProfile, hour = 21, limit = 15)
            val byp = Discovery.becauseYouPlayed(tracks, events, builtProfile, limit = 12)
            assertTrue(rn.tracks.isNotEmpty())
            assertNotNull(byp)
        }
        results["2f. Home/Discovery: rightNow + becauseYouPlayed ($trackCount tracks)"] =
            String.format(Locale.US, "%.2f ms", discoveryMs)

        val searchMs = measureMedianMs(warmup = 2, iterations = 7) {
            var hits = 0
            val query = "acoustic morning light"
            for (t in tracks) {
                if (SearchUtils.matchesTokens("${t.title} ${t.artist} ${t.album}", query)) hits++
            }
            assertTrue(hits > 0)
        }
        results["2g. Search: SearchUtils.matchesTokens ($trackCount tracks)"] =
            String.format(Locale.US, "%.2f ms", searchMs)

        // =====================================================================
        // 3. UI SCROLLING & RENDERING FLOWS
        // =====================================================================
        val rowSample = tracks.take(1000)
        val rowFormatMs = measureMedianMs(warmup = 2, iterations = 7) {
            var nonNull = 0
            for (t in rowSample) {
                if (t.title.cleanMetaOrNull() != null) nonNull++
                t.artist.cleanMetaOrNull()
                t.album.cleanMetaOrNull()
                TimeUtils.formatDuration(t.durationMs)
                formatCount(t.trackNumber)
            }
            assertTrue(nonNull > 0)
        }
        results["3a. UI Scroll: TrackListItem formatting (1000 rows)"] =
            String.format(Locale.US, "%.2f ms", rowFormatMs)

        val testBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 64) {
                for (x in 0 until 64) {
                    setPixel(x, y, AndroidColor.rgb((x * 4) % 256, (y * 4) % 256, ((x + y) * 2) % 256))
                }
            }
        }
        val colorExtractMs = measureMedianMs(warmup = 2, iterations = 7) {
            repeat(50) {
                val c = ArtworkColorExtractor.extractVibrantColorFromBitmap(testBitmap, Color(0xFF006A60))
                assertTrue(c != Color.Unspecified)
            }
        }
        testBitmap.recycle()
        results["3b. UI Theme: ArtworkColorExtractor.extractVibrantColor (50 x 64x64)"] =
            String.format(Locale.US, "%.2f ms", colorExtractMs)

        // =====================================================================
        // 4. PLAYBACK RESPONSIVENESS FLOWS
        // =====================================================================
        val mediaItemMapMs = measureMedianMs(warmup = 1, iterations = 5) {
            val items = tracks.map { MediaItemMapper.toMediaItem(it) }
            assertEquals(trackCount, items.size)
        }
        results["4a. Playback: MediaItemMapper.toMediaItem batch ($trackCount tracks)"] =
            String.format(Locale.US, "%.2f ms", mediaItemMapMs)

        val radioRecMs = measureMedianMs(warmup = 1, iterations = 5) {
            val recs = TrackCuration.recommendNextTracks(
                currentTrack = tracks[0],
                allTracks = tracks,
                limit = 15,
                profile = builtProfile
            )
            assertEquals(15, recs.size)
        }
        results["4b. Playback: TrackCuration.recommendNextTracks ($trackCount tracks)"] =
            String.format(Locale.US, "%.2f ms", radioRecMs)

        println("================ PERFORMANCE BENCHMARK RESULTS ================")
        results.forEach { (k, v) -> println("  $k => $v") }
        println("===============================================================")
    }
}
