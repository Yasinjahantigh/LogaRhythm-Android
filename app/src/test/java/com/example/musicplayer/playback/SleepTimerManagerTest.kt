package com.example.musicplayer.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepTimerManagerTest {

    @Test
    fun sleepTimer_startsAndCancelsCleanly() = runTest {
        val manager = SleepTimerManager()
        assertNull(manager.remainingMs.value)
        assertFalse(manager.isEndOfTrack.value)

        manager.startTimer(15)
        assertNotNull(manager.remainingMs.value)
        assertTrue(manager.remainingMs.value!! > 0L)
        assertFalse(manager.isEndOfTrack.value)

        manager.cancelTimer()
        assertNull(manager.remainingMs.value)
        assertFalse(manager.isEndOfTrack.value)
    }

    @Test
    fun sleepTimer_startEndOfTrackSetsFlag() = runTest {
        val manager = SleepTimerManager()
        manager.startEndOfTrack()

        assertTrue(manager.isEndOfTrack.value)
        assertEquals(0L, manager.remainingMs.value)

        manager.cancelTimer()
        assertFalse(manager.isEndOfTrack.value)
        assertNull(manager.remainingMs.value)
    }
}
