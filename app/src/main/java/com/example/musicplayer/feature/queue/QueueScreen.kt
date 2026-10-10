package com.example.musicplayer.feature.queue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.feature.player.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMsg = stringResource(R.string.queue_item_removed)
    val undoMsg = stringResource(R.string.queue_undo)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("queue_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
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
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("queue_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
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
            )
        }
    ) { innerPadding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
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
                        .testTag("queue_list")
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
                    QueueItem(
                        track = track,
                        isCurrent = track.id == currentTrack?.id,
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
