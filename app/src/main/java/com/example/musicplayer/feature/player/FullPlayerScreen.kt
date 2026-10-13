package com.example.musicplayer.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.sp
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
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.ui.draw.blur
import com.example.musicplayer.ui.components.ArtworkImage
import com.example.musicplayer.ui.components.cleanMetaOrNull
import java.util.Locale
import kotlin.math.abs
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

    val scheme = MaterialTheme.colorScheme
    val surfaceColor = scheme.surface
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
        animationSpec = tween(durationMillis = 700),
        label = "player_bg_color"
    )

    val albumText = track?.album.cleanMetaOrNull()
    val timerLabel: String? = when {
        sleepTimerRemainingMs == null -> null
        isSleepTimerEndOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
        else -> TimeUtils.formatDuration(sleepTimerRemainingMs ?: 0L)
    }
    val speedLabel: String? = if (abs(playbackSpeed - 1.0f) > 0.01f) {
        String.format(Locale.US, "%.1fx", playbackSpeed)
    } else null

    val onRewind10 = { viewModel.seekTo((positionState.value - 10_000L).coerceAtLeast(0L)) }
    val onForward10 = { viewModel.seekTo((positionState.value + 10_000L).coerceAtMost(durationMs)) }

    // The middle of the screen: big artwork, or lyrics (with a compact song row in portrait).
    val stage: @Composable (Modifier, Boolean) -> Unit = { stageModifier, compactIdentity ->
        Box(modifier = stageModifier) {
            Crossfade(targetState = showLyrics, label = "player_stage") { lyricsOn ->
                if (lyricsOn) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (compactIdentity) {
                            CompactTrackRow(
                                track = track,
                                isFavorite = isFavorite,
                                onToggleFavorite = viewModel::toggleFavorite
                            )
                        }
                        LyricsView(
                            viewModel = lyricsViewModel,
                            onNext = viewModel::next,
                            onPrevious = viewModel::previous,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                } else {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // The artwork is the hero: as large as the space allows, never clipped.
                        val artworkSize = minOf(maxWidth, maxHeight - 8.dp, 400.dp).coerceAtLeast(120.dp)
                        PlayerArtwork(
                            track = track,
                            isPlaying = isPlaying,
                            isVinylMode = isVinylMode,
                            glowColor = animatedBgColor,
                            onSwipeNext = viewModel::next,
                            onSwipePrevious = viewModel::previous,
                            modifier = Modifier.size(artworkSize)
                        )
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("full_player_screen"),
        color = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerBackdrop(
                tint = animatedBgColor,
                glow = scheme.tertiary
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscapeLayout = maxWidth > maxHeight

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = if (isLandscapeLayout) 28.dp else 24.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FullPlayerHeader(
                        isPlaying = isPlaying,
                        subtitle = albumText,
                        isVinylMode = isVinylMode,
                        onCollapse = onCollapse,
                        onToggleVinyl = { isVinylMode = !isVinylMode },
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

                    if (isLandscapeLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            stage(Modifier.weight(0.95f).fillMaxHeight(), false)

                            Column(
                                modifier = Modifier
                                    .weight(1.05f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Center
                            ) {
                                TrackIdentityAndActions(
                                    track = track,
                                    isFavorite = isFavorite,
                                    onToggleFavorite = viewModel::toggleFavorite
                                )
                                PlaybackTimeline(
                                    positionState = positionState,
                                    durationMs = durationMs,
                                    trackKey = track?.id.orEmpty(),
                                    showRemainingTime = showRemainingTime,
                                    onRemainingTimeClick = { showRemainingTime = !showRemainingTime },
                                    onSeek = viewModel::seekTo,
                                    onRewind10 = onRewind10,
                                    onForward10 = onForward10,
                                    modifier = Modifier.padding(top = 6.dp)
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
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    compact = true
                                )
                                PlayerQuickActions(
                                    showLyrics = showLyrics,
                                    onToggleLyrics = { showLyrics = !showLyrics },
                                    onOpenQueue = viewModel::openQueue,
                                    timerLabel = timerLabel,
                                    onOpenTimer = { showSleepTimerDialog = true },
                                    speedLabel = speedLabel,
                                    onOpenSpeed = { showSpeedDialog = true }
                                )
                            }
                        }
                    } else {
                        stage(Modifier.fillMaxWidth().weight(1f), true)

                        if (!showLyrics) {
                            Spacer(modifier = Modifier.height(14.dp))
                            TrackIdentityAndActions(
                                track = track,
                                isFavorite = isFavorite,
                                onToggleFavorite = viewModel::toggleFavorite
                            )
                        }

                        PlaybackTimeline(
                            positionState = positionState,
                            durationMs = durationMs,
                            trackKey = track?.id.orEmpty(),
                            showRemainingTime = showRemainingTime,
                            onRemainingTimeClick = { showRemainingTime = !showRemainingTime },
                            onSeek = viewModel::seekTo,
                            onRewind10 = onRewind10,
                            onForward10 = onForward10,
                            modifier = Modifier.padding(top = 8.dp)
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        PlayerQuickActions(
                            showLyrics = showLyrics,
                            onToggleLyrics = { showLyrics = !showLyrics },
                            onOpenQueue = viewModel::openQueue,
                            timerLabel = timerLabel,
                            onOpenTimer = { showSleepTimerDialog = true },
                            speedLabel = speedLabel,
                            onOpenSpeed = { showSpeedDialog = true },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backdrop: the artwork's colour pools at the top and a second theme-tinted glow sits at the
 * bottom, then a scrim fades it into the surface so text always stays readable. Deliberately two
 * cheap gradients (drawn once) instead of a blurred copy of the cover, which costs a full-screen
 * GPU blur and made the player feel heavy on mid-range phones.
 */
@Composable
private fun PlayerBackdrop(
    tint: Color,
    glow: Color,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.06f),
                        radius = size.maxDimension * 0.9f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.96f, size.height * 0.88f),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            surface.copy(alpha = 0.30f),
                            surface.copy(alpha = 0.80f),
                            surface
                        )
                    )
                )
        )
    }
}

/**
 * The hero artwork. It breathes with playback (full size while playing, gently receded while
 * paused), casts a soft shadow tinted by its own colours, and can be swiped sideways to skip.
 * Vinyl mode (from the menu) keeps the same footprint and gestures.
 */
@Composable
private fun PlayerArtwork(
    track: Track?,
    isPlaying: Boolean,
    isVinylMode: Boolean,
    glowColor: Color,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 180f),
        label = "artwork_breathing"
    )
    val shape = RoundedCornerShape(36.dp)
    val title = track?.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dragX.value < -120f) onSwipeNext()
                                else if (dragX.value > 120f) onSwipePrevious()
                                dragX.animateTo(0f, spring())
                            }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { dragX.snapTo((dragX.value + amount).coerceIn(-200f, 200f)) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isVinylMode) {
                CuteVinylTurntable(track, isPlaying, Modifier.fillMaxSize())
            } else {
                ArtworkImage(
                    uri = track?.artworkUri,
                    fallbackKey = title,
                    shape = shape,
                    glyphSize = 96.sp,
                    contentDescription = stringResource(R.string.cd_artwork, title),
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 18.dp,
                            shape = shape,
                            ambientColor = glowColor.copy(alpha = 0.6f),
                            spotColor = glowColor
                        )
                )
            }
        }
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val favoriteContainer by animateColorAsState(
        targetValue = if (isFavorite) scheme.primaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.6f),
        animationSpec = tween(250),
        label = "favorite_container"
    )
    IconButton(
        onClick = onToggleFavorite,
        modifier = modifier
            .size(52.dp)
            .background(favoriteContainer, CircleShape)
            .testTag("player_favorite_button")
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
            tint = if (isFavorite) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Small song row shown above the lyrics (portrait), so the song is never anonymous. */
@Composable
private fun CompactTrackRow(
    track: Track?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = track?.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track?.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            uri = track?.artworkUri,
            fallbackKey = title,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            contentDescription = stringResource(R.string.cd_artwork, title)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
    }
}

@Composable
fun TrackIdentityAndActions(
    track: Track?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = track?.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val displayArtist = track?.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(initialDelayMillis = 1800)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayArtist,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
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
