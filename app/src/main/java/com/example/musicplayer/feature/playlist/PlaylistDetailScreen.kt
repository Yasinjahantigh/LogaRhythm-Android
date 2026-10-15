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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.foundation.layout.WindowInsets
import com.example.musicplayer.ui.components.DetailHero
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
            },
            onHideTrack = {
                viewModel.hideTrack(track)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.track_hidden_toast, track.title))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("playlist_detail_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                item {
                    DetailHero(
                        title = displayName,
                        meta = stringResource(R.string.playlist_track_count, tracks.size) +
                            " • " + TimeUtils.formatDuration(tracks.sumOf { it.durationMs }),
                        artworkUri = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                        icon = if (isBuiltIn) Icons.Default.Favorite else Icons.AutoMirrored.Filled.QueueMusic,
                        onBack = onNavigateBack,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::shuffleAll,
                        actionsEnabled = tracks.isNotEmpty(),
                        backTag = "playlist_back_button",
                        playTag = "btn_playlist_play",
                        shuffleTag = "btn_playlist_shuffle",
                        topEndAction = if (!isBuiltIn) {
                            {
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
                        } else null
                    )
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
}
