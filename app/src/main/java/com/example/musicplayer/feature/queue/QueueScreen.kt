package com.example.musicplayer.feature.queue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.feature.library.LibraryEmptyHint
import com.example.musicplayer.feature.player.PlayerViewModel
import com.example.musicplayer.ui.components.formatCount
import kotlinx.coroutines.launch

/**
 * The playing queue, split the way people think about it: what already played (dimmed), what is
 * playing now, and what comes next. The list opens scrolled to the current song, so "what's
 * next" is on screen immediately instead of at the bottom of a long list.
 */
@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val removedMsg = stringResource(R.string.queue_item_removed)
    val undoMsg = stringResource(R.string.queue_undo)

    val currentIndex = queue.indexOfFirst { it.id == currentTrack?.id }

    // Open on the current song (one item of "played" context above it), once per queue screen.
    LaunchedEffect(currentIndex >= 0) {
        if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("queue_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("queue_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = stringResource(R.string.queue_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.queue_track_count, queue.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (queue.isNotEmpty()) {
                    IconButton(
                        onClick = viewModel::clearQueue,
                        modifier = Modifier.testTag("queue_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = stringResource(R.string.search_clear_recent)
                        )
                    }
                }
            }

            if (queue.isEmpty()) {
                LibraryEmptyHint(
                    text = stringResource(R.string.queue_empty),
                    icon = Icons.AutoMirrored.Filled.QueueMusic
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                            .testTag("queue_list")
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { index, track -> "${track.id}_$index" }
                        ) { index, track ->
                            Column {
                                // Section labels ride on the first row of each section.
                                if (currentIndex > 0 && index == 0) {
                                    QueueSectionLabel(stringResource(R.string.queue_played))
                                }
                                if (index == currentIndex) {
                                    QueueSectionLabel(stringResource(R.string.now_playing))
                                }
                                if (currentIndex >= 0 && index == currentIndex + 1) {
                                    QueueSectionLabel(
                                        stringResource(R.string.queue_up_next) + " · " +
                                            formatCount(queue.size - currentIndex - 1)
                                    )
                                }
                                QueueItem(
                                    track = track,
                                    isCurrent = index == currentIndex,
                                    isPlaying = isPlaying,
                                    isPlayed = currentIndex >= 0 && index < currentIndex,
                                    index = index,
                                    onClick = { viewModel.playQueueItem(index) },
                                    onDismiss = {
                                        val removed = viewModel.removeFromQueue(index)
                                        if (removed != null) {
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = removedMsg,
                                                    actionLabel = undoMsg,
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.insertIntoQueue(index, removed)
                                                }
                                            }
                                        }
                                    },
                                    onDragMove = { from, to ->
                                        viewModel.moveQueueItem(from, to)
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
private fun QueueSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}
