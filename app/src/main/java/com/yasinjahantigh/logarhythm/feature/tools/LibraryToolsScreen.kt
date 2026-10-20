package com.yasinjahantigh.logarhythm.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    viewModel: LibraryToolsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val missing by viewModel.missingTags.collectAsStateWithLifecycle()
    val hiddenTracks by viewModel.hiddenTracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    menuTrack?.let { track ->
        SongMenuBottomSheet(
            track = track,
            onDismissRequest = { menuTrack = null },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onAddToPlaylist = { showAddToPlaylist = true },
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
                            TrackRow(track, currentTrack, isPlaying, viewModel::play) { menuTrack = it }
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
                        TrackRow(track, currentTrack, isPlaying, viewModel::play) { menuTrack = it }
                    }
                }
            } else {
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
                                TrackRow(track, currentTrack, isPlaying, viewModel::play) { menuTrack = it }
                            }
                            TextButton(onClick = { viewModel.unhideTrack(track.id) }) {
                                Text(stringResource(R.string.btn_restore))
                            }
                        }
                    }
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
    onMenu: (Track) -> Unit
) {
    val isCurrent = current?.id == track.id
    TrackListItem(
        track = track,
        onClick = { onPlay(track) },
        isCurrentTrack = isCurrent,
        isPlaying = isCurrent && isPlaying,
        onMenuClick = { onMenu(track) }
    )
}

@Composable
private fun EmptyNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
