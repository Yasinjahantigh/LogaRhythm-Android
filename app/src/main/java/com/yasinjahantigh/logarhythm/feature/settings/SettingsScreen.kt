package com.yasinjahantigh.logarhythm.feature.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.ai.AiProvider
import com.yasinjahantigh.logarhythm.data.update.UpdateState
import com.yasinjahantigh.logarhythm.ui.components.FeatureGuideSheet
import com.yasinjahantigh.logarhythm.ui.components.formatCount
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
import com.yasinjahantigh.logarhythm.ui.designsystem.bounceOnPress
import com.yasinjahantigh.logarhythm.ui.designsystem.staggeredEntrance

private enum class SettingsCategory(
    val titleRes: Int,
    val icon: ImageVector
) {
    APPEARANCE(R.string.settings_category_appearance, Icons.Filled.ColorLens),
    PLAYBACK(R.string.settings_category_playback, Icons.AutoMirrored.Filled.VolumeUp),
    LIBRARY(R.string.settings_category_library, Icons.Filled.LibraryMusic),
    LYRICS(R.string.settings_category_lyrics, Icons.Filled.Lyrics),
    AI_DATA(R.string.settings_page_ai_data, Icons.Filled.Backup),
    ABOUT(R.string.settings_category_about, Icons.Filled.Info)
}

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
    val isNeteaseEnabled by viewModel.isNeteaseEnabled.collectAsStateWithLifecycle()
    val isKugouEnabled by viewModel.isKugouEnabled.collectAsStateWithLifecycle()
    val isLyricsOvhEnabled by viewModel.isLyricsOvhEnabled.collectAsStateWithLifecycle()
    val isCustomLyricsEnabled by viewModel.isCustomLyricsEnabled.collectAsStateWithLifecycle()
    val customLyricsUrl by viewModel.customLyricsUrl.collectAsStateWithLifecycle()
    val sourceTestResults by viewModel.sourceTestResults.collectAsStateWithLifecycle()
    val isTestingSources by viewModel.isTestingSources.collectAsStateWithLifecycle()
    val fadeSec by viewModel.fadeSec.collectAsStateWithLifecycle()
    val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
    val replayGainMode by viewModel.replayGainMode.collectAsStateWithLifecycle()
    val replayGainPreampDb by viewModel.replayGainPreampDb.collectAsStateWithLifecycle()
    val resumeOnHeadset by viewModel.resumeOnHeadset.collectAsStateWithLifecycle()
    val privateSession by viewModel.privateSession.collectAsStateWithLifecycle()
    val doubleTapSeek by viewModel.doubleTapSeek.collectAsStateWithLifecycle()
    val folderWhitelistMode by viewModel.folderWhitelistMode.collectAsStateWithLifecycle()
    val whitelistedFolders by viewModel.whitelistedFolders.collectAsStateWithLifecycle()
    val splitArtists by viewModel.splitArtists.collectAsStateWithLifecycle()
    val artistSeparators by viewModel.artistSeparators.collectAsStateWithLifecycle()
    val artistNeverSplit by viewModel.artistNeverSplit.collectAsStateWithLifecycle()
    val hiddenLyrics by viewModel.hiddenLyricsTracks.collectAsStateWithLifecycle()
    val hiddenTracks by viewModel.hiddenTracks.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val aiTestState by viewModel.aiTestState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()
    val autoCheckEnabled by viewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    val totalDeviceInstalls by viewModel.totalDeviceInstalls.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = selectedCategoryName?.let { name ->
        SettingsCategory.entries.firstOrNull { it.name == name }
    }

    var showFadeDialog by remember { mutableStateOf(false) }
    var showReplayGainDialog by remember { mutableStateOf(false) }
    var showPreampDialog by remember { mutableStateOf(false) }
    var showSeparatorsDialog by remember { mutableStateOf(false) }
    var showNeverSplitDialog by remember { mutableStateOf(false) }
    var showHiddenLyricsDialog by remember { mutableStateOf(false) }
    var showHiddenTracksDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCornerDialog by remember { mutableStateOf(false) }
    var showMinLengthDialog by remember { mutableStateOf(false) }
    var showFoldersDialog by remember { mutableStateOf(false) }
    var showCustomLyricsDialog by remember { mutableStateOf(false) }
    var showLyricsTestDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showFeatureGuide by remember { mutableStateOf(false) }

    if (showAiDialog) {
        AiSettingsDialog(
            settings = aiSettings,
            testState = aiTestState,
            onTest = viewModel::testAiConnection,
            onSave = { provider, baseUrl, model, apiKey ->
                viewModel.saveAiSettings(provider, baseUrl, model, apiKey) {
                    showAiDialog = false
                }
            },
            onClearKey = viewModel::clearAiKey,
            onDismiss = { showAiDialog = false }
        )
    }

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
                    onSaved = { context.findActivity()?.recreate() },
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
            whitelistMode = folderWhitelistMode,
            whitelistedFolders = whitelistedFolders,
            onSetWhitelistMode = viewModel::setFolderWhitelistMode,
            onToggleWhitelistFolder = viewModel::toggleWhitelistedFolder,
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

    if (showNeverSplitDialog) {
        NeverSplitDialog(
            current = artistNeverSplit,
            onSave = viewModel::setArtistNeverSplit,
            onReset = { viewModel.setArtistNeverSplit(emptySet()) },
            onDismiss = { showNeverSplitDialog = false }
        )
    }

    if (showHiddenLyricsDialog) {
        HiddenLyricsDialog(
            tracks = hiddenLyrics,
            onRestore = viewModel::restoreLyrics,
            onDismiss = { showHiddenLyricsDialog = false }
        )
    }

    if (showCustomLyricsDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_lyrics_custom_url_title),
            initialValue = customLyricsUrl.orEmpty(),
            label = stringResource(R.string.settings_lyrics_url_label),
            placeholder = stringResource(R.string.settings_lyrics_custom_url_hint),
            isValid = { it.startsWith("https://") && it.length > "https://".length },
            invalidMessage = stringResource(R.string.settings_lyrics_custom_url_invalid),
            onConfirm = { url ->
                viewModel.setCustomLyricsUrl(url.ifBlank { null })
                showCustomLyricsDialog = false
            },
            onDismiss = { showCustomLyricsDialog = false }
        )
    }

    if (showLyricsTestDialog) {
        LyricsTestDialog(
            results = sourceTestResults.orEmpty(),
            isTesting = isTestingSources,
            onDismiss = {
                showLyricsTestDialog = false
                viewModel.dismissSourceTestResults()
            }
        )
    }

    if (showFeatureGuide) {
        FeatureGuideSheet(onDismiss = { showFeatureGuide = false })
    }

    val languageLabel = when (appLanguage) {
        "en" -> stringResource(R.string.settings_language_english)
        "fa" -> stringResource(R.string.settings_language_persian)
        else -> stringResource(R.string.settings_language_system)
    }
    val themeLabel = when (themeMode) {
        "light" -> stringResource(R.string.settings_theme_light)
        "dark" -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
    }
    val themeStyleLabel = when (themeStyle) {
        "oled" -> stringResource(R.string.theme_preset_oled)
        "album_art" -> stringResource(R.string.theme_preset_album_art)
        "wallpaper" -> stringResource(R.string.theme_preset_wallpaper)
        "custom" -> stringResource(R.string.theme_preset_custom)
        else -> stringResource(R.string.theme_preset_default)
    }
    val cornerLabel = when (cornerRoundness) {
        "soft" -> stringResource(R.string.settings_corner_soft)
        "sharp" -> stringResource(R.string.settings_corner_sharp)
        else -> stringResource(R.string.settings_corner_standard)
    }
    val enabledLyricsCount = listOf(
        isLrclibEnabled,
        isGeniusEnabled,
        isNeteaseEnabled,
        isKugouEnabled,
        isLyricsOvhEnabled,
        isCustomLyricsEnabled
    ).count { it }

    val categorySubtitles: Map<SettingsCategory, String> = mapOf(
        SettingsCategory.APPEARANCE to "$themeStyleLabel · $languageLabel",
        SettingsCategory.PLAYBACK to stringResource(R.string.settings_hub_playback_sub),
        SettingsCategory.LIBRARY to stringResource(R.string.settings_hub_library_sub),
        SettingsCategory.LYRICS to "${formatCount(enabledLyricsCount)}/${formatCount(6)} · " +
            stringResource(R.string.settings_lyrics_sources_desc),
        SettingsCategory.AI_DATA to stringResource(R.string.settings_hub_ai_data_sub),
        SettingsCategory.ABOUT to "v${viewModel.installedVersion} · " + stringResource(R.string.settings_hub_about_sub)
    )

    val playbackBottomPadding = LocalPlaybackBottomPadding.current

    val renderCategoryContent: @Composable (SettingsCategory) -> Unit = { category ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = playbackBottomPadding + 32.dp)
        ) {
            when (category) {
                SettingsCategory.APPEARANCE -> {
                    item {
                        SettingsGroup(title = stringResource(R.string.settings_category_appearance)) {
                            SettingsActionItem(
                                title = stringResource(R.string.settings_theme_studio_title),
                                subtitle = themeStyleLabel,
                                icon = Icons.Filled.ColorLens,
                                onClick = onNavigateToTheme,
                                testTag = "settings_theme_studio_item"
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.settings_language_title),
                                subtitle = languageLabel,
                                icon = Icons.Filled.Language,
                                onClick = { showLanguageDialog = true },
                                testTag = "settings_language_item"
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.settings_theme_title),
                                subtitle = themeLabel,
                                icon = Icons.Filled.DarkMode,
                                onClick = { showThemeDialog = true },
                                testTag = "settings_theme_item"
                            )
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
                }

                SettingsCategory.PLAYBACK -> {
                    item {
                        SettingsGroup(title = stringResource(R.string.settings_category_playback)) {
                            SettingsActionItem(
                                title = stringResource(R.string.settings_equalizer_title),
                                subtitle = stringResource(R.string.settings_hub_eq_sub),
                                icon = Icons.Filled.GraphicEq,
                                onClick = onNavigateToEqualizer,
                                testTag = "settings_equalizer_sub_item"
                            )
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
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_double_tap_seek_title),
                                subtitle = stringResource(R.string.settings_double_tap_seek_desc),
                                icon = Icons.Filled.TouchApp,
                                checked = doubleTapSeek,
                                onCheckedChange = viewModel::setDoubleTapSeek,
                                rowTag = "settings_double_tap_seek_item",
                                switchTag = "settings_double_tap_seek_switch"
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_private_session_title),
                                subtitle = stringResource(R.string.settings_private_session_desc),
                                icon = Icons.Filled.VisibilityOff,
                                checked = privateSession,
                                onCheckedChange = viewModel::setPrivateSession,
                                rowTag = "settings_private_session_item",
                                switchTag = "settings_private_session_switch"
                            )
                        }
                    }
                }

                SettingsCategory.LIBRARY -> {
                    item {
                        SettingsGroup(title = stringResource(R.string.settings_category_library)) {
                            SettingsActionItem(
                                title = stringResource(R.string.settings_library_tools_title),
                                subtitle = stringResource(R.string.settings_library_tools_desc),
                                icon = Icons.Filled.AutoFixHigh,
                                onClick = onNavigateToLibraryTools,
                                testTag = "settings_library_tools_item"
                            )
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
                                SettingsActionItem(
                                    title = stringResource(R.string.settings_never_split_title),
                                    subtitle = if (artistNeverSplit.isEmpty()) {
                                        stringResource(R.string.settings_never_split_desc)
                                    } else {
                                        artistNeverSplit.sorted().joinToString("  ")
                                    },
                                    icon = Icons.AutoMirrored.Filled.CallSplit,
                                    onClick = { showNeverSplitDialog = true },
                                    testTag = "settings_never_split_item"
                                )
                            }
                        }
                    }
                }

                SettingsCategory.LYRICS -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VpnLock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.vpn_proactive_lyrics_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    item {
                        var lyricsSourcesExpanded by remember { mutableStateOf(true) }
                        SettingsGroup(title = stringResource(R.string.settings_category_lyrics)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { lyricsSourcesExpanded = !lyricsSourcesExpanded }
                                    .defaultMinSize(minHeight = 60.dp)
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                    .testTag("settings_lyrics_sources_expand"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SettingsIconTile(Icons.Filled.Lyrics)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_lyrics_sources_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${formatCount(enabledLyricsCount)}/${formatCount(6)} · " +
                                            stringResource(R.string.settings_lyrics_sources_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Icon(
                                    imageVector = if (lyricsSourcesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(visible = lyricsSourcesExpanded) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
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
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_lyrics_netease_title),
                                        subtitle = stringResource(R.string.settings_lyrics_netease_desc),
                                        icon = Icons.Filled.Lyrics,
                                        checked = isNeteaseEnabled,
                                        onCheckedChange = viewModel::setNeteaseEnabled,
                                        rowTag = "settings_netease_item",
                                        switchTag = "settings_netease_switch"
                                    )
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_lyrics_kugou_title),
                                        subtitle = stringResource(R.string.settings_lyrics_kugou_desc),
                                        icon = Icons.Filled.Lyrics,
                                        checked = isKugouEnabled,
                                        onCheckedChange = viewModel::setKugouEnabled,
                                        rowTag = "settings_kugou_item",
                                        switchTag = "settings_kugou_switch"
                                    )
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_lyrics_ovh_title),
                                        subtitle = stringResource(R.string.settings_lyrics_ovh_desc),
                                        icon = Icons.Filled.Lyrics,
                                        checked = isLyricsOvhEnabled,
                                        onCheckedChange = viewModel::setLyricsOvhEnabled,
                                        rowTag = "settings_lyrics_ovh_item",
                                        switchTag = "settings_lyrics_ovh_switch"
                                    )
                                    SettingsSwitchItem(
                                        title = stringResource(R.string.settings_lyrics_custom_title),
                                        subtitle = stringResource(R.string.settings_lyrics_custom_desc),
                                        icon = Icons.Filled.Lyrics,
                                        checked = isCustomLyricsEnabled,
                                        onCheckedChange = viewModel::setCustomLyricsEnabled,
                                        rowTag = "settings_custom_lyrics_item",
                                        switchTag = "settings_custom_lyrics_switch"
                                    )
                                    SettingsActionItem(
                                        title = stringResource(R.string.settings_lyrics_custom_url_title),
                                        subtitle = customLyricsUrl ?: stringResource(R.string.settings_lyrics_custom_url_not_set),
                                        icon = Icons.Filled.Language,
                                        onClick = { showCustomLyricsDialog = true },
                                        testTag = "settings_custom_lyrics_url_item"
                                    )
                                }
                            }
                            SettingsActionItem(
                                title = stringResource(R.string.settings_lyrics_test_title),
                                subtitle = if (isTestingSources) {
                                    stringResource(R.string.lyrics_test_running)
                                } else {
                                    stringResource(R.string.settings_lyrics_test_desc)
                                },
                                icon = Icons.Filled.Lyrics,
                                onClick = {
                                    showLyricsTestDialog = true
                                    if (!isTestingSources) viewModel.testLyricsSources()
                                },
                                testTag = "settings_lyrics_test_item"
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
                }

                SettingsCategory.AI_DATA -> {
                    item {
                        SettingsGroup(title = stringResource(R.string.settings_category_ai)) {
                            SettingsActionItem(
                                title = stringResource(R.string.settings_ai_title),
                                subtitle = if (aiSettings.hasKey) {
                                    stringResource(
                                        R.string.settings_ai_configured,
                                        stringResource(
                                            if (aiSettings.provider == AiProvider.GEMINI) {
                                                R.string.settings_ai_provider_gemini
                                            } else {
                                                R.string.settings_ai_provider_openai
                                            }
                                        )
                                    )
                                } else {
                                    stringResource(R.string.settings_ai_not_configured)
                                },
                                icon = Icons.Filled.AutoAwesome,
                                onClick = {
                                    viewModel.resetAiTestState()
                                    showAiDialog = true
                                },
                                testTag = "settings_ai_item"
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

                SettingsCategory.ABOUT -> {
                    item {
                        SettingsGroup(title = stringResource(R.string.settings_category_about)) {
                            SettingsInfoItem(
                                title = stringResource(R.string.settings_version_title),
                                subtitle = viewModel.installedVersion,
                                icon = Icons.Filled.Info,
                                testTag = "settings_version_item"
                            )
                            val updateSubtitle = when (val state = updateState) {
                                UpdateState.Checking -> stringResource(R.string.update_checking)
                                is UpdateState.Available ->
                                    stringResource(R.string.update_available_short, state.release.versionName)
                                is UpdateState.Downloading -> if (state.fraction >= 0f) {
                                    stringResource(R.string.update_downloading_percent, (state.fraction * 100).toInt())
                                } else {
                                    stringResource(R.string.update_downloading_wait)
                                }
                                is UpdateState.Failed -> stringResource(R.string.update_failed_title)
                                UpdateState.Hidden -> latestRelease?.let {
                                    val count = maxOf(it.downloads, totalDeviceInstalls ?: 0L)
                                    stringResource(
                                        R.string.update_latest_short,
                                        it.versionName,
                                        formatCount(count.toInt())
                                    )
                                } ?: stringResource(R.string.settings_check_update_desc)
                            }
                            SettingsActionItem(
                                title = stringResource(R.string.settings_check_update_title),
                                subtitle = updateSubtitle,
                                icon = Icons.Filled.SystemUpdate,
                                onClick = viewModel::checkForUpdates,
                                testTag = "settings_check_update_item"
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_auto_update_title),
                                subtitle = stringResource(R.string.settings_auto_update_desc),
                                icon = Icons.Filled.SystemUpdate,
                                checked = autoCheckEnabled,
                                onCheckedChange = viewModel::setAutoCheckEnabled,
                                rowTag = "settings_auto_update_item",
                                switchTag = "settings_auto_update_switch"
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.settings_github_title),
                                subtitle = stringResource(R.string.settings_github_desc),
                                icon = Icons.Filled.Code,
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yasinjahantigh/LogaRhythm-Android"))
                                        context.startActivity(intent)
                                    }
                                },
                                testTag = "settings_github_item"
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.settings_donate_daramet_title),
                                subtitle = stringResource(R.string.settings_donate_daramet_desc),
                                icon = Icons.Filled.Favorite,
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://daramet.com/yasinjahantigh"))
                                        context.startActivity(intent)
                                    }
                                },
                                testTag = "settings_donate_daramet_item"
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.settings_donate_ton_title),
                                subtitle = stringResource(R.string.settings_donate_ton_desc),
                                icon = Icons.Filled.ContentCopy,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "GRAM (TON)",
                                            "UQD-le1G2GZN7rF8PE0PKZ6BeyqKGDqaby6IZvcmw5VgLNZj"
                                        )
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_donate_ton_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                testTag = "settings_donate_ton_item"
                            )
                            if (viewModel.installStatsAvailable) {
                                val installSubtitle = if (totalDeviceInstalls != null && totalDeviceInstalls!! > 0) {
                                    stringResource(R.string.settings_install_stats_count, formatCount(totalDeviceInstalls!!.toInt())) +
                                        " · " + stringResource(R.string.settings_install_stats_desc)
                                } else {
                                    stringResource(R.string.settings_install_stats_desc)
                                }
                                SettingsInfoItem(
                                    title = stringResource(R.string.settings_install_stats_title),
                                    subtitle = installSubtitle,
                                    icon = Icons.Filled.Info,
                                    testTag = "settings_install_stats_item"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTwoPane = maxWidth >= 720.dp

            // On phones, pressing system Back inside a Settings sub-page returns to the Settings Hub.
            BackHandler(enabled = !isTwoPane && selectedCategory != null) {
                selectedCategoryName = null
            }

            if (isTwoPane) {
                val activeCategory = selectedCategory ?: SettingsCategory.APPEARANCE
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1140.dp)
                        .align(Alignment.TopCenter)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .width(330.dp)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = playbackBottomPadding + 28.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(id = R.string.settings_title),
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
                            )
                        }
                        item {
                            SettingsQuickShortcutsGrid(
                                onEqualizer = onNavigateToEqualizer,
                                onStats = onNavigateToStats,
                                onTagFixer = onNavigateToLibraryTools,
                                onDownload = onNavigateToDownload,
                                onGuide = { showFeatureGuide = true }
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        items(SettingsCategory.entries.size) { idx ->
                            val cat = SettingsCategory.entries[idx]
                            SettingsCategoryRowCard(
                                category = cat,
                                subtitle = categorySubtitles[cat].orEmpty(),
                                selected = cat == activeCategory,
                                onClick = { selectedCategoryName = cat.name },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    }

                    VerticalDivider(
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                    ) {
                        Text(
                            text = stringResource(activeCategory.titleRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
                        )
                        AnimatedContent(
                            targetState = activeCategory,
                            transitionSpec = {
                                fadeIn(tween(180)).togetherWith(fadeOut(tween(130)))
                            },
                            label = "settings_tablet_detail"
                        ) { cat ->
                            renderCategoryContent(cat)
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(160)))
                        } else {
                            (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(160)))
                        }
                    },
                    label = "settings_phone_pages"
                ) { activeCategory ->
                    if (activeCategory == null) {
                        // Clean Settings Hub
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = playbackBottomPadding + 32.dp)
                        ) {
                            item {
                                Text(
                                    text = stringResource(id = R.string.settings_title),
                                    style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)
                                        .staggeredEntrance(0)
                                )
                            }
                            item {
                                SettingsQuickShortcutsGrid(
                                    onEqualizer = onNavigateToEqualizer,
                                    onStats = onNavigateToStats,
                                    onTagFixer = onNavigateToLibraryTools,
                                    onDownload = onNavigateToDownload,
                                    onGuide = { showFeatureGuide = true },
                                    modifier = Modifier.staggeredEntrance(1)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            items(SettingsCategory.entries.size) { idx ->
                                val cat = SettingsCategory.entries[idx]
                                SettingsCategoryRowCard(
                                    category = cat,
                                    subtitle = categorySubtitles[cat].orEmpty(),
                                    selected = false,
                                    onClick = { selectedCategoryName = cat.name },
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                        .staggeredEntrance(idx + 2)
                                )
                            }
                        }
                    } else {
                        // Focused Settings Sub-Page
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedCategoryName = null },
                                    modifier = Modifier.testTag("settings_subpage_back")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.btn_close)
                                    )
                                }
                                Text(
                                    text = stringResource(activeCategory.titleRes),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            renderCategoryContent(activeCategory)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsQuickShortcutsGrid(
    onEqualizer: () -> Unit,
    onStats: () -> Unit,
    onTagFixer: () -> Unit,
    onDownload: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsShortcutTile(
                title = stringResource(R.string.settings_library_tools_title),
                icon = Icons.Filled.AutoFixHigh,
                onClick = onTagFixer,
                modifier = Modifier.weight(1f),
                testTag = "settings_tag_fixer_tile"
            )
            SettingsShortcutTile(
                title = stringResource(R.string.settings_equalizer_title),
                icon = Icons.Filled.GraphicEq,
                onClick = onEqualizer,
                modifier = Modifier.weight(1f),
                testTag = "settings_equalizer_item"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsShortcutTile(
                title = stringResource(R.string.settings_stats_title),
                icon = Icons.Filled.BarChart,
                onClick = onStats,
                modifier = Modifier.weight(1f),
                testTag = "settings_stats_item"
            )
            SettingsShortcutTile(
                title = stringResource(R.string.settings_download_title),
                icon = Icons.Filled.CloudDownload,
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                testTag = "settings_download_item"
            )
        }
        val guideInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = onGuide,
            interactionSource = guideInteraction,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .bounceOnPress(guideInteraction)
                .testTag("settings_feature_guide_item")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconTile(Icons.Filled.AutoAwesome)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.feature_guide_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryRowCard(
    category: SettingsCategory,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.8.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .bounceOnPress(interactionSource)
            .testTag("settings_category_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 68.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(category.icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!selected) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
