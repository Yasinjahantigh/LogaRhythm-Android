package com.example.musicplayer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SavedQueueState(
    val trackIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long
)

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keyDarkTheme = booleanPreferencesKey("key_dark_theme")
    private val keyThemeMode = stringPreferencesKey("key_theme_mode")
    private val keyColorSource = stringPreferencesKey("key_color_source")
    private val keyCornerRoundness = stringPreferencesKey("key_corner_roundness")
    private val keyCompactMiniPlayer = booleanPreferencesKey("key_compact_mini_player")
    private val keyMinTrackLengthSec = intPreferencesKey("key_min_track_length_sec")
    private val keyExcludedFolders = stringPreferencesKey("key_excluded_folders")
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
        preferences[keyColorSource] ?: "wallpaper"
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
        preferences[keyMinTrackLengthSec] ?: 60
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

    suspend fun toggleExcludedFolder(folder: String) {
        dataStore.edit { preferences ->
            val current = preferences[keyExcludedFolders].orEmpty()
                .split("\n").filter { it.isNotBlank() }.toMutableSet()
            if (current.contains(folder)) {
                current.remove(folder)
            } else {
                current.add(folder)
            }
            preferences[keyExcludedFolders] = current.joinToString("\n")
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
        preferences[keyOnboardingCompleted] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        dataStore.edit { preferences ->
            preferences[keyOnboardingCompleted] = completed
        }
    }
}
