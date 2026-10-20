package com.yasinjahantigh.logarhythm.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.PermissionUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import com.yasinjahantigh.logarhythm.feature.player.EditTrackDialog
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistsTabContent
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet
import com.yasinjahantigh.logarhythm.ui.components.SearchPill
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onNavigateToGenre: (String) -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val categoryCounts = remember(uiState.tracks, uiState.albums, uiState.artists, uiState.genres, uiState.folders, allPlaylists) {
        mapOf(
            LibraryTab.SONGS to uiState.tracks.size,
            LibraryTab.ALBUMS to uiState.albums.size,
            LibraryTab.ARTISTS to uiState.artists.size,
            LibraryTab.PLAYLISTS to allPlaylists.size,
            LibraryTab.GENRES to uiState.genres.size,
            LibraryTab.FOLDERS to uiState.folders.size
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()

    LaunchedEffect(viewModel) {
        viewModel.scanErrors.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.library_scan_failed))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.scanSuccess.collect { count ->
            snackbarHostState.showSnackbar(context.getString(R.string.library_refreshed_toast, count))
        }
    }

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var selectedTrackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var selectedTrackForEdit by remember { mutableStateOf<Track?>(null) }
    var editTrackError by remember { mutableStateOf(false) }
    var editTrackSaving by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> viewModel.onPermissionResult(isGranted) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.checkPermissionAndLoad()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentSort = uiState.sortOption,
            onSortSelected = { newSort ->
                viewModel.setSortOption(newSort)
            },
            onDismissRequest = { showSortSheet = false }
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
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
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
            onAddToPlaylist = { selectedTrackForAddToPlaylist = track },
            onDeleteTrack = {
                mediaStoreConsent.delete(track, onApproved = { approvedTrack ->
                    viewModel.deleteTrack(approvedTrack) { deleted ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(if (deleted) R.string.song_deleted else R.string.track_delete_failed)
                            )
                        }
                    }
                }, onCancelled = {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.track_delete_failed)) }
                })
            },
            onEditTrack = { selectedTrackForEdit = track },
            onGoToAlbum = if (track.album.isNotBlank() && track.album != "Unknown Album") {
                { onNavigateToAlbum(track.album) }
            } else null,
            onGoToArtist = if (track.artist.isNotBlank() && track.artist != "Unknown Artist") {
                { onNavigateToArtist(track.artist) }
            } else null,
            onHideTrack = {
                viewModel.hideTrack(track)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.track_hidden_toast, track.title))
                }
            }
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
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.track_updated_toast)) }
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

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("library_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalPlaybackBottomPadding.current)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Header: one big search pill (search is not a tab any more) with sort + rescan beside it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchPill(
                    onClick = onNavigateToSearch,
                    modifier = Modifier.weight(1f),
                    tag = "library_search_pill"
                )
                if (uiState.hasPermission) {
                    IconButton(onClick = { showSortSheet = true }, modifier = Modifier.testTag("sort_action_button")) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_by_title))
                    }
                    IconButton(
                        onClick = { viewModel.refresh(true) },
                        enabled = !uiState.isRefreshing,
                        modifier = Modifier.testTag("refresh_action_button")
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_rescan))
                        }
                    }
                }
            }

            when {
                !uiState.hasPermission -> LibraryPermissionState(onRequestPermission = { permissionLauncher.launch(PermissionUtils.getRequiredAudioPermission()) })
                uiState.isLoading && uiState.tracks.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = stringResource(R.string.library_scanning), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                uiState.tracks.isEmpty() -> LibraryEmptyState(onRescan = { viewModel.refresh(false) })
                else -> {
                    LibraryCategoryRail(
                        selected = uiState.selectedTab,
                        counts = categoryCounts,
                        onSelect = viewModel::selectTab
                    )
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh(true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        when (uiState.selectedTab) {
                            LibraryTab.SONGS -> SongsTabContent(
                                tracks = uiState.tracks,
                                currentTrackId = currentTrack?.id,
                                isPlaying = isPlaying,
                                onTrackClick = viewModel::playTrack,
                                onTrackMenuClick = { selectedTrackForMenu = it },
                                onPlayAll = viewModel::playAll,
                                onShuffleAll = viewModel::shuffleAll
                            )
                            LibraryTab.ALBUMS -> AlbumsTabContent(albums = uiState.albums, onAlbumClick = onNavigateToAlbum)
                            LibraryTab.ARTISTS -> ArtistsTabContent(artists = uiState.artists, onArtistClick = onNavigateToArtist)
                            LibraryTab.PLAYLISTS -> PlaylistsTabContent(onPlaylistClick = onNavigateToPlaylist)
                            LibraryTab.GENRES -> GenresTabContent(genres = uiState.genres, onGenreClick = onNavigateToGenre)
                            LibraryTab.FOLDERS -> FoldersTabContent(folders = uiState.folders, onFolderClick = onNavigateToFolder)
                        }
                    }
                }
            }
        }
    }
}
