package com.yasinjahantigh.logarhythm.feature.search

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToTagEditor: (String) -> Unit = {},
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
            onEditTrack = { onNavigateToTagEditor(menuTrack.id) },
            onGoToAlbum = if (menuTrack.album.isNotBlank() && menuTrack.album != "Unknown Album") {
                { onNavigateToAlbum(menuTrack.album) }
            } else null,
            onGoToArtist = if (menuTrack.artist.isNotBlank() && menuTrack.artist != "Unknown Artist") {
                { artistName -> onNavigateToArtist(artistName) }
            } else null,
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

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("search_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
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
            // Search is a focused, full-screen task: back arrow + field, keyboard opens at once.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("search_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_text_field"),
                    placeholder = {
                        Text(text = stringResource(R.string.search_placeholder), maxLines = 1)
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
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.saveRecentQuery(query)
                            focusManager.clearFocus()
                        }
                    )
                )
            }

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
