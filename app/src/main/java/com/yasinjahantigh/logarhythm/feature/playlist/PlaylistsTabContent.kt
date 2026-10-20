package com.yasinjahantigh.logarhythm.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Playlist

@Composable
fun PlaylistsTabContent(
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createPlaylist(name)
            }
        )
    }

    playlistToRename?.let { playlist ->
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlist.id, newName)
                playlistToRename = null
            }
        )
    }

    playlistToDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                viewModel.deletePlaylist(playlist.id)
                playlistToDelete = null
            }
        )
    }

    val bottomPadding = com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding.current
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("playlists_tab_content")
    ) {
        item {
            Surface(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("create_new_playlist_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.playlist_create),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.playlist_create),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
                onRename = { playlistToRename = playlist },
                onDelete = { playlistToDelete = playlist }
            )
        }
    }
}
