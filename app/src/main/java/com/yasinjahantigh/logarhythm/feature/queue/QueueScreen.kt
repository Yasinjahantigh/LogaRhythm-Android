package com.yasinjahantigh.logarhythm.feature.queue

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.feature.library.LibraryEmptyHint
import com.yasinjahantigh.logarhythm.feature.player.PlayerViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.CreatePlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.ui.components.formatCount
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Live state of one free-drag reorder gesture. The queue is not mutated until drop, so item
 * keys stay stable for the whole drag.
 */
private data class QueueDrag(
    val fromIndex: Int,
    val targetIndex: Int,
    val totalDy: Float,
    /** Finger Y in LazyColumn viewport coords; null when coordinates aren't available yet. */
    val fingerY: Float?
)

/**
 * The playing queue, split the way people think about it: what already played (dimmed), what is
 * playing now, and what comes next. The list opens scrolled to the current song, so "what's
 * next" is on screen immediately instead of at the bottom of a long list.
 */
@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    BackHandler(onBack = onNavigateBack)

    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    val removedMsg = stringResource(R.string.queue_item_removed)
    val undoMsg = stringResource(R.string.queue_undo)
    var showSavePlaylistDialog by remember { mutableStateOf(false) }

    if (showSavePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showSavePlaylistDialog = false },
            onConfirm = { name ->
                showSavePlaylistDialog = false
                val trackIds = queue.map { it.id }
                playlistViewModel.createPlaylistWithTracks(name, trackIds) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.queue_saved_as_playlist, name)
                        )
                    }
                }
            }
        )
    }

    val currentIndex = queue.indexOfFirst { it.id == currentTrack?.id }

    // Open on the current song (one item of "played" context above it), once per queue screen.
    LaunchedEffect(currentIndex >= 0) {
        if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    // ---- free-drag reorder ------------------------------------------------------------
    var listCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var drag by remember { mutableStateOf<QueueDrag?>(null) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val estRowPx = remember(density) { with(density) { 64.dp.toPx() } }

    /** Precise target: which visible row is under the finger (handles variable row heights). */
    fun indexAtViewportY(y: Float): Int {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return -1
        return visible.firstOrNull { y >= it.offset && y <= it.offset + it.size }?.index
            ?: if (y < visible.first().offset) visible.first().index else visible.last().index
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
                        onClick = { showSavePlaylistDialog = true },
                        modifier = Modifier.testTag("queue_save_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.queue_save_as_playlist)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (shuffle) viewModel.reshuffleQueue() else viewModel.toggleShuffle()
                        },
                        modifier = Modifier.testTag("queue_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.cd_shuffle),
                            tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            .onGloballyPositioned { listCoords = it }
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
                                val activeDrag = drag
                                if (activeDrag != null && activeDrag.targetIndex == index &&
                                    activeDrag.targetIndex != activeDrag.fromIndex
                                ) {
                                    // Insertion marker: where the song will land on drop.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 2.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .testTag("queue_insertion_marker")
                                    )
                                }
                                QueueItem(
                                    track = track,
                                    isCurrent = index == currentIndex,
                                    isPlaying = isPlaying,
                                    isPlayed = currentIndex >= 0 && index < currentIndex,
                                    index = index,
                                    isDragging = activeDrag?.fromIndex == index,
                                    dragTranslationY = if (activeDrag?.fromIndex == index) {
                                        activeDrag.totalDy
                                    } else 0f,
                                    toViewportY = { windowY ->
                                        windowY - (listCoords?.positionInWindow()?.y ?: 0f)
                                    },
                                    onDragStart = { from, fingerY ->
                                        drag = QueueDrag(
                                            fromIndex = from,
                                            targetIndex = from,
                                            totalDy = 0f,
                                            fingerY = fingerY
                                        )
                                    },
                                    onDragDelta = { dy ->
                                        val d = drag ?: return@QueueItem
                                        val total = d.totalDy + dy
                                        val finger = d.fingerY?.plus(dy)
                                        val target = if (finger != null) {
                                            indexAtViewportY(finger)
                                                .takeIf { it >= 0 }
                                                ?.coerceIn(0, queue.size - 1)
                                        } else {
                                            // Coordinates not ready: estimate from row height.
                                            (d.fromIndex + (total / estRowPx).roundToInt())
                                                .coerceIn(0, queue.size - 1)
                                        } ?: d.targetIndex
                                        if (target != d.targetIndex) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        drag = d.copy(
                                            totalDy = total,
                                            fingerY = finger,
                                            targetIndex = target
                                        )
                                    },
                                    onDragEnd = { commit ->
                                        val d = drag
                                        drag = null
                                        if (commit && d != null && d.targetIndex != d.fromIndex) {
                                            viewModel.moveQueueItem(d.fromIndex, d.targetIndex)
                                        }
                                    },
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
