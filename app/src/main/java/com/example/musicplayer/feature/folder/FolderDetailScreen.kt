package com.example.musicplayer.feature.folder

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import com.example.musicplayer.ui.components.DetailHero
import com.example.R
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.library.TrackListItem
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.PlaylistViewModel
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showExcludeDialog) {
        AlertDialog(
            onDismissRequest = { showExcludeDialog = false },
            title = { Text(stringResource(R.string.dialog_exclude_folder_title)) },
            text = { Text(stringResource(R.string.dialog_exclude_folder_confirm, viewModel.folderName)) },
            confirmButton = {
                Button(
                    onClick = {
                        showExcludeDialog = false
                        viewModel.excludeFolder()
                        Toast.makeText(context, context.getString(R.string.folder_excluded_toast, viewModel.folderName), Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_folder_excluded_badge))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExcludeDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (selectedTrackForMenu != null) {
        val menuTrack = selectedTrackForMenu!!
        SongMenuBottomSheet(
            track = menuTrack,
            onDismissRequest = { selectedTrackForMenu = null },
            onPlayNext = { viewModel.playNext(menuTrack) },
            onAddToQueue = { viewModel.addToQueue(menuTrack) },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            onHideTrack = {
                viewModel.hideTrack(menuTrack)
                Toast.makeText(context, context.getString(R.string.track_hidden_toast, menuTrack.title), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddToPlaylistDialog && selectedTrackForMenu != null) {
        val targetTrack = selectedTrackForMenu!!
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { playlist ->
                playlistViewModel.addTrackToPlaylist(playlist.id, targetTrack.id)
                showAddToPlaylistDialog = false
                Toast.makeText(context, context.getString(R.string.added_to_playlist, playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylist = { name ->
                playlistViewModel.createPlaylist(name)
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("folder_detail_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { innerPadding ->
        val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                Column {
                    DetailHero(
                        title = viewModel.folderName,
                        meta = stringResource(R.string.folder_track_count, tracks.size),
                        artworkUri = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                        icon = Icons.Default.Folder,
                        onBack = onNavigateBack,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::shuffleAll,
                        actionsEnabled = tracks.isNotEmpty(),
                        backTag = "folder_back_button",
                        playTag = "folder_play_button",
                        shuffleTag = "folder_shuffle_button"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = { showExcludeDialog = true },
                            modifier = Modifier.testTag("folder_exclude_button")
                        ) {
                            Icon(Icons.Filled.FolderOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_exclude_folder))
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty_folder_tracks),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(items = tracks, key = { it.id }) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    TrackListItem(
                        track = track,
                        onClick = { viewModel.playTrack(track) },
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onMenuClick = { selectedTrackForMenu = track }
                    )
                }
            }
        }
    }
}
