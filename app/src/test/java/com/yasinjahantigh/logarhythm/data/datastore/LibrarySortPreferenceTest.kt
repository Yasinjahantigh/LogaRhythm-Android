package com.yasinjahantigh.logarhythm.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySortPreferenceTest {

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var manager: PreferencesManager
    private lateinit var tab: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(context.cacheDir, "sort-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        manager = PreferencesManager(dataStore, context)
        tab = "TEST_${UUID.randomUUID().toString().take(8)}"
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun sortOrder_isReadableSynchronouslyRightAfterSaving() = runBlocking {
        manager.setLibrarySortOrder(tab, "YEAR", "DESCENDING")

        assertEquals("YEAR" to "DESCENDING", manager.librarySortOrderCached(tab))
    }

    @Test
    fun sortOrder_flowPrefersTheMirrorWhenDataStoreLosesTheKey() = runBlocking {
        manager.setLibrarySortOrder(tab, "ARTIST", "DESCENDING")
        dataStore.edit { it.remove(stringPreferencesKey("key_library_sort_${tab.lowercase()}")) }

        assertEquals("ARTIST" to "DESCENDING", manager.librarySortOrder(tab).first())
    }

    @Test
    fun sortOrder_isKeptPerTab() = runBlocking {
        manager.setLibrarySortOrder("$tab-SONGS", "YEAR", "ASCENDING")
        manager.setLibrarySortOrder("$tab-ALBUMS", "TRACK_COUNT", "DESCENDING")

        assertEquals("YEAR" to "ASCENDING", manager.librarySortOrderCached("$tab-SONGS"))
        assertEquals("TRACK_COUNT" to "DESCENDING", manager.librarySortOrderCached("$tab-ALBUMS"))
    }
}
