package com.yasinjahantigh.logarhythm.feature.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding

@Composable
fun LibraryToolsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEditTrack: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    initialTab: Int = 0,
    viewModel: LibraryToolsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val missing by viewModel.missingTags.collectAsStateWithLifecycle()
    val hiddenTracks by viewModel.hiddenTracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val findingFixes by viewModel.findingFixes.collectAsStateWithLifecycle()
    val aiSuggestions by viewModel.aiSuggestions.collectAsStateWithLifecycle()
    val findingAiFixes by viewModel.findingAiFixes.collectAsStateWithLifecycle()
    val savingFix by viewModel.savingFix.collectAsStateWithLifecycle()
    val fixFailed by viewModel.fixFailed.collectAsStateWithLifecycle()
    val mediaStoreConsent = com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(fixFailed) {
        if (fixFailed) android.widget.Toast.makeText(context, R.string.tools_fix_failed, android.widget.Toast.LENGTH_SHORT).show()
    }

    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    menuTrack?.let { track ->
        SongMenuBottomSheet(
            track = track,
            onDismissRequest = { menuTrack = null },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onAddToPlaylist = { showAddToPlaylist = true },
            onEditTrack = { onEditTrack(track.id) },
            onHideTrack = { viewModel.hideTrack(track) }
        )
        if (showAddToPlaylist) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { showAddToPlaylist = false },
                onSelectPlaylist = { playlist ->
                    playlistViewModel.addTrackToPlaylist(playlist.id, track.id)
                    showAddToPlaylist = false
                },
                onCreatePlaylist = { name -> playlistViewModel.createPlaylist(name) }
            )
        }
    }

    Column(modifier = modifier.fillMaxSize().testTag("library_tools_screen")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("library_tools_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_close))
            }
            Text(
                text = stringResource(R.string.settings_library_tools_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text(stringResource(R.string.tools_duplicates) + (duplicates?.let { " (${it.size})" } ?: "")) }
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text(stringResource(R.string.tools_missing_tags) + (missing?.let { " (${it.size})" } ?: "")) }
            )
            FilterChip(
                selected = tab == 2,
                onClick = { tab = 2 },
                label = { Text(stringResource(R.string.tools_hidden_tracks) + (hiddenTracks?.let { " (${it.size})" } ?: "")) }
            )
            FilterChip(
                selected = tab == 3,
                onClick = { tab = 3; viewModel.suggestFixes() },
                label = { Text(stringResource(R.string.tools_regex_cleaner) + (suggestions?.let { " (${it.size})" } ?: "")) }
            )
            FilterChip(
                selected = tab == 4,
                onClick = {
                    tab = 4
                    if (viewModel.isAiConfigured && aiSuggestions == null) {
                        viewModel.suggestAiFixes()
                    }
                },
                leadingIcon = {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(stringResource(R.string.tools_ai_cleaner) + (aiSuggestions?.let { " (${it.size})" } ?: "")) }
            )
        }

        val bottom = LocalPlaybackBottomPadding.current
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = bottom + 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (tab == 0) {
                val groups = duplicates
                if (groups != null && groups.isEmpty()) item { EmptyNote(stringResource(R.string.tools_no_duplicates)) }
                groups?.forEach { group ->
                    item(key = "h_" + group.key) {
                        Text(
                            text = stringResource(R.string.tools_duplicate_group, group.tracks.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp)
                        )
                    }
                    items(group.tracks, key = { "d_" + group.key + it.id }) { track ->
                        Column {
                            TrackRow(
                                track = track,
                                current = currentTrack,
                                isPlaying = isPlaying,
                                onPlay = viewModel::play,
                                onMenu = { menuTrack = it },
                                onPlayNext = viewModel::playNext
                            )
                            Text(
                                text = track.filePath.ifBlank { track.folder },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            } else if (tab == 1) {
                val tracks = missing
                if (tracks != null && tracks.isEmpty()) item { EmptyNote(stringResource(R.string.tools_no_missing)) }
                tracks?.let { list ->
                    items(list, key = { "m_" + it.id }) { track ->
                        TrackRow(
                            track = track,
                            current = currentTrack,
                            isPlaying = isPlaying,
                            onPlay = viewModel::play,
                            onMenu = { menuTrack = it },
                            onPlayNext = viewModel::playNext
                        )
                    }
                }
            } else if (tab == 2) {
                val hidden = hiddenTracks
                if (hidden != null && hidden.isEmpty()) {
                    item { EmptyNote(stringResource(R.string.tools_no_hidden_tracks)) }
                }
                if (hidden != null && hidden.isNotEmpty()) {
                    item(key = "restore_all_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.unhideAllTracks() }) {
                                Text(stringResource(R.string.btn_restore_all))
                            }
                        }
                    }
                    items(hidden, key = { "h_" + it.id }) { track ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TrackRow(
                                    track = track,
                                    current = currentTrack,
                                    isPlaying = isPlaying,
                                    onPlay = viewModel::play,
                                    onMenu = { menuTrack = it },
                                    onPlayNext = viewModel::playNext
                                )
                            }
                            TextButton(onClick = { viewModel.unhideTrack(track.id) }) {
                                Text(stringResource(R.string.btn_restore))
                            }
                        }
                    }
                }
            } else if (tab == 3) {
                if (findingFixes) item { EmptyNote(stringResource(R.string.tools_finding_fixes)) }
                val list = suggestions
                if (!findingFixes && list != null && list.isEmpty()) item { EmptyNote(stringResource(R.string.tools_no_fixes)) }
                if (!findingFixes && list != null && list.isNotEmpty()) {
                    item(key = "regex_apply_all_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${list.size} suggestions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    val tracksToConsent = list.map { it.track }
                                    mediaStoreConsent.deleteTracks(tracksToConsent, onApproved = {
                                        viewModel.applyAllSuggestions(isAi = false) { count ->
                                            android.widget.Toast.makeText(context, "$count updated", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                }
                            ) {
                                Text(stringResource(R.string.tools_apply_all))
                            }
                        }
                    }
                }
                list?.let { items(it, key = { s -> "fix_" + s.track.id }) { suggestion ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(suggestion.track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            FixDiff(stringResource(R.string.tag_editor_field_title), suggestion.track.title, suggestion.tags.title)
                            FixDiff(stringResource(R.string.tag_editor_field_artist), suggestion.track.artist, suggestion.tags.artist)
                            FixDiff(stringResource(R.string.tag_editor_field_album), suggestion.track.album, suggestion.tags.album)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    enabled = savingFix == null,
                                    onClick = {
                                        mediaStoreConsent.write(suggestion.track, onApproved = {
                                            viewModel.applySuggestion(suggestion.track.id, isAi = false)
                                        })
                                    }
                                ) { Text(stringResource(R.string.btn_apply)) }
                            }
                        }
                    }
                } }
            } else if (tab == 4) {
                if (!viewModel.isAiConfigured) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.tools_ai_not_configured_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.tools_ai_not_configured_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onNavigateToSettings) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.tools_ai_configure_btn))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.tools_ai_cleaner),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { viewModel.suggestAiFixes() },
                                enabled = !findingAiFixes
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.tools_ai_scan_btn))
                            }
                        }
                    }
                    if (findingAiFixes) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.tools_ai_scanning),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    val aiList = aiSuggestions
                    if (!findingAiFixes && aiList != null && aiList.isEmpty()) {
                        item { EmptyNote(stringResource(R.string.tools_no_fixes)) }
                    }
                    if (!findingAiFixes && aiList != null && aiList.isNotEmpty()) {
                        item(key = "ai_apply_all_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${aiList.size} suggestions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        val tracksToConsent = aiList.map { it.track }
                                        mediaStoreConsent.deleteTracks(tracksToConsent, onApproved = {
                                            viewModel.applyAllSuggestions(isAi = true) { count ->
                                                android.widget.Toast.makeText(context, "$count updated", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                    }
                                ) {
                                    Text(stringResource(R.string.tools_apply_all))
                                }
                            }
                        }
                    }
                    aiList?.let { items(it, key = { s -> "ai_fix_" + s.track.id }) { suggestion ->
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(suggestion.track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FixDiff(stringResource(R.string.tag_editor_field_title), suggestion.track.title, suggestion.tags.title)
                                FixDiff(stringResource(R.string.tag_editor_field_artist), suggestion.track.artist, suggestion.tags.artist)
                                FixDiff(stringResource(R.string.tag_editor_field_album), suggestion.track.album, suggestion.tags.album)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(
                                        enabled = savingFix == null,
                                        onClick = {
                                            mediaStoreConsent.write(suggestion.track, onApproved = {
                                                viewModel.applySuggestion(suggestion.track.id, isAi = true)
                                            })
                                        }
                                    ) { Text(stringResource(R.string.btn_apply)) }
                                }
                            }
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    current: Track?,
    isPlaying: Boolean,
    onPlay: (Track) -> Unit,
    onMenu: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val isCurrent = current?.id == track.id
    TrackListItem(
        track = track,
        onClick = { onPlay(track) },
        isCurrentTrack = isCurrent,
        isPlaying = isCurrent && isPlaying,
        onMenuClick = { onMenu(track) },
        onPlayNext = { onPlayNext(track) }
    )
}

@Composable
private fun FixDiff(label: String, before: String, after: String) {
    if (before == after) return
    Text(
        text = "$label: $before → $after",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
