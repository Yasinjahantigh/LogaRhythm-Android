package com.example.musicplayer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.example.R
import com.example.musicplayer.domain.ai.MusicAiEngine
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.library.TrackListItem
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet

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
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val totalTimeMs by viewModel.totalListenTimeMs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val isLibraryEmpty = dailyMixes.isEmpty() && recentlyPlayed.isEmpty() && mostPlayed.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.home_title),
                        fontWeight = FontWeight.Bold
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("home_content_list"),
                contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)
            ) {
                if (totalTimeMs > 0L) {
                    item {
                        HomeStatsBanner(
                            totalTimeMs = totalTimeMs,
                            onClick = onNavigateToStats
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
                                onClick = { viewModel.playAiMoodPlaylist(mood) }
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
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = mood.titleEn,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
