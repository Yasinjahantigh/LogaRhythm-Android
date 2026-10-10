package com.example.musicplayer.feature.search

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.core.utils.rememberMediaStoreConsent
import com.example.musicplayer.feature.player.EditTrackDialog
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.PlaylistViewModel
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var selectedTrackForEdit by remember { mutableStateOf<Track?>(null) }
    var editTrackError by remember { mutableStateOf(false) }
    var editTrackSaving by remember { mutableStateOf(false) }

    if (selectedTrackForMenu != null) {
        val menuTrack = selectedTrackForMenu!!
        SongMenuBottomSheet(
            track = menuTrack,
            onDismissRequest = { selectedTrackForMenu = null },
            onPlayNext = { viewModel.playNext(menuTrack) },
            onAddToQueue = { viewModel.addToQueue(menuTrack) },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            onDeleteTrack = {
                mediaStoreConsent.delete(menuTrack, onApproved = { approvedTrack ->
                    viewModel.deleteTrack(approvedTrack) { deleted ->
                        Toast.makeText(
                            context,
                            context.getString(if (deleted) R.string.song_deleted else R.string.track_delete_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, onCancelled = {
                    Toast.makeText(context, context.getString(R.string.track_delete_failed), Toast.LENGTH_SHORT).show()
                })
                selectedTrackForMenu = null
            },
            onEditTrack = { selectedTrackForEdit = menuTrack },
            onGoToAlbum = if (menuTrack.album.isNotBlank() && menuTrack.album != "Unknown Album") {
                { onNavigateToAlbum(menuTrack.album) }
            } else null,
            onGoToArtist = if (menuTrack.artist.isNotBlank() && menuTrack.artist != "Unknown Artist") {
                { onNavigateToArtist(menuTrack.artist) }
            } else null
        )
    }

    selectedTrackForEdit?.let { track ->
        EditTrackDialog(
            track = track,
            onDismiss = { selectedTrackForEdit = null; editTrackError = false; editTrackSaving = false },
            errorMessage = if (editTrackError) context.getString(R.string.track_metadata_write_failed) else null,
            isSaving = editTrackSaving,
            onSave = { newTitle, newArtist, newAlbum, newGenre ->
                if (!editTrackSaving) {
                    editTrackSaving = true
                    editTrackError = false
                    mediaStoreConsent.write(track, onApproved = {
                        viewModel.updateTrackMetadata(track.id, newTitle, newArtist, newAlbum, newGenre) { updated ->
                            editTrackSaving = false
                            if (updated != null) {
                                selectedTrackForEdit = null
                                Toast.makeText(context, context.getString(R.string.track_updated_toast), Toast.LENGTH_SHORT).show()
                            } else editTrackError = true
                        }
                    }, onCancelled = {
                        editTrackSaving = false
                        editTrackError = true
                    })
                }
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
        modifier = modifier.fillMaxSize().testTag("search_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.search_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_text_field"),
                placeholder = {
                    Text(text = stringResource(R.string.search_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search_icon)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::clearQuery,
                            modifier = Modifier.testTag("clear_search_query_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.saveRecentQuery(query)
                        focusManager.clearFocus()
                    }
                )
            )

            if (query.trim().isEmpty()) {
                RecentSearchesView(
                    recentSearches = recentSearches,
                    onSelectRecent = { term ->
                        viewModel.onSelectRecentSearch(term)
                        focusManager.clearFocus()
                    },
                    onRemoveRecent = viewModel::onRemoveRecentSearch,
                    onClearAll = viewModel::onClearRecentSearches
                )
            } else {
                SearchResultsView(
                    query = query,
                    results = results,
                    onTrackClick = { track ->
                        viewModel.playTrack(track, results.songs)
                    },
                    onAlbumClick = { albumName ->
                        viewModel.saveRecentQuery(query)
                        onNavigateToAlbum(albumName)
                    },
                    onArtistClick = { artistName ->
                        viewModel.saveRecentQuery(query)
                        onNavigateToArtist(artistName)
                    },
                    onClearQuery = viewModel::clearQuery,
                    onTrackMenuClick = { track ->
                        selectedTrackForMenu = track
                    }
                )
            }
        }
    }
}
}
