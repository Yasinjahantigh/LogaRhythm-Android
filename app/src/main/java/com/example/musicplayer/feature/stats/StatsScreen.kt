package com.example.musicplayer.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val totalTimeMs by viewModel.totalListenTimeMs.collectAsStateWithLifecycle()
    val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    val topAlbums by viewModel.topAlbums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("stats_screen"),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TimeRangeSelector(
                selectedRange = timeRange,
                onRangeSelected = viewModel::setTimeRange
            )

            TotalTimeCard(totalTimeMs = totalTimeMs)

            val tabs = listOf(
                StatsTab.TRACKS to stringResource(R.string.stats_top_tracks),
                StatsTab.ARTISTS to stringResource(R.string.stats_top_artists),
                StatsTab.ALBUMS to stringResource(R.string.stats_top_albums)
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.testTag("stats_tabs")
            ) {
                tabs.forEach { (tab, label) ->
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.setSelectedTab(tab) },
                        text = {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("tab_${tab.name}")
                    )
                }
            }

            when (selectedTab) {
                StatsTab.TRACKS -> {
                    StatsTracksTab(
                        tracks = topTracks,
                        onTrackClick = { index ->
                            val tracksToPlay = topTracks.map { it.track }
                            viewModel.playTrackFromTop(tracksToPlay, index)
                        },
                        onTrackMenuClick = { track -> selectedTrackForMenu = track },
                        modifier = Modifier.weight(1f)
                    )
                }
                StatsTab.ARTISTS -> {
                    StatsArtistsTab(
                        artists = topArtists,
                        onArtistClick = onNavigateToArtist,
                        modifier = Modifier.weight(1f)
                    )
                }
                StatsTab.ALBUMS -> {
                    StatsAlbumsTab(
                        albums = topAlbums,
                        onAlbumClick = onNavigateToAlbum,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

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
            onHideTrack = {
                viewModel.hideTrack(track)
                selectedTrackForMenu = null
                android.widget.Toast.makeText(context, context.getString(R.string.track_hidden_toast, track.title), android.widget.Toast.LENGTH_SHORT).show()
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
