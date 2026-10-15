package com.example.musicplayer.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SavedQueueState(
    val trackIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long
)

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {
    private val keyDarkTheme = booleanPreferencesKey("key_dark_theme")
    private val keyAppLanguage = stringPreferencesKey("key_app_language")
    private val keyThemeMode = stringPreferencesKey("key_theme_mode")
    private val keyColorSource = stringPreferencesKey("key_color_source")
    private val keyCornerRoundness = stringPreferencesKey("key_corner_roundness")
    private val keyCompactMiniPlayer = booleanPreferencesKey("key_compact_mini_player")
    private val keyMinTrackLengthSec = intPreferencesKey("key_min_track_length_sec")
    private val keyExcludedFolders = stringPreferencesKey("key_excluded_folders")
    private val keyHiddenTracks = stringPreferencesKey("key_hidden_track_ids")
    private val keyRecentSearches = stringPreferencesKey("key_recent_searches")
    private val keyQueueTrackIds = stringPreferencesKey("key_queue_track_ids")
    private val keyQueueCurrentIndex = intPreferencesKey("key_queue_current_index")
    private val keyQueuePositionMs = longPreferencesKey("key_queue_position_ms")
    private val keyLrclibEnabled = booleanPreferencesKey("key_lrclib_enabled")
    private val keyEqualizerEnabled = booleanPreferencesKey("key_equalizer_enabled")
    private val keyEqualizerPreset = intPreferencesKey("key_equalizer_preset")
    private val keyEqualizerBandLevels = stringPreferencesKey("key_equalizer_band_levels")
    private val keyBassBoostStrength = intPreferencesKey("key_bass_boost_strength")
    private val keyVirtualizerStrength = intPreferencesKey("key_virtualizer_strength")


    /** App locale override: "system", "en", or "fa". */
    val appLanguage: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyAppLanguage] ?: "system"
    }

    suspend fun setAppLanguage(language: String) {
        val normalized = language.takeIf { it in setOf("system", "en", "fa") } ?: "system"
        // attachBaseContext runs before DataStore/Hilt are ready, so keep this small bootstrap
        // preference in sync with DataStore. The activity is recreated only after this method returns.
        val bootstrap = context.getSharedPreferences("music_settings", Context.MODE_PRIVATE)
        val previousBootstrap = bootstrap.getString("app_locale_override", null)
        try {
            withContext(Dispatchers.IO) {
                check(bootstrap.edit().putString("app_locale_override", normalized).commit()) {
                    "Could not persist the startup language preference"
                }
            }
            dataStore.edit { preferences -> preferences[keyAppLanguage] = normalized }
        } catch (cancelled: CancellationException) {
            rollbackBootstrapLanguage(bootstrap, previousBootstrap)
            throw cancelled
        } catch (failure: Exception) {
            rollbackBootstrapLanguage(bootstrap, previousBootstrap)
            throw failure
        }
    }

    private suspend fun rollbackBootstrapLanguage(
        bootstrap: android.content.SharedPreferences,
        previousLanguage: String?
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            val editor = bootstrap.edit()
            if (previousLanguage == null) editor.remove("app_locale_override")
            else editor.putString("app_locale_override", previousLanguage)
            if (!editor.commit()) {
                Log.e("PreferencesManager", "Could not roll back the startup language preference")
            }
        }
    }

    val themeMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyThemeMode] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[keyThemeMode] = mode
            preferences[keyDarkTheme] = when (mode) {
                "dark" -> true
                "light" -> false
                else -> false
            }
        }
    }

    val isDarkTheme: Flow<Boolean?> = dataStore.data.map { preferences ->
        preferences[keyDarkTheme]
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        setThemeMode(if (isDark) "dark" else "light")
    }

    val colorSource: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyColorSource] ?: "album_art"
    }

    suspend fun setColorSource(source: String) {
        dataStore.edit { preferences ->
            preferences[keyColorSource] = source
        }
    }

    val cornerRoundness: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyCornerRoundness] ?: "standard"
    }

    suspend fun setCornerRoundness(roundness: String) {
        dataStore.edit { preferences ->
            preferences[keyCornerRoundness] = roundness
        }
    }

    val isCompactMiniPlayer: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyCompactMiniPlayer] ?: false
    }

    suspend fun setCompactMiniPlayer(compact: Boolean) {
        dataStore.edit { preferences ->
            preferences[keyCompactMiniPlayer] = compact
        }
    }

    val minTrackLengthSec: Flow<Int> = dataStore.data.map { preferences ->
        preferences[keyMinTrackLengthSec] ?: 15
    }

    suspend fun setMinTrackLengthSec(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[keyMinTrackLengthSec] = seconds
        }
    }

    val excludedFolders: Flow<Set<String>> = dataStore.data.map { preferences ->
        val raw = preferences[keyExcludedFolders]
        if (raw == null) {
            com.example.musicplayer.data.sources.LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS
        } else if (raw.isBlank()) {
            emptySet()
        } else {
            raw.split("\n").filter { it.isNotBlank() }.toSet()
        }
    }

    suspend fun setExcludedFolders(folders: Set<String>) {
        dataStore.edit { preferences ->
            preferences[keyExcludedFolders] = folders.joinToString("\n")
        }
    }

    suspend fun resetExcludedFoldersToDefault() {
        dataStore.edit { preferences ->
            preferences.remove(keyExcludedFolders)
        }
    }

    suspend fun clearAllExcludedFolders() {
        dataStore.edit { preferences ->
            preferences[keyExcludedFolders] = ""
        }
    }

    suspend fun excludeFolder(folder: String) {
        dataStore.edit { preferences ->
            val raw = preferences[keyExcludedFolders]
            val current = if (raw == null) {
                com.example.musicplayer.data.sources.LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS.toMutableSet()
            } else if (raw.isBlank()) {
                mutableSetOf()
            } else {
                raw.split("\n").filter { it.isNotBlank() }.toMutableSet()
            }
            current.add(folder)
            preferences[keyExcludedFolders] = current.joinToString("\n")
        }
    }

    suspend fun toggleExcludedFolder(folder: String) {
        dataStore.edit { preferences ->
            val raw = preferences[keyExcludedFolders]
            val current = if (raw == null) {
                com.example.musicplayer.data.sources.LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS.toMutableSet()
            } else if (raw.isBlank()) {
                mutableSetOf()
            } else {
                raw.split("\n").filter { it.isNotBlank() }.toMutableSet()
            }
            if (current.contains(folder)) {
                current.remove(folder)
            } else {
                current.add(folder)
            }
            preferences[keyExcludedFolders] = current.joinToString("\n")
        }
    }

    // ---- Hidden Tracks ----------------------------------------------------------------------

    val hiddenTrackIds: Flow<Set<String>> = dataStore.data.map { preferences ->
        val raw = preferences[keyHiddenTracks].orEmpty()
        if (raw.isBlank()) emptySet()
        else raw.split("\n").filter { it.isNotBlank() }.toSet()
    }

    suspend fun hideTrack(trackId: String) {
        dataStore.edit { preferences ->
            val current = preferences[keyHiddenTracks].orEmpty()
                .split("\n").filter { it.isNotBlank() }.toMutableSet()
            current.add(trackId)
            preferences[keyHiddenTracks] = current.joinToString("\n")
        }
    }

    suspend fun unhideTrack(trackId: String) {
        dataStore.edit { preferences ->
            val current = preferences[keyHiddenTracks].orEmpty()
                .split("\n").filter { it.isNotBlank() }.toMutableSet()
            current.remove(trackId)
            preferences[keyHiddenTracks] = current.joinToString("\n")
        }
    }

    suspend fun unhideAllTracks() {
        dataStore.edit { preferences ->
            preferences.remove(keyHiddenTracks)
        }
    }

    val isLrclibEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyLrclibEnabled] ?: true
    }

    suspend fun setLrclibEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[keyLrclibEnabled] = enabled
        }
    }

    val recentSearches: Flow<List<String>> = dataStore.data.map { preferences ->
        val raw = preferences[keyRecentSearches].orEmpty()
        if (raw.isBlank()) emptyList()
        else raw.split("\n").filter { it.isNotBlank() }
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val current = preferences[keyRecentSearches].orEmpty()
                .split("\n")
                .filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
            val updated = (listOf(trimmed) + current).take(10)
            preferences[keyRecentSearches] = updated.joinToString("\n")
        }
    }

    suspend fun removeRecentSearch(query: String) {
        val trimmed = query.trim()
        dataStore.edit { preferences ->
            val current = preferences[keyRecentSearches].orEmpty()
                .split("\n")
                .filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
            preferences[keyRecentSearches] = current.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { preferences ->
            preferences.remove(keyRecentSearches)
        }
    }

    val savedQueue: Flow<SavedQueueState> = dataStore.data.map { preferences ->
        val raw = preferences[keyQueueTrackIds].orEmpty()
        val ids = if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
        val index = preferences[keyQueueCurrentIndex] ?: 0
        val pos = preferences[keyQueuePositionMs] ?: 0L
        SavedQueueState(ids, index, pos)
    }

    suspend fun saveQueue(trackIds: List<String>, currentIndex: Int, positionMs: Long) {
        dataStore.edit { preferences ->
            preferences[keyQueueTrackIds] = trackIds.joinToString("\n")
            preferences[keyQueueCurrentIndex] = currentIndex
            preferences[keyQueuePositionMs] = positionMs
        }
    }

    val isEqualizerEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyEqualizerEnabled] ?: false
    }

    val equalizerPreset: Flow<Int> = dataStore.data.map { preferences ->
        preferences[keyEqualizerPreset] ?: -1
    }

    val equalizerBandLevels: Flow<Map<Int, Int>> = dataStore.data.map { preferences ->
        val raw = preferences[keyEqualizerBandLevels].orEmpty()
        if (raw.isBlank()) emptyMap()
        else {
            raw.split(";").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val band = parts[0].toIntOrNull()
                    val level = parts[1].toIntOrNull()
                    if (band != null && level != null) band to level else null
                } else null
            }.toMap()
        }
    }

    val bassBoostStrength: Flow<Int> = dataStore.data.map { preferences ->
        preferences[keyBassBoostStrength] ?: 0
    }

    val virtualizerStrength: Flow<Int> = dataStore.data.map { preferences ->
        preferences[keyVirtualizerStrength] ?: 0
    }

    suspend fun saveEqualizerSettings(
        enabled: Boolean,
        preset: Int,
        bandLevels: Map<Int, Int>,
        bassBoost: Int = 0,
        virtualizer: Int = 0
    ) {
        dataStore.edit { preferences ->
            preferences[keyEqualizerEnabled] = enabled
            preferences[keyEqualizerPreset] = preset
            preferences[keyEqualizerBandLevels] = bandLevels.entries.joinToString(";") { "${it.key}:${it.value}" }
            preferences[keyBassBoostStrength] = bassBoost
            preferences[keyVirtualizerStrength] = virtualizer
        }
    }

    private val keyOnboardingCompleted = booleanPreferencesKey("key_onboarding_completed")

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyOnboardingCompleted] ?: readOnboardingBootstrap()
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        dataStore.edit { preferences ->
            preferences[keyOnboardingCompleted] = completed
        }
        writeOnboardingBootstrap(completed)
    }

    /**
     * Synchronous SharedPreferences mirror so the first frame never guesses.
     * DataStore reads are disk-async; without this the UI must start with `false` and flashes the
     * onboarding menu for a few hundred ms on slow phones. The bootstrap value is only a starting
     * point — the DataStore flow above remains the source of truth afterwards.
     */
    private fun readOnboardingBootstrap(): Boolean =
        runCatching {
            context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
                .getBoolean(BOOTSTRAP_ONBOARDING_KEY, false)
        }.getOrDefault(false)

    private fun writeOnboardingBootstrap(completed: Boolean) {
        runCatching {
            context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(BOOTSTRAP_ONBOARDING_KEY, completed).apply()
        }
    }

    companion object {
        const val BOOTSTRAP_PREFS = "music_settings"
        const val BOOTSTRAP_ONBOARDING_KEY = "onboarding_completed_bootstrap"
    }

    // ---- Playback quality -------------------------------------------------------------------

    private val keyFadeSec = intPreferencesKey("key_fade_between_songs_sec")
    private val keySkipSilence = booleanPreferencesKey("key_skip_silence")
    private val keyReplayGain = stringPreferencesKey("key_replay_gain_mode")
    private val keyReplayGainPreamp = floatPreferencesKey("key_replay_gain_preamp_db")
    private val keyResumeOnHeadset = booleanPreferencesKey("key_resume_on_headset")

    /** 0 = off. The player fades out/in around each song change (single player, no overlap). */
    val fadeBetweenSongsSec: Flow<Int> = dataStore.data.map { it[keyFadeSec] ?: 0 }
    val skipSilence: Flow<Boolean> = dataStore.data.map { it[keySkipSilence] ?: false }
    /** "off" | "track" | "album". */
    val replayGainMode: Flow<String> = dataStore.data.map { it[keyReplayGain] ?: "off" }
    /** Manual pre-amplifier added on top of the picked ReplayGain tag, in dB [-6, +6]. */
    val replayGainPreampDb: Flow<Float> = dataStore.data.map { it[keyReplayGainPreamp] ?: 0f }
    val resumeOnHeadset: Flow<Boolean> = dataStore.data.map { it[keyResumeOnHeadset] ?: true }

    suspend fun setFadeBetweenSongsSec(seconds: Int) { dataStore.edit { it[keyFadeSec] = seconds.coerceIn(0, 12) } }
    suspend fun setSkipSilence(enabled: Boolean) { dataStore.edit { it[keySkipSilence] = enabled } }
    suspend fun setReplayGainMode(mode: String) { dataStore.edit { it[keyReplayGain] = mode } }
    suspend fun setReplayGainPreampDb(db: Float) { dataStore.edit { it[keyReplayGainPreamp] = db.coerceIn(-6f, 6f) } }
    suspend fun setResumeOnHeadset(enabled: Boolean) { dataStore.edit { it[keyResumeOnHeadset] = enabled } }

    // ---- Library intelligence ---------------------------------------------------------------

    private val keySplitArtists = booleanPreferencesKey("key_split_artists")
    private val keyArtistSeparators = stringPreferencesKey("key_artist_separators")
    private val keyLibraryGeneration = stringPreferencesKey("key_library_generation")

    val splitArtists: Flow<Boolean> = dataStore.data.map { it[keySplitArtists] ?: true }
    /** One separator per line; null means "use the built-in defaults". */
    val artistSeparators: Flow<String?> = dataStore.data.map { it[keyArtistSeparators] }
    /** MediaStore version+generation seen by the last successful scan; lets a scan be skipped. */
    val libraryGeneration: Flow<String?> = dataStore.data.map { it[keyLibraryGeneration] }

    suspend fun setSplitArtists(enabled: Boolean) { dataStore.edit { it[keySplitArtists] = enabled } }
    suspend fun setArtistSeparators(raw: String?) {
        dataStore.edit { if (raw == null) it.remove(keyArtistSeparators) else it[keyArtistSeparators] = raw }
    }
    suspend fun setLibraryGeneration(value: String?) {
        dataStore.edit { if (value == null) it.remove(keyLibraryGeneration) else it[keyLibraryGeneration] = value }
    }
}
