package com.yasinjahantigh.logarhythm.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yasinjahantigh.logarhythm.core.utils.PermissionUtils
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Lock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.model.DailyMix
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.player.EditTrackDialog
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet
import com.yasinjahantigh.logarhythm.ui.theme.DisplayFontFamily
import com.yasinjahantigh.logarhythm.domain.discovery.Discovery
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToStats: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToDownload: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val dailyMixes by viewModel.dailyMixes.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val totalTimeMs by viewModel.totalListenTimeMs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val rightNow by viewModel.rightNow.collectAsStateWithLifecycle()
    val onRepeat by viewModel.onRepeat.collectAsStateWithLifecycle()
    val becauseYouPlayed by viewModel.becauseYouPlayed.collectAsStateWithLifecycle()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var selectedTrackForEdit by remember { mutableStateOf<Track?>(null) }
    var editTrackError by remember { mutableStateOf(false) }
    var editTrackSaving by remember { mutableStateOf(false) }
    var selectedMixForDetail by remember { mutableStateOf<DailyMix?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()
    val emptyMoodMessage = stringResource(R.string.mood_empty)
    val sessionUnavailableMessage = stringResource(R.string.home_session_unavailable)

    val isLibraryEmpty = allTracks.isEmpty() && dailyMixes.isEmpty() && recentlyPlayed.isEmpty() && mostPlayed.isEmpty()

    var shelf by remember { mutableStateOf(JumpShelf.RECENT) }
    val shelves: List<Pair<JumpShelf, List<Track>>> = listOf(
        JumpShelf.RECENT to recentlyPlayed,
        JumpShelf.NEW to recentlyAdded,
        JumpShelf.REPEAT to onRepeat.ifEmpty { mostPlayed }
    ).filter { it.second.isNotEmpty() }
    val activeShelf = shelves.firstOrNull { it.first == shelf } ?: shelves.firstOrNull()

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding.current)
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen")
    ) { innerPadding ->
        if (isLibraryEmpty) {
            var audioGranted by remember { mutableStateOf(PermissionUtils.hasAudioPermission(context)) }
            var askedOnce by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                audioGranted = granted
                askedOnce = true
                if (granted) viewModel.rescanLibrary()
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (audioGranted) Icons.Filled.MusicNote else Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = if (audioGranted) R.string.home_title else R.string.permission_audio_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = if (audioGranted) R.string.home_empty_message else R.string.permission_audio_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (!audioGranted) {
                    Button(
                        onClick = {
                            if (askedOnce) {
                                // Android stops showing the prompt after a refusal; only Settings can grant it now.
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                )
                            } else {
                                permissionLauncher.launch(PermissionUtils.getRequiredAudioPermission())
                            }
                        },
                        modifier = Modifier.testTag("home_grant_permission_button")
                    ) {
                        Text(
                            text = stringResource(
                                id = if (askedOnce) R.string.permission_open_settings else R.string.permission_grant_button
                            )
                        )
                    }
                } else {
                    Button(
                        onClick = viewModel::rescanLibrary,
                        modifier = Modifier.testTag("home_rescan_button")
                    ) {
                        Text(text = stringResource(id = R.string.home_rescan))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onNavigateToLibrary,
                        modifier = Modifier.testTag("home_browse_library_button")
                    ) {
                        Text(text = stringResource(id = R.string.home_browse_library))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onNavigateToDownload,
                    modifier = Modifier.testTag("home_download_button")
                ) {
                    Text(text = stringResource(id = R.string.home_download_card_title))
                }
            }
        } else {
            val bottomPadding = com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .testTag("home_content_list"),
                    contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
                ) {
                    item(key = "header") {
                        HomeHeader(
                            title = rightNow?.let { greetingFor(it.daypart) }
                                ?: stringResource(id = R.string.home_title),
                            onSearchClick = onNavigateToSearch
                        )
                    }

                    rightNow?.takeIf { it.tracks.isNotEmpty() }?.let { now ->
                        item(key = "right_now") {
                            RightNowCard(rightNow = now, onPlay = viewModel::playRightNow)
                        }
                    }

                    if (allTracks.isNotEmpty()) {
                        item(key = "quick_start") {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            ListeningSessionCard(
                                onStartSession = { minutes ->
                                    viewModel.playListeningSession(minutes) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(sessionUnavailableMessage)
                                        }
                                    }
                                },
                                onShuffleAll = viewModel::shuffleAllTracks
                            )
                        }
                    }
                    }

                    item(key = "download_banner") {
                        HomeDownloadBanner(onClick = onNavigateToDownload)
                    }

                    // One shelf instead of three: recently played / new / on repeat share a row.
                    if (activeShelf != null) {
                        item(key = "jump_back_in") {
                        Column {
                            HomeSectionHeader(title = stringResource(R.string.home_jump_back_in))
                            if (shelves.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    shelves.forEach { (kind, _) ->
                                        FilterChip(
                                            selected = kind == activeShelf.first,
                                            onClick = { shelf = kind },
                                            label = { Text(stringResource(kind.titleRes)) },
                                            shape = CircleShape,
                                            modifier = Modifier.testTag("home_shelf_${kind.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                            TrackShelf(
                                tracks = activeShelf.second,
                                tagPrefix = "home_shelf_${activeShelf.first.name.lowercase()}",
                                onPlay = { list, index -> viewModel.playTracks(list, index) }
                            )
                        }
                    }
                    }

                    becauseYouPlayed?.let { because ->
                        item(key = "because_played") {
                        Column {
                            HomeSectionHeader(title = stringResource(R.string.home_because_played, because.seed.title))
                            TrackShelf(
                                tracks = because.tracks,
                                tagPrefix = "because_played",
                                onPlay = { list, index -> viewModel.playTracks(list, index) }
                            )
                        }
                    }
                    }

                    if (dailyMixes.isNotEmpty()) {
                        item(key = "daily_mixes") {
                        Column {
                            HomeSectionHeader(title = stringResource(R.string.home_daily_mixes))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("daily_mixes_row")
                            ) {
                                items(dailyMixes, key = { it.id }) { mix ->
                                    DailyMixCard(
                                        mix = mix,
                                        onClick = { selectedMixForDetail = mix },
                                        onPlayMix = { viewModel.playTracks(mix.tracks, 0) }
                                    )
                                }
                            }
                        }
                    }
                    }

                    item(key = "moods") {
                        Column {
                        HomeSectionHeader(title = stringResource(R.string.home_mixes_title))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TrackCuration.Mood.entries.forEach { mood ->
                                MoodChip(
                                    mood = mood,
                                    onClick = {
                                        viewModel.playMoodPlaylist(mood) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(emptyMoodMessage)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    }

                    if (totalTimeMs > 0L) {
                        item(key = "stats") {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            HomeStatsBanner(
                                totalTimeMs = totalTimeMs,
                                onClick = onNavigateToStats
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    selectedTrackForMenu?.let { track ->
        SongMenuBottomSheet(
            track = track,
            onDismissRequest = { selectedTrackForMenu = null },
            onPlayNext = {
                viewModel.playNext(track)
                selectedTrackForMenu = null
            },
            onAddToQueue = {
                viewModel.addToQueue(track)
                selectedTrackForMenu = null
            },
            onAddToPlaylist = {
                selectedTrackForMenu = null
                trackForAddToPlaylist = track
            },
            onDeleteTrack = {
                mediaStoreConsent.delete(track, onApproved = { approvedTrack ->
                    viewModel.deleteTrack(approvedTrack) { deleted ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(if (deleted) R.string.song_deleted else R.string.track_delete_failed)
                            )
                        }
                    }
                }, onCancelled = {
                    coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.track_delete_failed)) }
                })
                selectedTrackForMenu = null
            },
            onEditTrack = {
                selectedTrackForMenu = null
                selectedTrackForEdit = track
            },
            onHideTrack = {
                viewModel.hideTrack(track)
                selectedTrackForMenu = null
                coroutineScope.launch {
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
                                coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.track_updated_toast)) }
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

    trackForAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { trackForAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, track.id)
                trackForAddToPlaylist = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    selectedMixForDetail?.let { mix ->
        DailyMixDetailBottomSheet(
            mix = mix,
            onDismiss = { selectedMixForDetail = null },
            onPlayAll = { viewModel.playTracks(mix.tracks, 0) },
            onShufflePlay = { viewModel.playTracks(mix.tracks.shuffled(), 0) },
            onTrackClick = { index -> viewModel.playTracks(mix.tracks, index) }
        )
    }
}

private enum class JumpShelf(@androidx.annotation.StringRes val titleRes: Int) {
    RECENT(R.string.home_recently_played),
    NEW(R.string.home_recently_added),
    REPEAT(R.string.home_on_repeat)
}

@Composable
private fun MoodChip(
    mood: TrackCuration.Mood,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
        modifier = Modifier.testTag("mood_${mood.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (mood) {
                    TrackCuration.Mood.LATE_NIGHT_CHILL -> Icons.Filled.Bedtime
                    TrackCuration.Mood.DEEP_FOCUS -> Icons.Filled.CenterFocusStrong
                    TrackCuration.Mood.ENERGY_BOOST -> Icons.Filled.Whatshot
                    TrackCuration.Mood.MELANCHOLY_RAIN -> Icons.Filled.Cloud
                    TrackCuration.Mood.ACOUSTIC_MORNING -> Icons.Filled.WbSunny
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    when (mood) {
                        TrackCuration.Mood.LATE_NIGHT_CHILL -> R.string.mood_late_night_chill
                        TrackCuration.Mood.DEEP_FOCUS -> R.string.mood_deep_focus
                        TrackCuration.Mood.ENERGY_BOOST -> R.string.mood_energy_boost
                        TrackCuration.Mood.MELANCHOLY_RAIN -> R.string.mood_melancholy_rain
                        TrackCuration.Mood.ACOUSTIC_MORNING -> R.string.mood_acoustic_morning
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

