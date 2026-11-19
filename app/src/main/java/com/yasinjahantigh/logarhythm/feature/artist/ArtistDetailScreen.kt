package com.yasinjahantigh.logarhythm.feature.artist

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.library.LibraryEmptyHint
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.DetailHero
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding

@Composable
fun ArtistDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAlbum: ((String, String) -> Unit)? = null,
    onNavigateToArtist: ((String) -> Unit)? = null,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isFollowed.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    val onlineTracks by viewModel.onlineTracks.collectAsStateWithLifecycle()
    val onlineAlbums by viewModel.onlineAlbums.collectAsStateWithLifecycle()
    val onlineLoading by viewModel.onlineLoading.collectAsStateWithLifecycle()
    val onlineLoadingMore by viewModel.onlineLoadingMore.collectAsStateWithLifecycle()
    val onlineSuggestVpn by viewModel.onlineSuggestVpn.collectAsStateWithLifecycle()
    val openedOnlineAlbum by viewModel.openedOnlineAlbum.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val downloadHistory by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val artistPhotoUri by viewModel.artistPhotoUri.collectAsStateWithLifecycle()
    val photoCandidates by viewModel.photoCandidates.collectAsStateWithLifecycle()
    val searchingPhotos by viewModel.searchingPhotos.collectAsStateWithLifecycle()

    val topTracks = profile?.topTracks.orEmpty()
    val albums = profile?.albums.orEmpty()
    val appearsOn = profile?.appearsOn.orEmpty()

    // 0 = In Library, 1 = Online Discography & Downloads
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var onlineFilterQuery by rememberSaveable { mutableStateOf("") }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var inspectedOnlineTrack by remember { mutableStateOf<DownloadSearchResult?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showEditPhotoSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.loadOnlineCatalog(force = false)
        }
    }

    val historyByUrl = remember(downloadHistory) {
        downloadHistory.associateBy { it.sourceUrl }
    }

    val localTracksByNormalizedTitle = remember(tracks) {
        tracks.associateBy { normalizeTrackTitleForMatch(it.title) }
    }

    if (selectedTrackForMenu != null) {
        val menuTrack = selectedTrackForMenu!!
        SongMenuBottomSheet(
            track = menuTrack,
            onDismissRequest = { selectedTrackForMenu = null },
            onPlayNext = { viewModel.playNext(menuTrack) },
            onAddToQueue = { viewModel.addToQueue(menuTrack) },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            onGoToAlbum = if (onNavigateToAlbum != null && menuTrack.album.isNotBlank() && menuTrack.album != "Unknown Album") {
                { onNavigateToAlbum(menuTrack.album, menuTrack.albumArtist.ifBlank { menuTrack.artist }) }
            } else null,
            onGoToArtist = if (onNavigateToArtist != null && menuTrack.artist.isNotBlank() && menuTrack.artist != "Unknown Artist") {
                { artistName -> onNavigateToArtist(artistName) }
            } else null,
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
            },
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

    val displayArtistName = if (viewModel.artistName.isBlank() ||
        viewModel.artistName.equals("<unknown>", ignoreCase = true) ||
        viewModel.artistName.equals("unknown", ignoreCase = true) ||
        viewModel.artistName.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else viewModel.artistName

    if (showEditPhotoSheet) {
        EditArtistPhotoSheet(
            artistName = displayArtistName,
            hasCustomOrFetchedPhoto = !artistPhotoUri.isNullOrBlank(),
            candidates = photoCandidates,
            searching = searchingPhotos,
            onSearch = viewModel::searchArtistPhotos,
            onSelectOnlineUrl = viewModel::setArtistPhotoUrl,
            onSelectGalleryBytes = viewModel::setArtistPhotoBytes,
            onResetPhoto = viewModel::resetArtistPhoto,
            onDismiss = { showEditPhotoSheet = false }
        )
    }

    openedOnlineAlbum?.let { albumDetail ->
        com.yasinjahantigh.logarhythm.feature.download.DownloadCollectionSheet(
            state = albumDetail,
            history = downloadHistory,
            previewState = previewState,
            onTogglePreview = viewModel::togglePreview,
            onDismiss = viewModel::closeOnlineAlbum,
            onRefresh = { viewModel.openOnlineAlbum(albumDetail.header, forceRefresh = true) },
            onDownloadAll = { albumTracks ->
                viewModel.downloadOnlineBatch(albumTracks) { count ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.artist_online_batch_queued_toast, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                viewModel.closeOnlineAlbum()
            },
            onTrackClick = { tr -> inspectedOnlineTrack = tr },
            onTrackDownload = { tr ->
                viewModel.downloadOnlineTrack(tr) {
                    Toast.makeText(context, context.getString(R.string.download_added_to_queue), Toast.LENGTH_SHORT).show()
                }
            },
            onAlbumClick = { nested -> viewModel.openOnlineAlbum(nested) }
        )
    }

    inspectedOnlineTrack?.let { result ->
        val localMatch = findMatchingLocalTrack(
            onlineTitle = result.title,
            localByNormalizedTitle = localTracksByNormalizedTitle,
            allLocalTracks = tracks
        )
        val activeItem = historyByUrl[result.url]
        val pKey = viewModel.previewKeyFor(result)
        val isPreviewPlaying = previewState.trackKey == pKey && previewState.isPlaying
        val isPreviewLoading = previewState.trackKey == pKey && previewState.isLoading
        com.yasinjahantigh.logarhythm.feature.download.OnlineTrackDetailSheet(
            result = result,
            activeItem = activeItem,
            isOnDevice = localMatch != null,
            isPreviewLoading = isPreviewLoading,
            isPreviewPlaying = isPreviewPlaying,
            onTogglePreview = { viewModel.togglePreview(result) },
            onPlayLocal = localMatch?.let { local -> { viewModel.playTrack(local) } },
            onDownload = {
                viewModel.downloadOnlineTrack(result) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.download_added_to_queue),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { inspectedOnlineTrack = null }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("artist_detail_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { innerPadding ->
        val bottomPadding = LocalPlaybackBottomPadding.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding + 20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp)
            ) {
                item(key = "hero") {
                    DetailHero(
                        title = displayArtistName,
                        meta = stringResource(
                            R.string.artist_metadata,
                            tracks.size,
                            profile?.artist?.albumCount ?: albums.size
                        ),
                        artworkUri = artistPhotoUri
                            ?: tracks.firstOrNull()?.artworkUri
                            ?: onlineTracks.firstOrNull()?.thumbnailUrl,
                        round = true,
                        onBack = onNavigateBack,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::shuffleAll,
                        actionsEnabled = tracks.isNotEmpty(),
                        backTag = "artist_back_button",
                        playTag = "artist_play_button",
                        shuffleTag = "artist_shuffle_button",
                        onEditArtwork = { showEditPhotoSheet = true },
                        topEndAction = {
                            if (isFollowed) {
                                FilledTonalButton(
                                    onClick = viewModel::toggleFollow,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.artist_following),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = viewModel::toggleFollow,
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.artist_follow),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    )
                }

                // Segmented Tab Switch: In Library vs Online
                item(key = "artist_mode_tabs") {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("artist_tab_row")
                    ) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("artist_tab_library")
                        ) {
                            Text(stringResource(R.string.artist_tab_library))
                        }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("artist_tab_online")
                        ) {
                            Text(stringResource(R.string.artist_tab_online))
                        }
                    }
                }

                item(key = "artist_micro_tip") {
                    com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                        hintId = com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.ARTIST_DETAIL,
                        text = stringResource(R.string.tip_artist_detail),
                        icon = Icons.Filled.Public,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (selectedTab == 0) {
                    if (tracks.isEmpty()) {
                        item(key = "empty_local") {
                            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                                LibraryEmptyHint(
                                    text = stringResource(R.string.empty_artist_tracks),
                                    icon = Icons.Filled.Person,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        if (topTracks.isNotEmpty()) {
                            item(key = "header_top") {
                                ArtistSectionHeader(stringResource(R.string.artist_section_top_tracks))
                            }
                            items(items = topTracks, key = { "top_${it.id}" }) { track ->
                                ArtistTrackRow(track, currentTrack, isPlaying, viewModel) {
                                    selectedTrackForMenu = track
                                }
                            }
                        }

                        if (albums.isNotEmpty()) {
                            item(key = "header_albums") {
                                ArtistSectionHeader(stringResource(R.string.artist_section_albums))
                            }
                            item(key = "albums_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(items = albums, key = { it.id }) { album ->
                                        ArtistAlbumCard(album = album, onNavigateToAlbum = onNavigateToAlbum)
                                    }
                                }
                            }
                        }

                        if (appearsOn.isNotEmpty()) {
                            item(key = "header_appears_on") {
                                ArtistSectionHeader(stringResource(R.string.artist_section_appears_on))
                            }
                            items(items = appearsOn, key = { "appears_${it.id}" }) { track ->
                                ArtistTrackRow(track, currentTrack, isPlaying, viewModel) {
                                    selectedTrackForMenu = track
                                }
                            }
                        }

                        item(key = "header_all") {
                            ArtistSectionHeader(stringResource(R.string.artist_section_all_tracks))
                        }
                        items(items = tracks, key = { "all_${it.id}" }) { track ->
                            ArtistTrackRow(track, currentTrack, isPlaying, viewModel) {
                                selectedTrackForMenu = track
                            }
                        }
                    }
                } else {
                    // ONLINE TAB
                    item(key = "online_vpn_banner") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VpnLock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.vpn_proactive_online_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.loadOnlineCatalog(force = true) },
                                    enabled = !onlineLoading,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.radar_refresh_action),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (onlineLoading && onlineTracks.isNotEmpty()) {
                        item(key = "online_loading_bar") {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (onlineLoading && onlineTracks.isEmpty() && onlineAlbums.isEmpty()) {
                        item(key = "online_loading_spinner") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.artist_online_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (!onlineLoading && onlineTracks.isEmpty() && onlineAlbums.isEmpty()) {
                        item(key = "online_empty_vpn") {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.VpnLock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.artist_online_empty_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            if (onlineSuggestVpn) R.string.artist_online_vpn_error
                                            else R.string.artist_online_empty_desc
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { viewModel.loadOnlineCatalog(force = true) },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.download_retry))
                                    }
                                }
                            }
                        }
                    } else {
                        if (onlineLoadingMore) {
                            item(key = "online_loading_more") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.artist_online_loading_more, onlineTracks.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                    )
                                }
                            }
                        }

                        if (onlineAlbums.isNotEmpty() && onlineFilterQuery.isBlank()) {
                            item(key = "online_albums_header") {
                                ArtistSectionHeader("${stringResource(R.string.artist_online_albums_header)} (${onlineAlbums.size})")
                            }
                            item(key = "online_albums_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = onlineAlbums,
                                        key = { "online_alb_${it.source.raw}_${it.id}" }
                                    ) { album ->
                                        OnlineAlbumCard(
                                            album = album,
                                            onClick = { viewModel.openOnlineAlbum(album) }
                                        )
                                    }
                                }
                            }
                        }

                        if (onlineTracks.isNotEmpty()) {
                            val filteredOnlineTracks = if (onlineFilterQuery.isBlank()) {
                                onlineTracks
                            } else {
                                val q = onlineFilterQuery.trim()
                                onlineTracks.filter {
                                    it.title.contains(q, ignoreCase = true) ||
                                        it.album.contains(q, ignoreCase = true)
                                }
                            }

                            val missingOnlineTracks = filteredOnlineTracks.filter { res ->
                                val hasLocal = findMatchingLocalTrack(
                                    onlineTitle = res.title,
                                    localByNormalizedTitle = localTracksByNormalizedTitle,
                                    allLocalTracks = tracks
                                ) != null
                                val dlStatus = historyByUrl[res.url]?.status
                                !hasLocal &&
                                    dlStatus != DownloadStatus.SAVED &&
                                    dlStatus != DownloadStatus.QUEUED &&
                                    dlStatus != DownloadStatus.DOWNLOADING &&
                                    dlStatus != DownloadStatus.SAVING
                            }

                            item(key = "online_tracks_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.artist_online_tracks_header)} (${filteredOnlineTracks.size})",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (missingOnlineTracks.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.downloadOnlineBatch(missingOnlineTracks) { count ->
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.artist_online_batch_queued_toast, count),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("artist_download_missing_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(
                                                    R.string.artist_online_download_missing_btn,
                                                    missingOnlineTracks.size
                                                ),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }

                            if (onlineTracks.size > 10) {
                                item(key = "online_tracks_filter") {
                                    OutlinedTextField(
                                        value = onlineFilterQuery,
                                        onValueChange = { onlineFilterQuery = it },
                                        placeholder = { Text(stringResource(R.string.artist_online_filter_hint)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        trailingIcon = if (onlineFilterQuery.isNotEmpty()) {
                                            {
                                                IconButton(onClick = { onlineFilterQuery = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = stringResource(R.string.btn_close),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else null,
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            items(
                                items = filteredOnlineTracks,
                                key = { "online_trk_${it.source.raw}_${it.id}" }
                            ) { result ->
                                val localMatch = findMatchingLocalTrack(
                                    onlineTitle = result.title,
                                    localByNormalizedTitle = localTracksByNormalizedTitle,
                                    allLocalTracks = tracks
                                )
                                val previewKey = viewModel.previewKeyFor(result)
                                val isLocalCurrentPlaying = localMatch != null && currentTrack?.id == localMatch.id && isPlaying
                                val isPreviewPlaying = if (localMatch != null) {
                                    isLocalCurrentPlaying
                                } else {
                                    previewState.trackKey == previewKey && previewState.isPlaying
                                }
                                val isPreviewLoading = localMatch == null &&
                                    previewState.trackKey == previewKey &&
                                    previewState.isLoading
                                val downloadItem = historyByUrl[result.url]
                                OnlineArtistTrackRow(
                                    result = result,
                                    localMatch = localMatch,
                                    isPreviewPlaying = isPreviewPlaying,
                                    isPreviewLoading = isPreviewLoading,
                                    downloadStatus = downloadItem?.status,
                                    onInspect = { inspectedOnlineTrack = result },
                                    onTogglePreview = {
                                        if (localMatch != null) {
                                            viewModel.playTrack(localMatch)
                                        } else {
                                            viewModel.togglePreview(result)
                                        }
                                    },
                                    onDownload = {
                                        viewModel.downloadOnlineTrack(result) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.download_added_to_queue),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineAlbumCard(
    album: DownloadSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        ArtworkImage(
            uri = album.thumbnailUrl,
            fallbackKey = album.title,
            modifier = Modifier.size(136.dp),
            shape = RoundedCornerShape(18.dp),
            glyphSize = 34.sp,
            contentDescription = album.title
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OnlineArtistTrackRow(
    result: DownloadSearchResult,
    localMatch: Track?,
    isPreviewPlaying: Boolean,
    isPreviewLoading: Boolean,
    downloadStatus: DownloadStatus?,
    onInspect: () -> Unit,
    onTogglePreview: () -> Unit,
    onDownload: () -> Unit
) {
    val isOnDevice = localMatch != null || downloadStatus == DownloadStatus.SAVED
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onInspect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onTogglePreview),
                contentAlignment = Alignment.Center
            ) {
                ArtworkImage(
                    uri = localMatch?.artworkUri ?: result.thumbnailUrl,
                    fallbackKey = result.title,
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    contentDescription = result.title
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isPreviewLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPreviewPlaying) R.string.cd_pause else R.string.cd_play
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sourceLabel = when (result.source) {
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.RADIOJAVAN -> "RadioJavan"
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.DEEZER -> "Deezer"
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.MUSICMAN -> "MusicMan"
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.YOUTUBE -> "YouTube"
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.SOUNDCLOUD -> "SoundCloud"
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.SAAVN -> "JioSaavn"
                    com.yasinjahantigh.logarhythm.domain.model.DownloadSource.BANDCAMP -> "Bandcamp"
                }
                val metaParts = buildList {
                    if (result.artist.isNotBlank()) add(result.artist)
                    if (result.durationMs > 0L) add(TimeUtils.formatDuration(result.durationMs))
                    if (isOnDevice) add(stringResource(R.string.artist_online_on_device))
                    else add(sourceLabel)
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOnDevice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tracks already on the phone are shown cleanly without any download icon.
            if (!isOnDevice) {
                when (downloadStatus) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.SAVING -> {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("artist_online_download_${result.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = stringResource(R.string.radar_notif_download),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.artist_online_on_device),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.artist_online_on_device),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private val TITLE_PARENS_REGEX = Regex("""\s*[\(\[].*?[\)\]]\s*""")
private val TITLE_NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")

private fun normalizeTrackTitleForMatch(raw: String): String {
    val base = com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarService.normalizeArtistKey(raw)
        .replace(TITLE_PARENS_REGEX, " ")
        .replace(TITLE_NON_WORD_REGEX, " ")
        .trim()
    return base
}

private fun findMatchingLocalTrack(
    onlineTitle: String,
    localByNormalizedTitle: Map<String, Track>,
    allLocalTracks: List<Track>
): Track? {
    val target = normalizeTrackTitleForMatch(onlineTitle)
    if (target.isBlank()) return null
    localByNormalizedTitle[target]?.let { return it }
    if (target.length < 3) return null
    return allLocalTracks.firstOrNull { local ->
        val localKey = normalizeTrackTitleForMatch(local.title)
        localKey.length >= 3 && (
            localKey == target ||
                com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge.similarity(localKey, target) >= 0.88
            )
    }
}

@Composable
private fun ArtistTrackRow(
    track: Track,
    currentTrack: Track?,
    isPlaying: Boolean,
    viewModel: ArtistDetailViewModel,
    onMenu: () -> Unit
) {
    val isCurrent = currentTrack?.id == track.id
    TrackListItem(
        track = track,
        onClick = { viewModel.playTrack(track) },
        isCurrentTrack = isCurrent,
        isPlaying = isCurrent && isPlaying,
        onMenuClick = onMenu,
        onPlayNext = { viewModel.playNext(track) }
    )
}

@Composable
private fun ArtistSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun ArtistAlbumCard(album: Album, onNavigateToAlbum: ((String, String) -> Unit)?) {
    val name = album.name.cleanMetaOrNull() ?: stringResource(R.string.unknown_album)
    Column(
        modifier = Modifier
            .width(138.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = onNavigateToAlbum != null) { onNavigateToAlbum?.invoke(album.name, album.artist) }
    ) {
        ArtworkImage(
            uri = album.artworkUri,
            fallbackKey = name,
            modifier = Modifier.size(138.dp),
            shape = RoundedCornerShape(18.dp),
            glyphSize = 34.sp,
            contentDescription = stringResource(R.string.cd_album_item, name)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (album.year > 0) album.year.toString() else stringResource(
                R.string.album_track_count,
                album.trackCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
