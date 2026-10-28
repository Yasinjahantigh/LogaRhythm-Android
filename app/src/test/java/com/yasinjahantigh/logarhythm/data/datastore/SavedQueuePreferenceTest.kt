package com.yasinjahantigh.logarhythm.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedQueuePreferenceTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.cacheDir, "queue-${UUID.randomUUID()}.preferences_pb")
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val manager = PreferencesManager(store, context)

    @After fun cleanup() { scope.cancel(); file.delete() }

    @Test fun savesBothOrdersAndFlagTogether() = runBlocking {
        manager.saveQueue(listOf("b", "a", "a"), 1, 1234L, true, listOf("a", "b", "a"))
        val restored = PreferencesManager(store, context).savedQueue.first()
        assertEquals(listOf("b", "a", "a"), restored.trackIds)
        assertEquals(listOf("a", "b", "a"), restored.originalTrackIds)
        assertTrue(restored.shuffle)
        assertEquals(1, restored.currentIndex)
        assertEquals(1234L, restored.positionMs)
    }

    @Test fun legacyQueueDefaultsToSavedOrderAndShuffleOff() = runBlocking {
        store.edit { it[stringPreferencesKey("key_queue_track_ids")] = "b\na" }
        val saved = manager.savedQueue.first()
        assertEquals(saved.trackIds, saved.originalTrackIds)
        assertFalse(saved.shuffle)
    }
}
