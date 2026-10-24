package com.yasinjahantigh.logarhythm.feature.smart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.CreatePlaylistDialog
import com.yasinjahantigh.logarhythm.ui.components.DetailHero
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
import kotlinx.coroutines.launch

@Composable
fun SmartMixDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartMixDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onNavigateBack)

    val mix by viewModel.mix.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }

    val title = stringResource(viewModel.type.titleRes())
    val description = stringResource(viewModel.type.descriptionRes())

    if (showSaveDialog) {
        CreatePlaylistDialog(
            initialName = title,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                viewModel.saveAsPlaylist(name) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.smart_mix_saved, name))
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_mix_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val bottomPadding = LocalPlaybackBottomPadding.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                item {
                    DetailHero(
                        title = title,
                        subtitle = description,
                        meta = stringResource(R.string.playlist_track_count, tracks.size) +
                            " • " + TimeUtils.formatDuration(tracks.sumOf { it.durationMs }),
                        artworkUri = mix?.artworkUris?.firstOrNull(),
                        icon = viewModel.type.icon(),
                        onBack = onNavigateBack,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::shuffleAll,
                        actionsEnabled = tracks.isNotEmpty(),
                        backTag = "smart_mix_back_button",
                        playTag = "smart_mix_play_button",
                        shuffleTag = "smart_mix_shuffle_button",
                        topEndAction = {
                            IconButton(
                                onClick = { showSaveDialog = true },
                                enabled = tracks.isNotEmpty(),
                                modifier = Modifier.testTag("smart_mix_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = stringResource(R.string.smart_mix_save_as_playlist)
                                )
                            }
                        }
                    )
                }

                if (tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.smart_mix_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackListItem(
                            track = track,
                            onClick = { viewModel.playTrackAt(index) }
                        )
                    }
                }
            }
        }
    }
}
