package com.example.musicplayer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.R
import com.example.musicplayer.domain.ai.MusicAiEngine
import com.example.musicplayer.domain.model.DailyMix
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.core.utils.rememberMediaStoreConsent
import com.example.musicplayer.feature.library.TrackListItem
import com.example.musicplayer.feature.player.EditTrackDialog
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet
import com.example.musicplayer.ui.theme.DisplayFontFamily
import com.example.musicplayer.domain.discovery.Discovery
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToStats: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
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
    val emptyMoodMessage = stringResource(R.string.ai_mood_empty)
    val sessionUnavailableMessage = stringResource(R.string.home_session_unavailable)

    val isLibraryEmpty = allTracks.isEmpty() && dailyMixes.isEmpty() && recentlyPlayed.isEmpty() && mostPlayed.isEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = rightNow?.let { greetingFor(it.daypart) }
                            ?: stringResource(id = R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = DisplayFontFamily
                    )
                }
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen")
    ) { innerPadding ->
        if (isLibraryEmpty) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.home_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToLibrary,
                    modifier = Modifier.testTag("home_browse_library_button")
                ) {
                    Text(text = stringResource(id = R.string.home_browse_library))
                }
            }
        } else {
            val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
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
                rightNow?.takeIf { it.tracks.isNotEmpty() }?.let { now ->
                    item {
                        RightNowCard(rightNow = now, onPlay = viewModel::playRightNow)
                    }
                }

                if (onRepeat.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = stringResource(R.string.home_on_repeat),
                            icon = Icons.Filled.Autorenew
                        )
                    }
                    item {
                        TrackShelf(
                            tracks = onRepeat,
                            tagPrefix = "on_repeat",
                            onPlay = { list, index -> viewModel.playTracks(list, index) }
                        )
                    }
                }

                becauseYouPlayed?.let { because ->
                    item {
                        HomeSectionHeader(
                            title = stringResource(R.string.home_because_played, because.seed.title),
                            icon = Icons.Filled.Radio
                        )
                    }
                    item {
                        TrackShelf(
                            tracks = because.tracks,
                            tagPrefix = "because_played",
                            onPlay = { list, index -> viewModel.playTracks(list, index) }
                        )
                    }
                }

                if (allTracks.isNotEmpty()) {
                    item {
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

                // AI Mood Radio / Smart Mixes
                item {
                    HomeSectionHeader(
                        title = stringResource(R.string.home_ai_mood_title),
                        icon = Icons.Filled.Psychology
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MusicAiEngine.AiMood.entries.forEach { mood ->
                            AiMoodChip(
                                mood = mood,
                                onClick = {
                                    viewModel.playAiMoodPlaylist(mood) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(emptyMoodMessage)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (dailyMixes.isNotEmpty()) {
                    item {
                        HomeSectionHeader(
                            title = stringResource(R.string.home_daily_mixes),
                            icon = Icons.Filled.AutoAwesome
                        )
                    }
                    item {
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

                if (recentlyPlayed.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HomeSectionHeader(
                            title = stringResource(R.string.home_recently_played),
                            icon = Icons.Filled.History
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.testTag("recently_played_row")
                        ) {
                            itemsIndexed(recentlyPlayed, key = { _, track -> track.id }) { index, track ->
                                RecentlyPlayedCard(
                                    track = track,
                                    onClick = { viewModel.playTracks(recentlyPlayed, index) }
                                )
                            }
                        }
                    }
                }

                if (recentlyAdded.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HomeSectionHeader(
                            title = stringResource(R.string.home_recently_added),
                            icon = Icons.Filled.MusicNote
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.testTag("recently_added_row")
                        ) {
                            itemsIndexed(recentlyAdded, key = { _, track -> track.id }) { index, track ->
                                RecentlyPlayedCard(
                                    track = track,
                                    onClick = { viewModel.playTracks(recentlyAdded, index) },
                                    tagPrefix = "recently_added"
                                )
                            }
                        }
                    }
                }

                if (mostPlayed.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HomeSectionHeader(
                            title = stringResource(R.string.home_most_played),
                            icon = Icons.Filled.Whatshot
                        )
                    }
                    itemsIndexed(mostPlayed, key = { _, track -> track.id }) { index, track ->
                        TrackListItem(
                            track = track,
                            onClick = { viewModel.playTracks(mostPlayed, index) },
                            onMenuClick = { selectedTrackForMenu = track },
                            modifier = Modifier.testTag("most_played_item_${track.id}")
                        )
                    }
                }
                if (totalTimeMs > 0L) {
                    item {
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

@Composable
private fun AiMoodChip(
    mood: MusicAiEngine.AiMood,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("ai_mood_${mood.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (mood) {
                    MusicAiEngine.AiMood.LATE_NIGHT_CHILL -> Icons.Filled.Bedtime
                    MusicAiEngine.AiMood.DEEP_FOCUS -> Icons.Filled.CenterFocusStrong
                    MusicAiEngine.AiMood.ENERGY_BOOST -> Icons.Filled.Whatshot
                    MusicAiEngine.AiMood.MELANCHOLY_RAIN -> Icons.Filled.Cloud
                    MusicAiEngine.AiMood.ACOUSTIC_MORNING -> Icons.Filled.WbSunny
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    when (mood) {
                        MusicAiEngine.AiMood.LATE_NIGHT_CHILL -> R.string.ai_mood_late_night_chill
                        MusicAiEngine.AiMood.DEEP_FOCUS -> R.string.ai_mood_deep_focus
                        MusicAiEngine.AiMood.ENERGY_BOOST -> R.string.ai_mood_energy_boost
                        MusicAiEngine.AiMood.MELANCHOLY_RAIN -> R.string.ai_mood_melancholy_rain
                        MusicAiEngine.AiMood.ACOUSTIC_MORNING -> R.string.ai_mood_acoustic_morning
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

