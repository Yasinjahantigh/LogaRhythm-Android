package com.yasinjahantigh.logarhythm.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
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
    onNavigateToAlbum: (String, String) -> Unit = { _, _ -> },
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onNavigateToGenre: (String) -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToSmartMix: (SmartMixType) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToTagEditor: (String) -> Unit = {},
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
    var tracksForAddToPlaylist by remember { mutableStateOf<List<Track>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedTracks = remember(uiState.visibleTracks, selectedTrackIds) {
        uiState.visibleTracks.filter { it.id in selectedTrackIds }
    }
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
                val reshuffle = newSort.field == SortField.RANDOM &&
                    uiState.sortOption.field == SortField.RANDOM
                viewModel.setSortOption(newSort, reshuffle = reshuffle)
            },
            onDismissRequest = { showSortSheet = false },
            availableFields = SortField.fieldsFor(uiState.selectedTab)
        )
    }

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

    tracksForAddToPlaylist?.let { tracks ->
        AddToPlaylistDialog(
            playlists = allPlaylists,
            onDismiss = { tracksForAddToPlaylist = null },
            onSelectPlaylist = { target ->
                viewModel.addTracksToPlaylist(target.id, tracks.map { it.id })
                tracksForAddToPlaylist = null
                exitSelection()
                scope.launch {
                    if (tracks.size == 1) {
                        snackbarHostState.showSnackbar(context.getString(R.string.added_to_playlist, target.name))
                    } else {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.selection_added_to_playlist, tracks.size, target.name)
                        )
                    }
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
            onAddToPlaylist = { tracksForAddToPlaylist = listOf(track) },
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
            onEditTrack = { onNavigateToTagEditor(track.id) },
            onGoToAlbum = if (track.album.isNotBlank() && track.album != "Unknown Album") {
                { onNavigateToAlbum(track.album, track.albumArtist.ifBlank { track.artist }) }
            } else null,
            onGoToArtist = if (track.artist.isNotBlank() && track.artist != "Unknown Artist") {
                { artistName -> onNavigateToArtist(artistName) }
            } else null,
            onHideTrack = {
                viewModel.hideTrack(track)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.track_hidden_toast, track.title))
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (selectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedTracks.size,
                        allSelected = uiState.visibleTracks.isNotEmpty() &&
                            selectedTracks.size == uiState.visibleTracks.size,
                        onClose = ::exitSelection,
                        onToggleSelectAll = {
                            if (selectedTracks.size == uiState.visibleTracks.size) {
                                exitSelection()
                            } else {
                                selectedTrackIds = uiState.visibleTracks.map { it.id }.toSet()
                            }
                        }
                    )
                } else {
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
                            onSelect = { tab ->
                                exitSelection()
                                viewModel.selectTab(tab)
                            }
                        )
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { viewModel.refresh(true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            when (uiState.selectedTab) {
                                LibraryTab.SONGS -> SongsTabContent(
                                    tracks = uiState.visibleTracks,
                                    currentTrackId = currentTrack?.id,
                                    isPlaying = isPlaying,
                                    onTrackClick = viewModel::playTrack,
                                    onTrackMenuClick = { selectedTrackForMenu = it },
                                    onPlayAll = viewModel::playAll,
                                    onShuffleAll = viewModel::shuffleAll,
                                    selectionMode = selectionMode,
                                    selectedTrackIds = selectedTrackIds,
                                    onTrackLongClick = { track ->
                                        if (selectionMode) toggleSelection(track) else startSelection(track)
                                    },
                                    onToggleSelect = ::toggleSelection,
                                    onPlayNext = { viewModel.playNext(it) },
                                    filter = uiState.trackFilter,
                                    onFilterChange = viewModel::setTrackFilter
                                )
                                LibraryTab.ALBUMS -> AlbumsTabContent(albums = uiState.albums, onAlbumClick = onNavigateToAlbum)
                                LibraryTab.ARTISTS -> ArtistsTabContent(artists = uiState.artists, onArtistClick = onNavigateToArtist)
                                LibraryTab.PLAYLISTS -> PlaylistsTabContent(
                                    onPlaylistClick = onNavigateToPlaylist,
                                    onSmartMixClick = onNavigateToSmartMix,
                                    onOpenAiSettings = onNavigateToSettings
                                )
                                LibraryTab.GENRES -> GenresTabContent(genres = uiState.genres, onGenreClick = onNavigateToGenre)
                                LibraryTab.FOLDERS -> FoldersTabContent(folders = uiState.folders, onFolderClick = onNavigateToFolder)
                            }
                        }
                    }
                }
            }

            if (selectionMode && selectedTracks.isNotEmpty()) {
                SelectionActionBar(
                    actions = listOf(
                        SelectionAction(
                            icon = Icons.Filled.PlayArrow,
                            label = stringResource(R.string.selection_play),
                            onClick = {
                                viewModel.playTracks(selectedTracks)
                                exitSelection()
                            }
                        ),
                        SelectionAction(
                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                            label = stringResource(R.string.action_play_next),
                            onClick = {
                                viewModel.playNext(selectedTracks)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.selection_playing_next, selectedTracks.size)
                                    )
                                }
                                exitSelection()
                            }
                        ),
                        SelectionAction(
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            label = stringResource(R.string.action_add_to_queue),
                            onClick = {
                                viewModel.addToQueue(selectedTracks)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.selection_added_to_queue, selectedTracks.size)
                                    )
                                }
                                exitSelection()
                            }
                        ),
                        SelectionAction(
                            icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            label = stringResource(R.string.action_add_to_playlist),
                            onClick = { tracksForAddToPlaylist = selectedTracks }
                        ),
                        SelectionAction(
                            icon = Icons.Filled.Favorite,
                            label = stringResource(R.string.selection_favorite),
                            onClick = {
                                viewModel.addTracksToFavorites(selectedTracks)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.selection_added_to_favorites, selectedTracks.size)
                                    )
                                }
                                exitSelection()
                            }
                        ),
                        SelectionAction(
                            icon = Icons.Filled.VisibilityOff,
                            label = stringResource(R.string.action_hide_track),
                            onClick = {
                                viewModel.hideTracks(selectedTracks)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.selection_hidden, selectedTracks.size)
                                    )
                                }
                                exitSelection()
                            }
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = LocalPlaybackBottomPadding.current + 10.dp)
                )
            }
        }
    }
}
