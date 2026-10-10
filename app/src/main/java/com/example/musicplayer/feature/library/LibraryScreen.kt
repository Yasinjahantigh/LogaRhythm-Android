package com.example.musicplayer.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.core.utils.PermissionUtils
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.core.utils.rememberMediaStoreConsent
import com.example.musicplayer.feature.player.EditTrackDialog
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.PlaylistsTabContent
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet
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
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(id = R.string.library_title), style = MaterialTheme.typography.headlineMedium)
                        if (uiState.hasPermission && uiState.tracks.isNotEmpty()) {
                            Text(text = stringResource(R.string.library_track_count, uiState.tracks.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
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
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = uiState.selectedTab.ordinal,
                            edgePadding = 12.dp,
                            modifier = Modifier.testTag("library_tabs")
                        ) {
                            LibraryTab.entries.forEach { tab ->
                                val isSelected = uiState.selectedTab == tab
                                Tab(
                                    selected = isSelected,
                                    onClick = { viewModel.selectTab(tab) },
                                    text = {
                                        Text(
                                            text = stringResource(tab.titleRes),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                                    onTrackMenuClick = { selectedTrackForMenu = it }
                                )
                                LibraryTab.ALBUMS -> AlbumsTabContent(albums = uiState.albums, onAlbumClick = onNavigateToAlbum)
                                LibraryTab.ARTISTS -> ArtistsTabContent(artists = uiState.artists, onArtistClick = onNavigateToArtist)
                                LibraryTab.GENRES -> GenresTabContent(genres = uiState.genres, onGenreClick = onNavigateToGenre)
                                LibraryTab.FOLDERS -> FoldersTabContent(folders = uiState.folders, onFolderClick = onNavigateToFolder)
                                LibraryTab.PLAYLISTS -> PlaylistsTabContent(onPlaylistClick = onNavigateToPlaylist)
                            }
                        }
                    }
                }
            }
        }
    }
}
