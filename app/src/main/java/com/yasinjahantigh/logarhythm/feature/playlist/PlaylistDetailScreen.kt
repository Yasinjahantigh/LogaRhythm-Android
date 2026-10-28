package com.yasinjahantigh.logarhythm.feature.playlist

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.yasinjahantigh.logarhythm.ui.components.DetailHero
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.library.SelectionAction
import com.yasinjahantigh.logarhythm.feature.library.SelectionActionBar
import com.yasinjahantigh.logarhythm.feature.library.SelectionTopBar
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
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
    var tracksForAddToPlaylist by remember { mutableStateOf<List<Track>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedTracks = remember(tracks, selectedTrackIds) { tracks.filter { it.id in selectedTrackIds } }

    fun exitSelection() {
        selectionMode = false
        selectedTrackIds = emptySet()
    }

    fun toggleSelection(track: Track) {
        val next = if (track.id in selectedTrackIds) selectedTrackIds - track.id else selectedTrackIds + track.id
        selectedTrackIds = next
        if (next.isEmpty()) selectionMode = false
    }

    fun startSelection(track: Track) {
        selectionMode = true
        selectedTrackIds = setOf(track.id)
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

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

    tracksForAddToPlaylist?.let { selected ->
        AddToPlaylistDialog(
            playlists = allPlaylists,
            onDismiss = { tracksForAddToPlaylist = null },
            onSelectPlaylist = { target ->
                viewModel.addTracksToPlaylist(target.id, selected.map { it.id })
                tracksForAddToPlaylist = null
                exitSelection()
                scope.launch {
                    if (selected.size == 1) {
                        snackbarHostState.showSnackbar(context.getString(R.string.added_to_playlist, target.name))
                    } else {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.selection_added_to_playlist, selected.size, target.name)
                        )
                    }
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
                tracksForAddToPlaylist = listOf(track)
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
        val bottomPadding = LocalPlaybackBottomPadding.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedTracks.size,
                        allSelected = tracks.isNotEmpty() && selectedTracks.size == tracks.size,
                        onClose = ::exitSelection,
                        onToggleSelectAll = {
                            if (selectedTracks.size == tracks.size) {
                                exitSelection()
                            } else {
                                selectedTrackIds = tracks.map { it.id }.toSet()
                            }
                        },
                        modifier = Modifier.widthIn(max = 840.dp)
                    )
                }

                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = 840.dp)
                ) {
                if (!selectionMode) {
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
                            onClick = {
                                if (selectionMode) toggleSelection(track) else viewModel.playTrackAt(index)
                            },
                            onMenuClick = { selectedTrackForMenu = track },
                            selectionMode = selectionMode,
                            isSelected = track.id in selectedTrackIds,
                            onLongClick = {
                                if (selectionMode) toggleSelection(track) else startSelection(track)
                            },
                            onPlayNext = { viewModel.playNext(track) }
                        )
                    }
                }
            }
            }

            if (selectionMode && selectedTracks.isNotEmpty()) {
                PlaylistSelectionBar(
                    selectedTracks = selectedTracks,
                    snackbarHostState = snackbarHostState,
                    onPlay = viewModel::playTracks,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onAddToPlaylist = { tracksForAddToPlaylist = it },
                    onFavorite = viewModel::addTracksToFavorites,
                    onRemove = { viewModel.removeTracks(it.map { track -> track.id }) },
                    onHide = viewModel::hideTracks,
                    onDone = ::exitSelection,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistSelectionBar(
    selectedTracks: List<Track>,
    snackbarHostState: SnackbarHostState,
    onPlay: (List<Track>) -> Unit,
    onPlayNext: (List<Track>) -> Unit,
    onAddToQueue: (List<Track>) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onFavorite: (List<Track>) -> Unit,
    onRemove: (List<Track>) -> Unit,
    onHide: (List<Track>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val count = selectedTracks.size

    fun notify(res: Int) {
        scope.launch { snackbarHostState.showSnackbar(context.getString(res, count)) }
    }

    SelectionActionBar(
        actions = listOf(
            SelectionAction(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(R.string.selection_play),
                onClick = {
                    onPlay(selectedTracks)
                    onDone()
                }
            ),
            SelectionAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = stringResource(R.string.action_play_next),
                onClick = {
                    onPlayNext(selectedTracks)
                    notify(R.string.selection_playing_next)
                    onDone()
                }
            ),
            SelectionAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = stringResource(R.string.action_add_to_queue),
                onClick = {
                    onAddToQueue(selectedTracks)
                    notify(R.string.selection_added_to_queue)
                    onDone()
                }
            ),
            SelectionAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                label = stringResource(R.string.action_add_to_playlist),
                onClick = { onAddToPlaylist(selectedTracks) }
            ),
            SelectionAction(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.selection_favorite),
                onClick = {
                    onFavorite(selectedTracks)
                    notify(R.string.selection_added_to_favorites)
                    onDone()
                }
            ),
            SelectionAction(
                icon = Icons.Filled.DeleteOutline,
                label = stringResource(R.string.action_remove_from_playlist),
                onClick = {
                    onRemove(selectedTracks)
                    notify(R.string.selection_removed_from_playlist)
                    onDone()
                }
            ),
            SelectionAction(
                icon = Icons.Filled.VisibilityOff,
                label = stringResource(R.string.action_hide_track),
                onClick = {
                    onHide(selectedTracks)
                    notify(R.string.selection_hidden)
                    onDone()
                }
            )
        ),
        modifier = modifier
    )
}
