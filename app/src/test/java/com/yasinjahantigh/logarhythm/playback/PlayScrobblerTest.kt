package com.yasinjahantigh.logarhythm.playback

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.domain.model.AlbumStat
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.StatsSortBy
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayScrobblerTest {

    private lateinit var statsRepository: FakeStatsRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var scrobbler: PlayScrobbler

    private val sampleTrack = Track(
        id = "local:1",
        title = "Tehran",
        artist = "Sogand",
        album = "Tehran Album",
        durationMs = 300_000L, // 5 minutes
        artworkUri = null,
        sourceType = SourceType.LOCAL,
        uri = "content://media/external/audio/media/1"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create { testFile }
        preferencesManager = PreferencesManager(dataStore, context)
        statsRepository = FakeStatsRepository()
        scrobbler = PlayScrobbler(statsRepository, preferencesManager)
    }

    private fun awaitEvents(timeoutMs: Long = 1500L, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition() && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(15)
        }
    }

    @Test
    fun singlePlay_recordsPlayOnceThresholdReached() = runTest {
        scrobbler.onTrackChanged(sampleTrack.id, force = true)

        // Tick past threshold (150s = 150_000ms for a 300s track)
        // Simulate playing for 160 seconds
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }

        awaitEvents { statsRepository.recordedEvents.any { !it.skipped } }

        val plays = statsRepository.recordedEvents.filter { !it.skipped }
        assertTrue("Expected at least one play event recorded", plays.isNotEmpty())
        assertEquals(sampleTrack.id, plays.first().trackId)
    }

    @Test
    fun repeatOne_loopingMultipleTimes_recordsPlayForEveryRepetition() = runTest {
        scrobbler.onTrackChanged(sampleTrack.id, force = true)

        // Loop 1: Play past threshold to 160s
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 1 }
        assertEquals("First play must be recorded", 1, statsRepository.recordedEvents.count { !it.skipped })

        // Track loops back to beginning via onTrackLooped (repeat one)
        Thread.sleep(50)
        scrobbler.onTrackLooped(sampleTrack.id)

        // Loop 2: Play past threshold to 160s
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 2 }
        assertEquals("Second play must be recorded on repeat one", 2, statsRepository.recordedEvents.count { !it.skipped })

        // Track loops again
        Thread.sleep(50)
        scrobbler.onTrackLooped(sampleTrack.id)

        // Loop 3: Play past threshold to 160s
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 3 }
        assertEquals("Third play must be recorded on repeat one", 3, statsRepository.recordedEvents.count { !it.skipped })
    }

    @Test
    fun positionWrapAroundInTicker_automaticallyTriggersLoop() = runTest {
        scrobbler.onTrackChanged(sampleTrack.id, force = true)

        // Play past threshold
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 1 }
        assertEquals(1, statsRepository.recordedEvents.count { !it.skipped })

        // Play to 290s
        scrobbler.onTick(
            isPlaying = true,
            track = sampleTrack,
            durationMs = sampleTrack.durationMs,
            positionMs = 290_000L
        )

        // Simulate ExoPlayer looping position back to 500ms without external callback
        Thread.sleep(50)
        scrobbler.onTick(
            isPlaying = true,
            track = sampleTrack,
            durationMs = sampleTrack.durationMs,
            positionMs = 500L
        )

        // Now play loop 2 past threshold
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 2 }

        val totalPlays = statsRepository.recordedEvents.count { !it.skipped }
        assertEquals("Position wrap-around must allow second play to be recorded", 2, totalPlays)
    }

    @Test
    fun debounce_doesNotDuplicateLoopWhenBothDiscontinuityAndTransitionFire() = runTest {
        scrobbler.onTrackChanged(sampleTrack.id, force = true)

        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 1 }
        assertEquals(1, statsRepository.recordedEvents.count { !it.skipped })

        // Fired virtually simultaneously by onMediaItemTransition and onPositionDiscontinuity
        scrobbler.onTrackLooped(sampleTrack.id)
        scrobbler.onTrackLooped(sampleTrack.id)

        // Should cleanly be in session 2 without duplicate resets
        for (sec in 1..160) {
            scrobbler.onTick(
                isPlaying = true,
                track = sampleTrack,
                durationMs = sampleTrack.durationMs,
                positionMs = sec * 1_000L
            )
        }
        awaitEvents { statsRepository.recordedEvents.count { !it.skipped } >= 2 }

        val totalPlays = statsRepository.recordedEvents.count { !it.skipped }
        assertEquals(2, totalPlays)
    }

    class FakeStatsRepository : StatsRepository {
        val recordedEvents = mutableListOf<PlayEvent>()
        val updatedEvents = mutableListOf<Triple<String, Long, Long>>()

        override suspend fun recordPlayEvent(event: PlayEvent) {
            synchronized(recordedEvents) {
                recordedEvents.add(event)
            }
        }

        override suspend fun updatePlayEventListened(trackId: String, timestamp: Long, listenedMs: Long) {
            synchronized(updatedEvents) {
                updatedEvents.add(Triple(trackId, timestamp, listenedMs))
            }
        }

        override fun getPlayEventsForTrack(trackId: String): Flow<List<PlayEvent>> = emptyFlow()
        override fun getAllPlayEvents(): Flow<List<PlayEvent>> = emptyFlow()
        override fun getTotalListenTimeMs(sinceTimestamp: Long, untilTimestamp: Long): Flow<Long> = emptyFlow()
        override fun getCompletedPlayCount(sinceTimestamp: Long, untilTimestamp: Long): Flow<Int> = emptyFlow()
        override fun getPeakHour(sinceTimestamp: Long, untilTimestamp: Long): Flow<Int?> = emptyFlow()
        override fun getTopTracks(sinceTimestamp: Long, limit: Int, untilTimestamp: Long, sortBy: StatsSortBy): Flow<List<TrackStat>> = emptyFlow()
        override fun getTopArtists(sinceTimestamp: Long, limit: Int, untilTimestamp: Long, sortBy: StatsSortBy): Flow<List<ArtistStat>> = emptyFlow()
        override fun getTopAlbums(sinceTimestamp: Long, limit: Int, untilTimestamp: Long, sortBy: StatsSortBy): Flow<List<AlbumStat>> = emptyFlow()
        override fun getRecentlyPlayedTracks(limit: Int): Flow<List<Track>> = emptyFlow()
        override fun getMostPlayedTracks(limit: Int): Flow<List<Track>> = emptyFlow()
    }
}
