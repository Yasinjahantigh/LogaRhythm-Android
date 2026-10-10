package com.example.musicplayer.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.musicplayer.core.utils.ArtworkColorExtractor
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.equalizer.EqualizerScreen
import com.example.musicplayer.feature.lyrics.LyricsView
import com.example.musicplayer.feature.lyrics.LyricsViewModel
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.PlaylistViewModel
import com.example.musicplayer.feature.queue.QueueScreen
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val isQueueOpen by viewModel.isQueueOpen.collectAsStateWithLifecycle()

    BackHandler {
        if (isQueueOpen) viewModel.closeQueue() else onCollapse()
    }

    if (isQueueOpen) {
        QueueScreen(
            viewModel = viewModel,
            onNavigateBack = viewModel::closeQueue,
            modifier = modifier
        )
        return
    }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.position.collectAsStateWithLifecycle()
    val durationMs by viewModel.duration.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val isSleepTimerEndOfTrack by viewModel.isSleepTimerEndOfTrack.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    var showLyrics by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showEditTrackDialog by remember { mutableStateOf(false) }
    var isVinylMode by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val activePositionMs = if (isDragging) dragPositionMs.toLong() else positionMs
    val track = currentTrack
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val artworkDragOffsetX = remember { Animatable(0f) }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSpeedSelected = viewModel::setPlaybackSpeed,
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = sleepTimerRemainingMs,
            isEndOfTrack = isSleepTimerEndOfTrack,
            onSetTimerMinutes = viewModel::setSleepTimer,
            onSetEndOfTrack = viewModel::setSleepTimerEndOfTrack,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showEqualizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false }
        ) {
            EqualizerScreen(
                onNavigateBack = { showEqualizerSheet = false }
            )
        }
    }

    if (showAddToPlaylistDialog && track != null) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { playlist ->
                playlistViewModel.addTrackToPlaylist(playlist.id, track.id)
                showAddToPlaylistDialog = false
                Toast.makeText(context, context.getString(R.string.added_to_playlist, playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylist = { name ->
                playlistViewModel.createPlaylist(name)
            }
        )
    }

    if (showDetailsDialog && track != null) {
        FullPlayerSongDetailsDialog(
            track = track,
            onDismiss = { showDetailsDialog = false }
        )
    }

    if (showEditTrackDialog && track != null) {
        EditTrackDialog(
            track = track,
            onDismiss = { showEditTrackDialog = false },
            onSave = { newTitle, newArtist, newAlbum, newGenre ->
                viewModel.updateTrackMetadata(track.id, newTitle, newArtist, newAlbum, newGenre) {
                    Toast.makeText(context, context.getString(R.string.track_updated_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    var dynamicArtworkColor by remember { mutableStateOf(surfaceColor) }

    LaunchedEffect(track?.artworkUri) {
        if (track?.artworkUri != null) {
            val extracted = ArtworkColorExtractor.extractDominantColor(context, track.artworkUri, surfaceColor)
            dynamicArtworkColor = extracted
        } else {
            dynamicArtworkColor = surfaceColor
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = dynamicArtworkColor,
        animationSpec = tween(durationMillis = 600),
        label = "player_bg_color"
    )

    val infinitePlayerTransition = rememberInfiniteTransition(label = "cute_player_glow")
    val breathingGlowScale by infinitePlayerTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_aura_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_screen"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBgColor.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                FullPlayerHeader(
                    showLyrics = showLyrics,
                    playbackSpeed = playbackSpeed,
                    sleepTimerRemainingMs = sleepTimerRemainingMs,
                    isSleepTimerEndOfTrack = isSleepTimerEndOfTrack,
                    onCollapse = onCollapse,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onOpenSpeedDialog = { showSpeedDialog = true },
                    onOpenSleepTimerDialog = { showSleepTimerDialog = true },
                    onOpenQueue = viewModel::openQueue,
                    onStartRadio = {
                        viewModel.startTrackRadio { count ->
                            val message = if (count > 0) {
                                context.getString(R.string.player_radio_started, count)
                            } else {
                                context.getString(R.string.player_radio_empty)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenEqualizer = { showEqualizerSheet = true },
                    onAddToPlaylist = { showAddToPlaylistDialog = true },
                    onSearchGoogleLyrics = {
                        if (track != null) {
                            val query = "${track.title} ${track.artist} lyrics"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"))
                            context.startActivity(intent)
                        }
                    },
                    onOpenSongDetails = { showDetailsDialog = true },
                    onShareSong = {
                        if (track != null) {
                            shareTrack(context, track)
                        }
                    },
                    onEditSongDetails = { showEditTrackDialog = true }
                )

                // Central Area: Flippable between Artwork and Lyrics with Swipe-to-skip
                if (showLyrics) {
                    LyricsView(
                        viewModel = lyricsViewModel,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Cute Style Switcher Toggle Button in top-end corner
                        Surface(
                            onClick = { isVinylMode = !isVinylMode },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 6.dp, top = 2.dp)
                                .testTag("player_style_toggle")
                        ) {
                            Text(
                                text = if (isVinylMode) "💿 Vinyl" else "🎴 Card",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        if (isVinylMode) {
                            CuteVinylTurntable(
                                track = track,
                                isPlaying = isPlaying,
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .widthIn(max = 330.dp)
                                    .aspectRatio(1f)
                                    .offset { IntOffset(artworkDragOffsetX.value.roundToInt(), 0) }
                            )
                        } else {
                            // Pulsating breathing aura glow behind Album Card
                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.90f)
                                        .widthIn(max = 340.dp)
                                        .aspectRatio(1f)
                                        .scale(breathingGlowScale)
                                        .clip(RoundedCornerShape(38.dp))
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    animatedBgColor.copy(alpha = 0.65f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            Card(
                                shape = RoundedCornerShape(32.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .widthIn(max = 330.dp)
                                    .aspectRatio(1f)
                                    .offset { IntOffset(artworkDragOffsetX.value.roundToInt(), 0) }
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    val current = artworkDragOffsetX.value
                                                    if (current < -120f) {
                                                        viewModel.next()
                                                    } else if (current > 120f) {
                                                        viewModel.previous()
                                                    }
                                                    artworkDragOffsetX.animateTo(0f, spring())
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch { artworkDragOffsetX.animateTo(0f, spring()) }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    artworkDragOffsetX.snapTo((artworkDragOffsetX.value + dragAmount).coerceIn(-200f, 200f))
                                                }
                                            }
                                        )
                                    }
                            ) {
                                AsyncImage(
                                    model = track?.artworkUri,
                                    contentDescription = stringResource(R.string.cd_artwork, track?.title.orEmpty()),
                                    placeholder = painterResource(id = R.drawable.ic_music_placeholder),
                                    error = painterResource(id = R.drawable.ic_music_placeholder),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Track metadata & Favorite Button & Format Tag
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track?.title?.ifBlank { stringResource(R.string.unknown_title) } ?: stringResource(R.string.unknown_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track?.artist?.ifBlank { stringResource(R.string.unknown_artist) } ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = viewModel::toggleFavorite,
                        modifier = Modifier.size(48.dp).testTag("player_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Subtitle audio info badge + cute vibe indicator + lively jumping visualizer bars
                val genreText = track?.genre?.takeIf { it.isNotBlank() && it != "Unknown Genre" }
                val albumText = track?.album?.takeIf { it.isNotBlank() && it != "Unknown Album" }
                val tagText = genreText ?: albumText ?: stringResource(R.string.offline_audio_badge)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = tagText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = if (isPlaying) "✨ Groovy Vibe 🎵" else "💤 Paused",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Cute lively equalizer bars!
                    CuteEqualizerVisualizer(
                        isPlaying = isPlaying,
                        barColor = MaterialTheme.colorScheme.primary,
                        maxHeight = 18.dp
                    )
                }
            }

            // Seek Bar Slider & Time Labels
            Column(modifier = Modifier.fillMaxWidth()) {
                val maxRange = durationMs.coerceAtLeast(1L).toFloat()
                Slider(
                    value = activePositionMs.toFloat().coerceIn(0f, maxRange),
                    onValueChange = {
                        isDragging = true
                        dragPositionMs = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        viewModel.seekTo(dragPositionMs.toLong())
                    },
                    valueRange = 0f..maxRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seek_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = TimeUtils.formatDuration(activePositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (showRemainingTime) {
                            val remaining = (durationMs - activePositionMs).coerceAtLeast(0L)
                            "-${TimeUtils.formatDuration(remaining)}"
                        } else {
                            TimeUtils.formatDuration(durationMs)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )
                }
            }

            // Controls row
            FullPlayerControls(
                isPlaying = isPlaying,
                shuffle = shuffle,
                repeatMode = repeatMode,
                onTogglePlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeat,
                onRewind10 = {
                    val target = (activePositionMs - 10_000L).coerceAtLeast(0L)
                    viewModel.seekTo(target)
                },
                onFastForward10 = {
                    val target = (activePositionMs + 10_000L).coerceAtMost(durationMs)
                    viewModel.seekTo(target)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
}

@Composable
private fun FullPlayerSongDetailsDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val filePath = remember(track) { resolveTrackPath(context, track) }
    val fileSizeMb = remember(filePath) {
        if (filePath != null) {
            val f = File(filePath)
            if (f.exists()) String.format("%.2f MB", f.length().toDouble() / (1024 * 1024)) else null
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_song_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailItem(label = stringResource(R.string.detail_title), value = track.title)
                DetailItem(label = stringResource(R.string.detail_artist), value = track.artist)
                DetailItem(label = stringResource(R.string.detail_album), value = track.album)
                DetailItem(label = stringResource(R.string.detail_duration), value = TimeUtils.formatDuration(track.durationMs))
                DetailItem(label = stringResource(R.string.detail_source), value = track.sourceType.name)
                if (fileSizeMb != null) {
                    DetailItem(label = stringResource(R.string.detail_file_size), value = fileSizeMb)
                }
                if (filePath != null) {
                    DetailItem(label = stringResource(R.string.detail_file_path), value = filePath)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareTrack(context: Context, track: Track) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            if (track.uri.isNotBlank()) {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri))
            }
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
            putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share_song)))
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
    }
}

private fun resolveTrackPath(context: Context, track: Track): String? {
    if (!track.id.startsWith("local:")) return track.uri
    val mediaStoreId = track.id.removePrefix("local:").toLongOrNull() ?: return null
    val projection = arrayOf(MediaStore.Audio.Media.DATA)
    val selection = "${MediaStore.Audio.Media._ID} = ?"
    val selectionArgs = arrayOf(mediaStoreId.toString())

    try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol != -1 && cursor.moveToFirst()) {
                return cursor.getString(dataCol)
            }
        }
    } catch (_: Exception) {
    }
    return null
}
