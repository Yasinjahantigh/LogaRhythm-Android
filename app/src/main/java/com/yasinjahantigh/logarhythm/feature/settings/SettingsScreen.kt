package com.yasinjahantigh.logarhythm.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToStats: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToLibraryTools: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToDownload: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
    val cornerRoundness by viewModel.cornerRoundness.collectAsStateWithLifecycle()
    val isCompactMiniPlayer by viewModel.isCompactMiniPlayer.collectAsStateWithLifecycle()
    val minTrackLengthSec by viewModel.minTrackLengthSec.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val showSystemAudio by viewModel.showSystemAudio.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val isLrclibEnabled by viewModel.isLrclibEnabled.collectAsStateWithLifecycle()
    val isGeniusEnabled by viewModel.isGeniusEnabled.collectAsStateWithLifecycle()
    val fadeSec by viewModel.fadeSec.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val replayGainMode by viewModel.replayGainMode.collectAsStateWithLifecycle()
    val replayGainPreampDb by viewModel.replayGainPreampDb.collectAsStateWithLifecycle()
    val resumeOnHeadset by viewModel.resumeOnHeadset.collectAsStateWithLifecycle()
    val splitArtists by viewModel.splitArtists.collectAsStateWithLifecycle()
    val artistSeparators by viewModel.artistSeparators.collectAsStateWithLifecycle()
    val hiddenLyrics by viewModel.hiddenLyricsTracks.collectAsStateWithLifecycle()
    val hiddenTracks by viewModel.hiddenTracks.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, context.getString(it.res, *it.args.toTypedArray()), Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::backupTo) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::restoreFrom) }
    val exportPlaylistsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::exportPlaylists) }
    val importPlaylistsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importPlaylists(uris) }

    var showFadeDialog by remember { mutableStateOf(false) }
    var showReplayGainDialog by remember { mutableStateOf(false) }
    var showPreampDialog by remember { mutableStateOf(false) }
    var showSeparatorsDialog by remember { mutableStateOf(false) }
    var showHiddenLyricsDialog by remember { mutableStateOf(false) }
    var showHiddenTracksDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCornerDialog by remember { mutableStateOf(false) }
    var showMinLengthDialog by remember { mutableStateOf(false) }
    var showFoldersDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language_title),
            options = listOf(
                "system" to stringResource(R.string.settings_language_system),
                "en" to stringResource(R.string.settings_language_english),
                "fa" to stringResource(R.string.settings_language_persian)
            ),
            selectedKey = appLanguage,
            onSelectKey = { language ->
                viewModel.setAppLanguage(
                    language = language,
                    onSaved = {
                        // Recreate only after both the bootstrap preference and DataStore commit.
                        context.findActivity()?.recreate()
                    },
                    onFailure = {
                        Toast.makeText(context, context.getString(R.string.settings_language_save_failed), Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme_title),
            options = listOf(
                "system" to stringResource(R.string.settings_theme_system),
                "light" to stringResource(R.string.settings_theme_light),
                "dark" to stringResource(R.string.settings_theme_dark)
            ),
            selectedKey = themeMode,
            onSelectKey = viewModel::setThemeMode,
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showCornerDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_corner_title),
            options = listOf(
                "standard" to stringResource(R.string.settings_corner_standard),
                "soft" to stringResource(R.string.settings_corner_soft),
                "sharp" to stringResource(R.string.settings_corner_sharp)
            ),
            selectedKey = cornerRoundness,
            onSelectKey = viewModel::setCornerRoundness,
            onDismiss = { showCornerDialog = false }
        )
    }

    if (showMinLengthDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_min_track_length_title),
            options = listOf(
                "0" to stringResource(R.string.settings_min_length_0s),
                "15" to stringResource(R.string.settings_min_length_15s),
                "30" to stringResource(R.string.settings_min_length_30s),
                "60" to stringResource(R.string.settings_min_length_60s)
            ),
            selectedKey = minTrackLengthSec.toString(),
            onSelectKey = { viewModel.setMinTrackLengthSec(it.toIntOrNull() ?: 30) },
            onDismiss = { showMinLengthDialog = false }
        )
    }

    if (showFoldersDialog) {
        ExcludedFoldersDialog(
            allFolders = allFolders,
            excludedFolders = excludedFolders,
            onToggleFolder = viewModel::toggleExcludedFolder,
            onResetDefaults = viewModel::resetExcludedFoldersToDefault,
            onClearAll = viewModel::clearAllExcludedFolders,
            onDismiss = { showFoldersDialog = false }
        )
    }

    if (showHiddenTracksDialog) {
        HiddenTracksDialog(
            tracks = hiddenTracks,
            onRestore = { trackId ->
                viewModel.unhideTrack(trackId)
                Toast.makeText(context, context.getString(R.string.track_restored_toast, ""), Toast.LENGTH_SHORT).show()
            },
            onRestoreAll = {
                viewModel.unhideAllTracks()
                Toast.makeText(context, context.getString(R.string.all_tracks_restored_toast), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showHiddenTracksDialog = false }
        )
    }

    if (showFadeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_fade_title),
            options = listOf(0, 2, 4, 6, 8, 10).map { sec ->
                sec.toString() to if (sec == 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_seconds_fmt, sec)
            },
            selectedKey = fadeSec.toString(),
            onSelectKey = { viewModel.setFadeSec(it.toIntOrNull() ?: 0) },
            onDismiss = { showFadeDialog = false }
        )
    }

    if (showReplayGainDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_replaygain_title),
            options = listOf(
                "off" to stringResource(R.string.settings_off),
                "track" to stringResource(R.string.settings_replaygain_track),
                "album" to stringResource(R.string.settings_replaygain_album)
            ),
            selectedKey = replayGainMode,
            onSelectKey = viewModel::setReplayGainMode,
            onDismiss = { showReplayGainDialog = false }
        )
    }

    if (showPreampDialog) {
        val offLabel = stringResource(R.string.settings_off)
        SingleChoiceDialog(
            title = stringResource(R.string.settings_replaygain_preamp_title),
            options = listOf(-6, -4, -2, 0, 2, 4, 6).map { db ->
                db.toFloat().toString() to if (db == 0) offLabel else "$db dB"
            },
            selectedKey = replayGainPreampDb.toInt().toFloat().toString(),
            onSelectKey = { viewModel.setReplayGainPreampDb(it.toFloatOrNull() ?: 0f) },
            onDismiss = { showPreampDialog = false }
        )
    }

    if (showSeparatorsDialog) {
        SeparatorsDialog(
            current = artistSeparators,
            onSave = { viewModel.setArtistSeparators(it) },
            onReset = { viewModel.setArtistSeparators(null) },
            onDismiss = { showSeparatorsDialog = false }
        )
    }

    if (showHiddenLyricsDialog) {
        HiddenLyricsDialog(
            tracks = hiddenLyrics,
            onRestore = viewModel::restoreLyrics,
            onDismiss = { showHiddenLyricsDialog = false }
        )
    }

    val playbackBottomPadding = LocalPlaybackBottomPadding.current

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(bottom = playbackBottomPadding + 32.dp)
            ) {
                item {
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 28.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)
                    )
                }

                // The two destinations people open most are big tiles, not list rows.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsShortcutTile(
                            title = stringResource(R.string.settings_equalizer_title),
                            icon = Icons.Filled.GraphicEq,
                            onClick = onNavigateToEqualizer,
                            modifier = Modifier.weight(1f),
                            testTag = "settings_equalizer_item"
                        )
                        SettingsShortcutTile(
                            title = stringResource(R.string.settings_stats_title),
                            icon = Icons.Filled.BarChart,
                            onClick = onNavigateToStats,
                            modifier = Modifier.weight(1f),
                            testTag = "settings_stats_item"
                        )
                        SettingsShortcutTile(
                            title = stringResource(R.string.settings_download_title),
                            icon = Icons.Filled.CloudDownload,
                            onClick = onNavigateToDownload,
                            modifier = Modifier.weight(1f),
                            testTag = "settings_download_item"
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_category_appearance)) {
                        val languageLabel = when (appLanguage) {
                            "en" -> stringResource(R.string.settings_language_english)
                            "fa" -> stringResource(R.string.settings_language_persian)
                            else -> stringResource(R.string.settings_language_system)
                        }
                        SettingsActionItem(
                            title = stringResource(R.string.settings_language_title),
                            subtitle = languageLabel,
                            icon = Icons.Filled.Language,
                            onClick = { showLanguageDialog = true },
                            testTag = "settings_language_item"
                        )
                        val themeLabel = when (themeMode) {
                            "light" -> stringResource(R.string.settings_theme_light)
                            "dark" -> stringResource(R.string.settings_theme_dark)
                            else -> stringResource(R.string.settings_theme_system)
                        }
                        SettingsActionItem(
                            title = stringResource(R.string.settings_theme_title),
                            subtitle = themeLabel,
                            icon = Icons.Filled.DarkMode,
                            onClick = { showThemeDialog = true },
                            testTag = "settings_theme_item"
                        )
                        val themeStyleLabel = when (themeStyle) {
                            "oled" -> stringResource(R.string.theme_preset_oled)
                            "album_art" -> stringResource(R.string.theme_preset_album_art)
                            "wallpaper" -> stringResource(R.string.theme_preset_wallpaper)
                            "custom" -> stringResource(R.string.theme_preset_custom)
                            else -> stringResource(R.string.theme_preset_default)
                        }
                        SettingsActionItem(
                            title = stringResource(R.string.settings_theme_studio_title),
                            subtitle = themeStyleLabel,
                            icon = Icons.Filled.ColorLens,
                            onClick = onNavigateToTheme,
                            testTag = "settings_theme_studio_item"
                        )
                        val cornerLabel = when (cornerRoundness) {
                            "soft" -> stringResource(R.string.settings_corner_soft)
                            "sharp" -> stringResource(R.string.settings_corner_sharp)
                            else -> stringResource(R.string.settings_corner_standard)
                        }
                        SettingsActionItem(
                            title = stringResource(R.string.settings_corner_title),
                            subtitle = cornerLabel,
                            icon = Icons.Filled.RoundedCorner,
                            onClick = { showCornerDialog = true },
                            testTag = "settings_corner_item"
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_compact_mini_player_title),
                            subtitle = stringResource(R.string.settings_compact_mini_player_desc),
                            icon = Icons.Filled.ViewStream,
                            checked = isCompactMiniPlayer,
                            onCheckedChange = viewModel::setCompactMiniPlayer,
                            rowTag = "settings_compact_player_item",
                            switchTag = "settings_compact_player_switch"
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_category_playback)) {
                        SettingsActionItem(
                            title = stringResource(R.string.settings_fade_title),
                            subtitle = if (fadeSec == 0) stringResource(R.string.settings_off)
                            else stringResource(R.string.settings_seconds_fmt, fadeSec),
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            onClick = { showFadeDialog = true },
                            testTag = "settings_fade_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_replaygain_title),
                            subtitle = when (replayGainMode) {
                                "track" -> stringResource(R.string.settings_replaygain_track)
                                "album" -> stringResource(R.string.settings_replaygain_album)
                                else -> stringResource(R.string.settings_off)
                            },
                            icon = Icons.Filled.GraphicEq,
                            onClick = { showReplayGainDialog = true },
                            testTag = "settings_replaygain_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_replaygain_preamp_title),
                            subtitle = if (replayGainPreampDb == 0f) stringResource(R.string.settings_off)
                            else "${replayGainPreampDb.toInt()} dB",
                            icon = Icons.Filled.Tune,
                            onClick = { showPreampDialog = true },
                            testTag = "settings_replaygain_preamp_item"
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_skip_silence_title),
                            subtitle = stringResource(R.string.settings_skip_silence_desc),
                            icon = Icons.Filled.SkipNext,
                            checked = skipSilence,
                            onCheckedChange = viewModel::setSkipSilence,
                            rowTag = "settings_skip_silence_item",
                            switchTag = "settings_skip_silence_switch"
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_resume_headset_title),
                            subtitle = stringResource(R.string.settings_resume_headset_desc),
                            icon = Icons.Filled.Headset,
                            checked = resumeOnHeadset,
                            onCheckedChange = viewModel::setResumeOnHeadset,
                            rowTag = "settings_resume_headset_item",
                            switchTag = "settings_resume_headset_switch"
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_category_library)) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_show_system_audio_title),
                            subtitle = stringResource(R.string.settings_show_system_audio_desc),
                            icon = Icons.Filled.Headset,
                            checked = showSystemAudio,
                            onCheckedChange = viewModel::setShowSystemAudio,
                            rowTag = "settings_show_system_audio_item",
                            switchTag = "settings_show_system_audio_switch"
                        )
                        val lengthText = if (minTrackLengthSec == 0) {
                            stringResource(R.string.settings_min_length_0s)
                        } else {
                            "${minTrackLengthSec}s"
                        }
                        SettingsActionItem(
                            title = stringResource(R.string.settings_min_track_length_title),
                            subtitle = stringResource(R.string.settings_min_track_length_desc, lengthText),
                            icon = Icons.Filled.Timer,
                            onClick = { showMinLengthDialog = true },
                            testTag = "settings_min_length_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_excluded_folders_title),
                            subtitle = stringResource(R.string.settings_excluded_folders_desc, excludedFolders.size),
                            icon = Icons.Filled.FolderOff,
                            onClick = { showFoldersDialog = true },
                            testTag = "settings_excluded_folders_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_hidden_tracks_title),
                            subtitle = stringResource(R.string.settings_hidden_tracks_desc, hiddenTracks.size),
                            icon = Icons.Filled.VisibilityOff,
                            onClick = { showHiddenTracksDialog = true },
                            testTag = "settings_hidden_tracks_item"
                        )
                        SettingsSwitchItem(
                            title = stringResource(R.string.settings_split_artists_title),
                            subtitle = stringResource(R.string.settings_split_artists_desc),
                            icon = Icons.AutoMirrored.Filled.CallSplit,
                            checked = splitArtists,
                            onCheckedChange = viewModel::setSplitArtists,
                            rowTag = "settings_split_artists_item",
                            switchTag = "settings_split_artists_switch"
                        )
                        if (splitArtists) {
                            SettingsActionItem(
                                title = stringResource(R.string.settings_separators_title),
                                subtitle = artistSeparators.joinToString("  ") { "\u2066" + it.trim() + "\u2069" },
                                icon = Icons.AutoMirrored.Filled.CallSplit,
                                onClick = { showSeparatorsDialog = true },
                                testTag = "settings_separators_item"
                            )
                        }
                        SettingsActionItem(
                            title = stringResource(R.string.settings_library_tools_title),
                            subtitle = stringResource(R.string.settings_library_tools_desc),
                            icon = Icons.Filled.LibraryMusic,
                            onClick = onNavigateToLibraryTools,
                            testTag = "settings_library_tools_item"
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_category_lyrics)) {
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.settings_lrclib_title),
                            subtitle = stringResource(id = R.string.settings_lrclib_desc),
                            icon = Icons.Filled.Lyrics,
                            checked = isLrclibEnabled,
                            onCheckedChange = viewModel::setLrclibEnabled,
                            rowTag = "settings_lrclib_item",
                            switchTag = "settings_lrclib_switch"
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.settings_genius_title),
                            subtitle = stringResource(id = R.string.settings_genius_desc),
                            icon = Icons.Filled.Lyrics,
                            checked = isGeniusEnabled,
                            onCheckedChange = viewModel::setGeniusEnabled,
                            rowTag = "settings_genius_item",
                            switchTag = "settings_genius_switch"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_hidden_lyrics_title),
                            subtitle = stringResource(R.string.settings_hidden_lyrics_desc, hiddenLyrics.size),
                            icon = Icons.Filled.VisibilityOff,
                            onClick = { showHiddenLyricsDialog = true },
                            testTag = "settings_hidden_lyrics_item"
                        )
                    }
                }

                item {
                    SettingsGroup(title = stringResource(R.string.settings_category_data)) {
                        SettingsActionItem(
                            title = stringResource(R.string.settings_backup_title),
                            subtitle = stringResource(R.string.settings_backup_desc),
                            icon = Icons.Filled.Backup,
                            onClick = {
                                val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(java.util.Date())
                                backupLauncher.launch("logarhythm-backup-$stamp.json")
                            },
                            testTag = "settings_backup_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_restore_title),
                            subtitle = stringResource(R.string.settings_restore_desc),
                            icon = Icons.Filled.Restore,
                            onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                            testTag = "settings_restore_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_export_m3u_title),
                            subtitle = stringResource(R.string.settings_export_m3u_desc),
                            icon = Icons.Filled.Upload,
                            onClick = { exportPlaylistsLauncher.launch(null) },
                            testTag = "settings_export_m3u_item"
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.settings_import_m3u_title),
                            subtitle = stringResource(R.string.settings_import_m3u_desc),
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            onClick = { importPlaylistsLauncher.launch(arrayOf("*/*")) },
                            testTag = "settings_import_m3u_item"
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
