package com.yasinjahantigh.logarhythm.feature.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import com.yasinjahantigh.logarhythm.feature.ai.MagicPlaylistSheet
import com.yasinjahantigh.logarhythm.feature.smart.SmartMixesSection
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding

@Composable
fun PlaylistsTabContent(
    onPlaylistClick: (Long) -> Unit,
    onSmartMixClick: (SmartMixType) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoritePlaylistIds.collectAsStateWithLifecycle()
    val smartMixes by viewModel.smartMixes.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showMagicPlaylist by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val builtInLabel = stringResource(R.string.playlist_favorites)
    val filteredPlaylists = remember(playlists, searchQuery, builtInLabel) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) playlists
        else playlists.filter { pl ->
            val label = if (pl.isBuiltIn) builtInLabel else pl.name
            label.lowercase().contains(q)
        }
    }
    val pinnedFavorites = remember(filteredPlaylists, favoriteIds) {
        filteredPlaylists.filter { !it.isBuiltIn && it.id in favoriteIds }
    }
    val builtInPlaylists = remember(filteredPlaylists) {
        filteredPlaylists.filter { it.isBuiltIn }
    }
    val regularPlaylists = remember(filteredPlaylists, favoriteIds) {
        filteredPlaylists.filter { !it.isBuiltIn && it.id !in favoriteIds }
    }

    if (showMagicPlaylist) {
        MagicPlaylistSheet(
            onDismiss = { showMagicPlaylist = false },
            onOpenSettings = {
                showMagicPlaylist = false
                onOpenAiSettings()
            }
        )
    }

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

    val bottomPadding = LocalPlaybackBottomPadding.current
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("playlists_tab_content")
    ) {
        item {
            SmartMixesSection(
                mixes = smartMixes,
                onMixClick = { onSmartMixClick(it.type) },
                onMagicPlaylist = { showMagicPlaylist = true },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tab_playlists),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        },
                        modifier = Modifier.testTag("playlists_tab_search_button")
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.playlist_search_action)
                        )
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("create_new_playlist_card")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.playlist_create),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.playlists_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("playlists_tab_search_field")
                )
            }
        }

        items(builtInPlaylists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
                onRename = { playlistToRename = playlist },
                onDelete = { playlistToDelete = playlist }
            )
        }

        if (pinnedFavorites.isNotEmpty()) {
            item(key = "pinned_playlists_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.playlists_pinned_header),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            items(pinnedFavorites, key = { "pinned_${it.id}" }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) },
                    onRename = { playlistToRename = playlist },
                    onDelete = { playlistToDelete = playlist },
                    isFavorite = true,
                    onToggleFavorite = { viewModel.toggleFavoritePlaylist(playlist.id) }
                )
            }
        }

        items(regularPlaylists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
                onRename = { playlistToRename = playlist },
                onDelete = { playlistToDelete = playlist },
                isFavorite = false,
                onToggleFavorite = { viewModel.toggleFavoritePlaylist(playlist.id) }
            )
        }
    }
}
