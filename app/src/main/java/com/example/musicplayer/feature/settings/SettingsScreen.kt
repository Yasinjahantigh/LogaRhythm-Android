package com.example.musicplayer.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.R
import com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToStats: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorSource by viewModel.colorSource.collectAsStateWithLifecycle()
    val cornerRoundness by viewModel.cornerRoundness.collectAsStateWithLifecycle()
    val isCompactMiniPlayer by viewModel.isCompactMiniPlayer.collectAsStateWithLifecycle()
    val minTrackLengthSec by viewModel.minTrackLengthSec.collectAsStateWithLifecycle()
    val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val isLrclibEnabled by viewModel.isLrclibEnabled.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorSourceDialog by remember { mutableStateOf(false) }
    var showCornerDialog by remember { mutableStateOf(false) }
    var showMinLengthDialog by remember { mutableStateOf(false) }
    var showFoldersDialog by remember { mutableStateOf(false) }

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

    if (showColorSourceDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_color_source_title),
            options = listOf(
                "fixed_color" to stringResource(R.string.settings_color_fixed),
                "wallpaper" to stringResource(R.string.settings_color_wallpaper),
                "album_art" to stringResource(R.string.settings_color_album_art)
            ),
            selectedKey = colorSource,
            onSelectKey = viewModel::setColorSource,
            onDismiss = { showColorSourceDialog = false }
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
            onDismiss = { showFoldersDialog = false }
        )
    }

    val playbackBottomPadding = LocalPlaybackBottomPadding.current

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = playbackBottomPadding + 48.dp)
        ) {
            item { SettingsCategoryHeader(stringResource(R.string.settings_category_audio)) }
            item {
                SettingsActionItem(
                    title = stringResource(R.string.settings_equalizer_title),
                    subtitle = stringResource(R.string.settings_equalizer_desc),
                    icon = Icons.Filled.GraphicEq,
                    onClick = onNavigateToEqualizer,
                    testTag = "settings_equalizer_item"
                )
            }
            item {
                SettingsActionItem(
                    title = stringResource(R.string.settings_stats_title),
                    subtitle = stringResource(R.string.settings_stats_desc),
                    icon = Icons.Filled.BarChart,
                    onClick = onNavigateToStats,
                    testTag = "settings_stats_item"
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SettingsCategoryHeader(stringResource(R.string.settings_category_appearance)) }
            item {
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
            }
            item {
                val colorLabel = when (colorSource) {
                    "album_art" -> stringResource(R.string.settings_color_album_art)
                    "wallpaper" -> stringResource(R.string.settings_color_wallpaper)
                    else -> stringResource(R.string.settings_color_fixed)
                }
                SettingsActionItem(
                    title = stringResource(R.string.settings_color_source_title),
                    subtitle = colorLabel,
                    icon = Icons.Filled.ColorLens,
                    onClick = { showColorSourceDialog = true },
                    testTag = "settings_color_source_item"
                )
            }
            item {
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
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("settings_compact_player_item"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewStream,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_compact_mini_player_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_compact_mini_player_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isCompactMiniPlayer,
                        onCheckedChange = viewModel::setCompactMiniPlayer,
                        modifier = Modifier.testTag("settings_compact_player_switch")
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SettingsCategoryHeader(stringResource(R.string.settings_category_library)) }
            item {
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
            }
            item {
                SettingsActionItem(
                    title = stringResource(R.string.settings_excluded_folders_title),
                    subtitle = stringResource(R.string.settings_excluded_folders_desc, excludedFolders.size),
                    icon = Icons.Filled.FolderOff,
                    onClick = { showFoldersDialog = true },
                    testTag = "settings_excluded_folders_item"
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("settings_lrclib_item"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.settings_lrclib_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.settings_lrclib_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isLrclibEnabled,
                        onCheckedChange = viewModel::setLrclibEnabled,
                        modifier = Modifier.testTag("settings_lrclib_switch")
                    )
                }
            }
        }
    }
}
