package com.yasinjahantigh.logarhythm.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val totalTimeMs by viewModel.totalListenTimeMs.collectAsStateWithLifecycle()
    val completedPlayCount by viewModel.completedPlayCount.collectAsStateWithLifecycle()
    val peakHour by viewModel.peakHour.collectAsStateWithLifecycle()
    val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    val topAlbums by viewModel.topAlbums.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }

    val customLabel = customDateRange?.let { range ->
        val fmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
        "${fmt.format(java.util.Date(range.startMs))} - ${fmt.format(java.util.Date(range.endMs))}"
    }
    val periodLabel = when (timeRange) {
        com.yasinjahantigh.logarhythm.domain.model.TimeRange.THIS_WEEK -> stringResource(R.string.stats_range_week)
        com.yasinjahantigh.logarhythm.domain.model.TimeRange.THIS_MONTH -> stringResource(R.string.stats_range_month)
        com.yasinjahantigh.logarhythm.domain.model.TimeRange.THIS_YEAR -> stringResource(R.string.stats_range_year)
        com.yasinjahantigh.logarhythm.domain.model.TimeRange.ALL_TIME -> stringResource(R.string.stats_range_all_time)
        com.yasinjahantigh.logarhythm.domain.model.TimeRange.CUSTOM -> customLabel ?: stringResource(R.string.stats_range_custom)
    }

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
                },
                actions = {
                    IconButton(onClick = { showReportSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.stats_report_action)
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
                customLabel = customLabel,
                onRangeSelected = { picked ->
                    if (picked == com.yasinjahantigh.logarhythm.domain.model.TimeRange.CUSTOM) {
                        showDatePicker = true
                    } else {
                        viewModel.setTimeRange(picked)
                    }
                }
            )

            TotalTimeCard(
                totalTimeMs = totalTimeMs,
                onOpenReport = { showReportSheet = true }
            )

            com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                hintId = com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.STATS_SORT_SHARE,
                text = stringResource(R.string.tip_stats_sort_share),
                icon = Icons.Filled.BarChart,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val tabs = listOf(
                StatsTab.HISTORY to stringResource(R.string.stats_history),
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

            if (selectedTab != StatsTab.HISTORY) {
                StatsSortSelector(
                    selectedSort = sortBy,
                    onSortSelected = viewModel::setSortBy
                )
            }

            when (selectedTab) {
                StatsTab.HISTORY -> {
                    if (recentlyPlayed.isEmpty()) {
                        StatsEmptyTabContent(
                            title = stringResource(R.string.stats_history_empty_title),
                            message = stringResource(R.string.stats_history_empty_desc),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val bottomPadding = LocalPlaybackBottomPadding.current
                        LazyColumn(
                            modifier = Modifier.weight(1f).testTag("stats_history_list"),
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp)
                        ) {
                            itemsIndexed(recentlyPlayed, key = { index, track -> "hist_${track.id}_$index" }) { index, track ->
                                val isCurrent = currentTrack?.id == track.id
                                TrackListItem(
                                    track = track,
                                    onClick = { viewModel.playTrackFromTop(recentlyPlayed, index) },
                                    isCurrentTrack = isCurrent,
                                    isPlaying = isCurrent && isPlaying,
                                    onMenuClick = { selectedTrackForMenu = track },
                                    onPlayNext = { viewModel.playNext(track) }
                                )
                            }
                        }
                    }
                }
                StatsTab.TRACKS -> {
                    StatsTracksTab(
                        tracks = topTracks,
                        sortBy = sortBy,
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
                        sortBy = sortBy,
                        onArtistClick = onNavigateToArtist,
                        modifier = Modifier.weight(1f)
                    )
                }
                StatsTab.ALBUMS -> {
                    StatsAlbumsTab(
                        albums = topAlbums,
                        sortBy = sortBy,
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

    if (showDatePicker) {
        val dateRangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customDateRange?.startMs,
            initialSelectedEndDateMillis = customDateRange?.endMs
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = dateRangeState.selectedStartDateMillis
                        val e = dateRangeState.selectedEndDateMillis ?: s
                        if (s != null && e != null) {
                            viewModel.setCustomDateRange(s, e)
                        }
                        showDatePicker = false
                    },
                    enabled = dateRangeState.selectedStartDateMillis != null
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DateRangePicker(state = dateRangeState)
        }
    }

    if (showReportSheet) {
        ListeningReportSheet(
            periodLabel = periodLabel,
            totalTimeMs = totalTimeMs,
            playCount = completedPlayCount,
            peakHour = peakHour,
            topArtists = topArtists,
            topTracks = topTracks,
            sortBy = sortBy,
            onDismiss = { showReportSheet = false }
        )
    }
}
