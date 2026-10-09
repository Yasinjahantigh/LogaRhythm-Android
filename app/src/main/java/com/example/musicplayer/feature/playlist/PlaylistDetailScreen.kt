package com.example.musicplayer.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.library.TrackListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var selectedTrackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val isBuiltIn = playlist?.isBuiltIn ?: false
    val displayName = if (isBuiltIn) stringResource(R.string.playlist_favorites) else (playlist?.name.orEmpty())

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist?.name.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                viewModel.renamePlaylist(newName)
            }
        )
    }

    if (showDeleteDialog) {
        DeletePlaylistDialog(
            playlistName = displayName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deletePlaylist(onDeleted = onNavigateBack)
            }
        )
    }

    selectedTrackForAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            playlists = allPlaylists,
            onDismiss = { selectedTrackForAddToPlaylist = null },
            onSelectPlaylist = { target ->
                viewModel.addTrackToPlaylist(target.id, track.id)
                selectedTrackForAddToPlaylist = null
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.added_to_playlist, target.name))
                }
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    selectedTrackForMenu?.let { track ->
        SongMenuBottomSheet(
            track = track,
            onDismissRequest = { selectedTrackForMenu = null },
            onPlayNext = {
                viewModel.playNext(track)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.playing_next))
                }
            },
            onAddToQueue = {
                viewModel.addToQueue(track)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.added_to_queue))
                }
            },
            onAddToPlaylist = {
                selectedTrackForAddToPlaylist = track
            },
            onRemoveFromPlaylist = {
                viewModel.removeTrack(track.id)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.removed_from_playlist))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("playlist_detail_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = displayName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (!isBuiltIn) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_playlist_menu))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_rename)) },
                                    onClick = { showMenu = false; showRenameDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_delete)) },
                                    onClick = { showMenu = false; showDeleteDialog = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isBuiltIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBuiltIn) Icons.Default.Favorite else Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = if (isBuiltIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val totalDuration = tracks.sumOf { it.durationMs }
                    Text(
                        text = "${tracks.size} songs • ${TimeUtils.formatDuration(totalDuration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = viewModel::playAll, enabled = tracks.isNotEmpty(), modifier = Modifier.testTag("btn_playlist_play")) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_play))
                        }
                        FilledTonalButton(onClick = viewModel::shuffleAll, enabled = tracks.isNotEmpty(), modifier = Modifier.testTag("btn_playlist_shuffle")) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_shuffle))
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.empty_playlist_tracks), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackListItem(
                        track = track,
                        onClick = { viewModel.playTrackAt(index) },
                        onMenuClick = { selectedTrackForMenu = track }
                    )
                }
            }
        }
    }
}
