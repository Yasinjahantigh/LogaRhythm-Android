package com.yasinjahantigh.logarhythm.data.datastore

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
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
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

/** Raw AI settings; the API key is kept separately in the encrypted [com.yasinjahantigh.logarhythm.data.ai.AiKeyStore]. */
data class AiPreferences(
    val provider: String?,
    val baseUrl: String?,
    val model: String?
)

/** Every online lyrics source and the optional user-owned endpoint. */
data class LyricsSourcePreferences(
    val lrclib: Boolean = true,
    val netease: Boolean = false,
    val kugou: Boolean = false,
    val lyricsOvh: Boolean = true,
    val genius: Boolean = true,
    val custom: Boolean = false,
    val customUrl: String? = null
) {
    val customUsable: Boolean
        get() = custom && !customUrl.isNullOrBlank() && customUrl.startsWith("https://")

    val anyEnabled: Boolean
        get() = lrclib || netease || kugou || lyricsOvh || genius || customUsable
}

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {
    private val keyDarkTheme = booleanPreferencesKey("key_dark_theme")
    private val keyAppLanguage = stringPreferencesKey("key_app_language")
    private val keyThemeMode = stringPreferencesKey("key_theme_mode")
    private val keyColorSource = stringPreferencesKey("key_color_source")
    private val keyThemeStyle = stringPreferencesKey("key_theme_style")
    private val keyCustomAccent = longPreferencesKey("key_custom_accent")
    private val keyCustomBase = stringPreferencesKey("key_custom_base")
    private val keyCornerRoundness = stringPreferencesKey("key_corner_roundness")
    private val keyCompactMiniPlayer = booleanPreferencesKey("key_compact_mini_player")
    private val keyMinTrackLengthSec = intPreferencesKey("key_min_track_length_sec")
    private val keyExcludedFolders = stringPreferencesKey("key_excluded_folders")
    private val keyShowSystemAudio = booleanPreferencesKey("key_show_system_audio")
    private val keyHiddenTracks = stringPreferencesKey("key_hidden_track_ids")
    private val keyRecentSearches = stringPreferencesKey("key_recent_searches")
    private val keyQueueTrackIds = stringPreferencesKey("key_queue_track_ids")
    private val keyQueueCurrentIndex = intPreferencesKey("key_queue_current_index")
    private val keyQueuePositionMs = longPreferencesKey("key_queue_position_ms")
    private val keyLrclibEnabled = booleanPreferencesKey("key_lrclib_enabled")
    private val keyGeniusEnabled = booleanPreferencesKey("key_genius_enabled")
    private val keyNeteaseEnabled = booleanPreferencesKey("key_netease_enabled")
    private val keyKugouEnabled = booleanPreferencesKey("key_kugou_enabled")
    private val keyLyricsOvhEnabled = booleanPreferencesKey("key_lyrics_ovh_enabled")
    private val keyCustomLyricsEnabled = booleanPreferencesKey("key_custom_lyrics_enabled")
    private val keyCustomLyricsUrl = stringPreferencesKey("key_custom_lyrics_url")
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

    val colorSource: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyColorSource] ?: "album_art"
    }

    suspend fun setColorSource(source: String) {
        dataStore.edit { preferences ->
            preferences[keyColorSource] = source
        }
    }

    /**
     * default | oled | album_art | wallpaper | custom.
     * Older builds stored only a color source; map it on first read so nobody loses their pick.
     */
    val themeStyle: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyThemeStyle] ?: when (preferences[keyColorSource]) {
            "wallpaper" -> "wallpaper"
            "album_art" -> "album_art"
            else -> "default"
        }
    }

    val customAccentColor: Flow<Long> = dataStore.data.map { preferences ->
        preferences[keyCustomAccent] ?: 0xFFF4B266
    }

    /** dark | light | oled. */
    val customBase: Flow<String> = dataStore.data.map { preferences ->
        preferences[keyCustomBase] ?: "dark"
    }

    suspend fun setThemeStyle(style: String) {
        val normalized = style.takeIf { it in setOf("default", "oled", "album_art", "wallpaper", "custom") }
            ?: "default"
        dataStore.edit { preferences -> preferences[keyThemeStyle] = normalized }
    }

    suspend fun setCustomAccentColor(argb: Long) {
        dataStore.edit { preferences -> preferences[keyCustomAccent] = argb }
    }

    suspend fun setCustomBase(base: String) {
        val normalized = base.takeIf { it in setOf("dark", "light", "oled") } ?: "dark"
        dataStore.edit { preferences -> preferences[keyCustomBase] = normalized }
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
            com.yasinjahantigh.logarhythm.data.sources.LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS
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
                com.yasinjahantigh.logarhythm.data.sources.LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS.toMutableSet()
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
                com.yasinjahantigh.logarhythm.data.sources.LocalMusicSource.DEFAULT_EXCLUDED_FOLDERS.toMutableSet()
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

    /** System sounds and recordings stay out of the library unless the user opts in. */
    val showSystemAudio: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyShowSystemAudio] ?: false
    }

    suspend fun setShowSystemAudio(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[keyShowSystemAudio] = show
        }
    }

    /**
     * Folders the user explicitly excluded. Unlike [excludedFolders] this does not fall back to
     * the built-in list, so turning on system audio can drop those defaults without losing custom
     * exclusions the user made on purpose.
     */
    val customExcludedFolders: Flow<Set<String>> = dataStore.data.map { preferences ->
        val raw = preferences[keyExcludedFolders]
        if (raw.isNullOrBlank()) emptySet() else raw.split("\n").filter { it.isNotBlank() }.toSet()
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

    val isGeniusEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[keyGeniusEnabled] ?: true
    }

    suspend fun setGeniusEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[keyGeniusEnabled] = enabled
        }
    }

    val lyricsSourcePreferences: Flow<LyricsSourcePreferences> = dataStore.data.map { preferences ->
        LyricsSourcePreferences(
            lrclib = preferences[keyLrclibEnabled] ?: true,
            netease = preferences[keyNeteaseEnabled] ?: false,
            kugou = preferences[keyKugouEnabled] ?: false,
            lyricsOvh = preferences[keyLyricsOvhEnabled] ?: true,
            genius = preferences[keyGeniusEnabled] ?: true,
            custom = preferences[keyCustomLyricsEnabled] ?: false,
            customUrl = preferences[keyCustomLyricsUrl]?.takeIf { it.isNotBlank() }
        )
    }

    val anyLyricsSourceEnabled: Flow<Boolean> = lyricsSourcePreferences.map { it.anyEnabled }

    suspend fun lyricsSourcePreferencesNow(): LyricsSourcePreferences = lyricsSourcePreferences.first()

    suspend fun setNeteaseEnabled(enabled: Boolean) {
        dataStore.edit { it[keyNeteaseEnabled] = enabled }
    }

    suspend fun setKugouEnabled(enabled: Boolean) {
        dataStore.edit { it[keyKugouEnabled] = enabled }
    }

    suspend fun setLyricsOvhEnabled(enabled: Boolean) {
        dataStore.edit { it[keyLyricsOvhEnabled] = enabled }
    }

    suspend fun setCustomLyricsEnabled(enabled: Boolean) {
        dataStore.edit { it[keyCustomLyricsEnabled] = enabled }
    }

    suspend fun setCustomLyricsUrl(url: String?) {
        dataStore.edit { preferences ->
            val clean = url?.trim()?.takeIf { it.startsWith("https://") }
            if (clean == null) preferences.remove(keyCustomLyricsUrl)
            else preferences[keyCustomLyricsUrl] = clean
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
    private val keySwipeRightForNext = booleanPreferencesKey("key_swipe_right_for_next")

    /** 0 = off. The player fades out/in around each song change (single player, no overlap). */
    val fadeBetweenSongsSec: Flow<Int> = dataStore.data.map { it[keyFadeSec] ?: 0 }
    val skipSilence: Flow<Boolean> = dataStore.data.map { it[keySkipSilence] ?: false }
    /** "off" | "track" | "album". */
    val replayGainMode: Flow<String> = dataStore.data.map { it[keyReplayGain] ?: "off" }
    /** Manual pre-amplifier added on top of the picked ReplayGain tag, in dB [-6, +6]. */
    val replayGainPreampDb: Flow<Float> = dataStore.data.map { it[keyReplayGainPreamp] ?: 0f }
    val resumeOnHeadset: Flow<Boolean> = dataStore.data.map { it[keyResumeOnHeadset] ?: true }
    /** RTL-friendly default: swipe right skips forward, swipe left goes back. */
    val swipeRightForNext: Flow<Boolean> = dataStore.data.map { it[keySwipeRightForNext] ?: true }

    suspend fun setFadeBetweenSongsSec(seconds: Int) { dataStore.edit { it[keyFadeSec] = seconds.coerceIn(0, 12) } }
    suspend fun setSkipSilence(enabled: Boolean) { dataStore.edit { it[keySkipSilence] = enabled } }
    suspend fun setReplayGainMode(mode: String) { dataStore.edit { it[keyReplayGain] = mode } }
    suspend fun setReplayGainPreampDb(db: Float) { dataStore.edit { it[keyReplayGainPreamp] = db.coerceIn(-6f, 6f) } }
    suspend fun setResumeOnHeadset(enabled: Boolean) { dataStore.edit { it[keyResumeOnHeadset] = enabled } }
    suspend fun setSwipeRightForNext(enabled: Boolean) { dataStore.edit { it[keySwipeRightForNext] = enabled } }

    // ---- Library intelligence ---------------------------------------------------------------

    private val keySplitArtists = booleanPreferencesKey("key_split_artists")
    private val keyArtistSeparators = stringPreferencesKey("key_artist_separators")
    private val keyArtistNeverSplit = stringPreferencesKey("key_artist_never_split")
    private val keyLibraryGeneration = stringPreferencesKey("key_library_generation")

    val splitArtists: Flow<Boolean> = dataStore.data.map { it[keySplitArtists] ?: true }
    /** One separator per line; null means "use the built-in defaults". */
    val artistSeparators: Flow<String?> = dataStore.data.map { it[keyArtistSeparators] }
    /** Artist names the user never wants split, one per line. */
    val artistNeverSplit: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[keyArtistNeverSplit].orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
    /** MediaStore version+generation seen by the last successful scan; lets a scan be skipped. */
    val libraryGeneration: Flow<String?> = dataStore.data.map { it[keyLibraryGeneration] }

    suspend fun setSplitArtists(enabled: Boolean) { dataStore.edit { it[keySplitArtists] = enabled } }
    suspend fun setArtistSeparators(raw: String?) {
        dataStore.edit { if (raw == null) it.remove(keyArtistSeparators) else it[keyArtistSeparators] = raw }
    }
    suspend fun setArtistNeverSplit(names: Set<String>) {
        dataStore.edit { preferences ->
            val clean = names.map { it.trim() }.filter { it.isNotBlank() }
            if (clean.isEmpty()) preferences.remove(keyArtistNeverSplit)
            else preferences[keyArtistNeverSplit] = clean.joinToString("\n")
        }
    }
    suspend fun setLibraryGeneration(value: String?) {
        dataStore.edit { if (value == null) it.remove(keyLibraryGeneration) else it[keyLibraryGeneration] = value }
    }

    // ---- Library sorting --------------------------------------------------------------------

    private fun librarySortKey(tab: String) = stringPreferencesKey("key_library_sort_${tab.lowercase()}")
    private fun librarySortMirrorKey(tab: String) = "library_sort_${tab.lowercase()}"

    /**
     * Synchronous copy of the last chosen sort. DataStore writes are asynchronous and can be lost
     * when the process is killed right after the user taps a sort option, which made the library
     * reopen on Title/Ascending.
     */
    fun librarySortOrderCached(tab: String): Pair<String, String> =
        librarySortRawCached(tab).toSortPair()

    private fun librarySortRawCached(tab: String): String? = runCatching {
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
            .getString(librarySortMirrorKey(tab), null)
    }.getOrNull()

    /** Persisted field/direction pair for one library tab, e.g. ("YEAR", "DESCENDING"). */
    fun librarySortOrder(tab: String): Flow<Pair<String, String>> = dataStore.data.map { preferences ->
        (librarySortRawCached(tab) ?: preferences[librarySortKey(tab)]).toSortPair()
    }

    @android.annotation.SuppressLint("ApplySharedPref")
    suspend fun setLibrarySortOrder(tab: String, field: String, direction: String) {
        val value = "$field|$direction"
        withContext(NonCancellable + Dispatchers.IO) {
            context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(librarySortMirrorKey(tab), value)
                .commit()
            dataStore.edit { it[librarySortKey(tab)] = value }
        }
    }

    private fun String?.toSortPair(): Pair<String, String> {
        val field = this?.substringBefore('|')?.takeIf { it.isNotBlank() } ?: "TITLE"
        val direction = this?.substringAfter('|', "")?.takeIf { it.isNotBlank() } ?: "ASCENDING"
        return field to direction
    }

    // ---- Music download ---------------------------------------------------------------------

    private val keyDownloadRecentSearches = stringPreferencesKey("key_download_recent_searches")
    private val keyYtdlpFirstUpdateDone = booleanPreferencesKey("key_ytdlp_first_update_done")
    private val keyDownloadSource = stringPreferencesKey("key_download_source")

    val downloadRecentSearches: Flow<List<String>> = dataStore.data.map { preferences ->
        val raw = preferences[keyDownloadRecentSearches].orEmpty()
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    suspend fun addDownloadRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val current = preferences[keyDownloadRecentSearches].orEmpty()
                .split("\n")
                .filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
            preferences[keyDownloadRecentSearches] =
                (listOf(trimmed) + current).take(10).joinToString("\n")
        }
    }

    suspend fun removeDownloadRecentSearch(query: String) {
        val trimmed = query.trim()
        dataStore.edit { preferences ->
            val current = preferences[keyDownloadRecentSearches].orEmpty()
                .split("\n")
                .filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
            preferences[keyDownloadRecentSearches] = current.joinToString("\n")
        }
    }

    suspend fun clearDownloadRecentSearches() {
        dataStore.edit { it.remove(keyDownloadRecentSearches) }
    }

    val ytdlpFirstUpdateDone: Flow<Boolean> = dataStore.data.map { it[keyYtdlpFirstUpdateDone] ?: false }

    suspend fun setYtdlpFirstUpdateDone(done: Boolean) {
        dataStore.edit { it[keyYtdlpFirstUpdateDone] = done }
    }

    /** null means search every source. */
    val downloadSourceFilter: Flow<DownloadSource?> = dataStore.data.map { preferences ->
        preferences[keyDownloadSource]?.let { raw ->
            DownloadSource.entries.firstOrNull { it.raw == raw }
        }
    }

    suspend fun setDownloadSourceFilter(source: DownloadSource?) {
        dataStore.edit { preferences ->
            if (source == null) preferences.remove(keyDownloadSource)
            else preferences[keyDownloadSource] = source.raw
        }
    }

    // ---- Optional bring-your-own-key AI -----------------------------------------------------

    private val keyAiProvider = stringPreferencesKey("key_ai_provider")
    private val keyAiBaseUrl = stringPreferencesKey("key_ai_base_url")
    private val keyAiModel = stringPreferencesKey("key_ai_model")

    val aiPreferences: Flow<AiPreferences> = dataStore.data.map { preferences ->
        AiPreferences(
            provider = preferences[keyAiProvider],
            baseUrl = preferences[keyAiBaseUrl],
            model = preferences[keyAiModel]
        )
    }

    suspend fun setAiPreferences(provider: String, baseUrl: String, model: String) {
        dataStore.edit { preferences ->
            preferences[keyAiProvider] = provider
            preferences[keyAiBaseUrl] = baseUrl
            preferences[keyAiModel] = model
        }
    }
}
