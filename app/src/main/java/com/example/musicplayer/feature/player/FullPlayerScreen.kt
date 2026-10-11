package com.example.musicplayer.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import com.example.R
import com.example.musicplayer.core.utils.ArtworkColorExtractor
import com.example.musicplayer.core.utils.FileUtils
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.core.utils.rememberMediaStoreConsent
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
    // Deliberately NOT read here: position ticks twice a second and would recompose this whole screen.
    val positionState = viewModel.position.collectAsStateWithLifecycle()
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
    var editTrackError by remember { mutableStateOf(false) }
    var editTrackSaving by remember { mutableStateOf(false) }
    var isVinylMode by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(false) }

    val track = currentTrack
    val context = LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()

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
            onDismiss = {
                showEditTrackDialog = false
                editTrackError = false
                editTrackSaving = false
            },
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
                                showEditTrackDialog = false
                                Toast.makeText(context, context.getString(R.string.track_updated_toast), Toast.LENGTH_SHORT).show()
                            } else {
                                editTrackError = true
                            }
                        }
                    }, onCancelled = {
                        editTrackSaving = false
                        editTrackError = true
                    })
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

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_screen"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBgColor.copy(alpha = 0.32f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            val isLandscapeLayout = maxWidth > maxHeight
            val horizontalPadding = if (isLandscapeLayout) 28.dp else 24.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = horizontalPadding, vertical = if (isLandscapeLayout) 6.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header bar
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

                // Main adaptive body
                if (isLandscapeLayout) {
                    // LANDSCAPE TWO-PANE LAYOUT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left Pane: Artwork or Lyrics
                        Box(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showLyrics) {
                                LyricsView(
                                    viewModel = lyricsViewModel,
                                    onNext = viewModel::next,
                                    onPrevious = viewModel::previous,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val artworkSize = minOf(maxWidth * 0.88f, maxHeight * 0.88f, 280.dp)
                                    Box(
                                        modifier = Modifier.size(artworkSize),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            onClick = { isVinylMode = !isVinylMode },
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .testTag("player_style_toggle")
                                        ) {
                                            Text(
                                                text = if (isVinylMode) stringResource(R.string.player_style_vinyl) else stringResource(R.string.player_style_artwork),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        if (isVinylMode) {
                                            CuteVinylTurntable(track, isPlaying, Modifier.fillMaxSize())
                                        } else {
                                            Card(
                                                shape = RoundedCornerShape(24.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .offset { IntOffset(artworkDragOffsetX.value.roundToInt(), 0) }
                                                    .pointerInput(Unit) {
                                                        detectHorizontalDragGestures(
                                                            onDragEnd = {
                                                                coroutineScope.launch {
                                                                    if (artworkDragOffsetX.value < -120f) viewModel.next()
                                                                    else if (artworkDragOffsetX.value > 120f) viewModel.previous()
                                                                    artworkDragOffsetX.animateTo(0f, spring())
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                coroutineScope.launch { artworkDragOffsetX.animateTo(0f, spring()) }
                                                            },
                                                            onHorizontalDrag = { change, amount ->
                                                                change.consume()
                                                                coroutineScope.launch {
                                                                    artworkDragOffsetX.snapTo((artworkDragOffsetX.value + amount).coerceIn(-200f, 200f))
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
                            }
                        }

                        // Right Pane: Metadata, Timeline, Controls
                        Column(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            TrackIdentityAndActions(
                                track = track,
                                isFavorite = isFavorite,
                                onToggleFavorite = viewModel::toggleFavorite
                            )

                            // Subtitle audio info badge + active visualizer in landscape
                            val landscapeGenreText = track?.genre?.takeIf { it.isNotBlank() && it != "Unknown Genre" && !it.equals("null", ignoreCase = true) }
                            val landscapeAlbumText = track?.album?.takeIf { it.isNotBlank() && it != "Unknown Album" && !it.equals("null", ignoreCase = true) }
                            val landscapeTagText = landscapeGenreText ?: landscapeAlbumText ?: stringResource(R.string.offline_audio_badge)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
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
                                            text = landscapeTagText,
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
                                            text = if (isPlaying) stringResource(R.string.player_groovy_vibe) else stringResource(R.string.player_paused),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                CuteEqualizerVisualizer(
                                    isPlaying = isPlaying,
                                    barColor = MaterialTheme.colorScheme.primary,
                                    maxHeight = 16.dp
                                )
                            }

                            PlaybackTimeline(
                                positionState = positionState,
                                durationMs = durationMs,
                                showRemainingTime = showRemainingTime,
                                onRemainingTimeClick = { showRemainingTime = !showRemainingTime },
                                onSeek = viewModel::seekTo
                            )

                            FullPlayerControls(
                                isPlaying = isPlaying,
                                shuffle = shuffle,
                                repeatMode = repeatMode,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onPrevious = viewModel::previous,
                                onNext = viewModel::next,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onCycleRepeat = viewModel::cycleRepeat,
                                onRewind10 = { viewModel.seekTo((positionState.value - 10_000L).coerceAtLeast(0L)) },
                                onFastForward10 = { viewModel.seekTo((positionState.value + 10_000L).coerceAtMost(durationMs)) },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    // PORTRAIT LAYOUT
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
                            Surface(
                                onClick = { isVinylMode = !isVinylMode },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 8.dp, top = 4.dp)
                                    .testTag("player_style_toggle")
                            ) {
                                Text(
                                    text = if (isVinylMode) stringResource(R.string.player_style_vinyl) else stringResource(R.string.player_style_artwork),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                                Card(
                                    shape = RoundedCornerShape(28.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth(0.88f)
                                        .widthIn(max = 330.dp)
                                        .aspectRatio(1f)
                                        .offset { IntOffset(artworkDragOffsetX.value.roundToInt(), 0) }
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    coroutineScope.launch {
                                                        if (artworkDragOffsetX.value < -120f) viewModel.next()
                                                        else if (artworkDragOffsetX.value > 120f) viewModel.previous()
                                                        artworkDragOffsetX.animateTo(0f, spring())
                                                    }
                                                },
                                                onDragCancel = {
                                                    coroutineScope.launch { artworkDragOffsetX.animateTo(0f, spring()) }
                                                },
                                                onHorizontalDrag = { change, amount ->
                                                    change.consume()
                                                    coroutineScope.launch {
                                                        artworkDragOffsetX.snapTo((artworkDragOffsetX.value + amount).coerceIn(-200f, 200f))
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

                    // Portrait track details & actions
                    TrackIdentityAndActions(
                        track = track,
                        isFavorite = isFavorite,
                        onToggleFavorite = viewModel::toggleFavorite
                    )

                    // Subtitle audio info badge + active playing indicator
                    val genreText = track?.genre?.takeIf { it.isNotBlank() && it != "Unknown Genre" }
                    val albumText = track?.album?.takeIf { it.isNotBlank() && it != "Unknown Album" }
                    val tagText = genreText ?: albumText ?: stringResource(R.string.offline_audio_badge)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                                    text = if (isPlaying) stringResource(R.string.player_groovy_vibe) else stringResource(R.string.player_paused),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        CuteEqualizerVisualizer(
                            isPlaying = isPlaying,
                            barColor = MaterialTheme.colorScheme.primary,
                            maxHeight = 18.dp
                        )
                    }

                    PlaybackTimeline(
                        positionState = positionState,
                        durationMs = durationMs,
                        showRemainingTime = showRemainingTime,
                        onRemainingTimeClick = { showRemainingTime = !showRemainingTime },
                        onSeek = viewModel::seekTo
                    )

                    FullPlayerControls(
                        isPlaying = isPlaying,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeat,
                        onRewind10 = { viewModel.seekTo((positionState.value - 10_000L).coerceAtLeast(0L)) },
                        onFastForward10 = { viewModel.seekTo((positionState.value + 10_000L).coerceAtMost(durationMs)) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackIdentityAndActions(
    track: Track?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = when {
        track == null || track.title.isBlank() ||
            track.title.equals("<unknown>", ignoreCase = true) ||
            track.title.equals("unknown", ignoreCase = true) ||
            track.title.equals("null", ignoreCase = true) -> stringResource(R.string.unknown_title)
        else -> track.title
    }

    val displayArtist = when {
        track == null || track.artist.isBlank() ||
            track.artist.equals("<unknown>", ignoreCase = true) ||
            track.artist.equals("unknown", ignoreCase = true) ||
            track.artist.equals("null", ignoreCase = true) -> stringResource(R.string.unknown_artist)
        else -> track.artist
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayArtist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(48.dp)
                .testTag("player_favorite_button")
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * LogaRhythm's scrubber: a thin ember thread with a glowing playhead that swells while you hold it.
 *
 * It owns its own drag state and reads [positionState] lazily (draw phase for the thread, a tiny
 * local recomposition for the clock), so playback ticks never recompose the surrounding player.
 * Always laid out left-to-right, like a tape: elapsed time on the left, in every UI language.
 */
@Composable
fun PlaybackTimeline(
    positionState: State<Long>,
    durationMs: Long,
    showRemainingTime: Boolean,
    onRemainingTimeClick: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val duration = durationMs.coerceAtLeast(1L)
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        val dragging = dragFraction != null
        val swell by animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(),
            label = "timeline_swell"
        )
        val active = MaterialTheme.colorScheme.primary
        val thread = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        val currentOnSeek by rememberUpdatedState(onSeek)
        val haptic = LocalHapticFeedback.current

        val shownMs = dragFraction?.let { (it * duration).toLong() } ?: positionState.value
        val progressForSemantics = (shownMs.toFloat() / duration).coerceIn(0f, 1f)
        val description = stringResource(R.string.cd_seek_bar)

        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("player_seek_bar")
                    .semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(progressForSemantics, 0f..1f)
                        setProgress { target ->
                            currentOnSeek((target.coerceIn(0f, 1f) * duration).toLong())
                            true
                        }
                    }
                    .pointerInput(duration) {
                        detectTapGestures { offset ->
                            val f = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            currentOnSeek((f * duration).toLong())
                        }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragFraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragFraction?.let { currentOnSeek((it * duration).toLong()) }
                                dragFraction = null
                            },
                            onDragCancel = { dragFraction = null },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fraction = dragFraction ?: (positionState.value.toFloat() / duration).coerceIn(0f, 1f)
                    val thickness = 4.dp.toPx() + 2.dp.toPx() * swell
                    val radius = thickness / 2f
                    val cy = size.height / 2f
                    val x = (size.width * fraction).coerceIn(radius, size.width - radius)
                    drawRoundRect(
                        color = thread,
                        topLeft = Offset(0f, cy - radius),
                        size = Size(size.width, thickness),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    drawRoundRect(
                        color = active,
                        topLeft = Offset(0f, cy - radius),
                        size = Size(x + radius, thickness),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    drawCircle(
                        color = active.copy(alpha = 0.18f + 0.10f * swell),
                        radius = 10.dp.toPx() + 8.dp.toPx() * swell,
                        center = Offset(x, cy)
                    )
                    drawCircle(
                        color = active,
                        radius = 5.dp.toPx() + 2.dp.toPx() * swell,
                        center = Offset(x, cy)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TimeUtils.formatDuration(shownMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dragging) active else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (dragging) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    text = if (showRemainingTime) {
                        "-${TimeUtils.formatDuration((durationMs - shownMs).coerceAtLeast(0L))}"
                    } else {
                        TimeUtils.formatDuration(durationMs)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onRemainingTimeClick)
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
    val filePath = remember(track) { FileUtils.resolveTrackPath(context, track) }
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
                val cleanTitle = track.title.takeUnless { it.isBlank() || it.equals("null", true) || it.equals("unknown", true) } ?: stringResource(R.string.unknown_title)
                val cleanArtist = track.artist.takeUnless { it.isBlank() || it.equals("null", true) || it.equals("unknown", true) } ?: stringResource(R.string.unknown_artist)
                val cleanAlbum = track.album.takeUnless { it.isBlank() || it.equals("null", true) || it.equals("unknown", true) } ?: stringResource(R.string.unknown_album)
                DetailRow(label = stringResource(R.string.detail_title), value = cleanTitle)
                DetailRow(label = stringResource(R.string.detail_artist), value = cleanArtist)
                DetailRow(label = stringResource(R.string.detail_album), value = cleanAlbum)
                DetailRow(label = stringResource(R.string.detail_duration), value = TimeUtils.formatDuration(track.durationMs))
                DetailRow(label = stringResource(R.string.detail_source), value = track.sourceType.name)
                if (fileSizeMb != null) {
                    DetailRow(label = stringResource(R.string.detail_file_size), value = fileSizeMb)
                }
                if (filePath != null) {
                    DetailRow(label = stringResource(R.string.detail_file_path), value = filePath)
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
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
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
            if (track.uri.startsWith("content://")) {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share_song)))
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
    }
}
