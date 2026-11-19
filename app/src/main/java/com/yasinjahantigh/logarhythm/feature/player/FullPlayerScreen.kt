package com.yasinjahantigh.logarhythm.feature.player

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
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.ui.draw.blur
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull
import java.util.Locale
import kotlin.math.abs
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.ArtworkColorExtractor
import com.yasinjahantigh.logarhythm.core.utils.FileUtils
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.equalizer.EqualizerScreen
import com.yasinjahantigh.logarhythm.feature.lyrics.LyricsView
import com.yasinjahantigh.logarhythm.feature.lyrics.LyricsViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.queue.QueueScreen
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
    onNavigateToTagEditor: (String) -> Unit = {},
    onNavigateToArtist: ((String) -> Unit)? = null,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    artistSplitViewModel: com.yasinjahantigh.logarhythm.feature.artist.ArtistSplitViewModel = hiltViewModel()
) {
    val isQueueOpen by viewModel.isQueueOpen.collectAsStateWithLifecycle()

    BackHandler {
        if (isQueueOpen) viewModel.closeQueue() else onCollapse()
    }

    if (isQueueOpen) {
        QueueScreen(
            viewModel = viewModel,
            onNavigateBack = viewModel::closeQueue,
            modifier = modifier,
            playlistViewModel = playlistViewModel
        )
        return
    }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val queueIndex by viewModel.queueIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    // Deliberately NOT read here: position ticks twice a second and would recompose this whole screen.
    val positionState = viewModel.position.collectAsStateWithLifecycle()
    val durationMs by viewModel.duration.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val slowedReverb by viewModel.slowedReverb.collectAsStateWithLifecycle()
    val abLoopStartMs by viewModel.abLoopStartMs.collectAsStateWithLifecycle()
    val abLoopEndMs by viewModel.abLoopEndMs.collectAsStateWithLifecycle()
    val doubleTapSeekEnabled by viewModel.doubleTapSeekEnabled.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val sleepTimerRemainingTracks by viewModel.sleepTimerRemainingTracks.collectAsStateWithLifecycle()
    val isSleepTimerEndOfTrack by viewModel.isSleepTimerEndOfTrack.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val artistSplitConfig by artistSplitViewModel.config.collectAsStateWithLifecycle()

    val isVinylMode by viewModel.isVinylMode.collectAsStateWithLifecycle()
    val audioOutputState by viewModel.audioOutputState.collectAsStateWithLifecycle()

    var showLyrics by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var showFullArtworkDialog by remember { mutableStateOf(false) }
    var showSongShareSheet by remember { mutableStateOf(false) }
    var isCarMode by remember { mutableStateOf(false) }

    val track = currentTrack
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val artworkDragOffsetX = remember { Animatable(0f) }

    if (showAudioOutputSheet) {
        AudioOutputPickerSheet(
            state = audioOutputState,
            onRefresh = viewModel::refreshAudioOutputs,
            onSelectDevice = { device ->
                val switched = viewModel.selectAudioOutput(device)
                if (!switched) {
                    showAudioOutputSheet = false
                    viewModel.openSystemAudioOutputSwitcher(context)
                }
            },
            onVolumeChange = viewModel::setMediaVolume,
            onOpenSystemSwitcher = {
                showAudioOutputSheet = false
                viewModel.openSystemAudioOutputSwitcher(context)
            },
            onDismiss = { showAudioOutputSheet = false }
        )
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSpeedSelected = viewModel::setPlaybackSpeed,
            currentPitch = playbackPitch,
            onPitchSelected = viewModel::setPlaybackPitch,
            slowedReverbEnabled = slowedReverb,
            onToggleSlowedReverb = viewModel::setSlowedReverb,
            onReset = viewModel::resetPlaybackSpeedAndPitch,
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (isCarMode) {
        CarModeOverlay(
            track = track,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            onTogglePlayPause = viewModel::togglePlayPause,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onToggleFavorite = viewModel::toggleFavorite,
            onExit = { isCarMode = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = sleepTimerRemainingMs,
            remainingTracks = sleepTimerRemainingTracks,
            isEndOfTrack = isSleepTimerEndOfTrack,
            onSetTimerMinutes = viewModel::setSleepTimer,
            onSetTimerTracks = viewModel::setSleepTimerTracks,
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

    if (showFullArtworkDialog && track != null) {
        FullArtworkDialog(
            track = track,
            onDismiss = { showFullArtworkDialog = false }
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
    // Blend the artwork color into the player surface so the whole screen follows the cover.
    val playerSurfaceColor = remember(animatedBgColor, surfaceColor) {
        val blended = androidx.core.graphics.ColorUtils.blendARGB(
            surfaceColor.toArgb(), animatedBgColor.toArgb(), 0.28f
        )
        Color(blended)
    }

    if (showSongShareSheet && track != null) {
        com.yasinjahantigh.logarhythm.ui.components.SongShareStudioSheet(
            track = track,
            defaultAccentColor = animatedBgColor.toArgb(),
            onShareAudioFile = { shareTrack(context, track) },
            onDismiss = { showSongShareSheet = false }
        )
    }

    val albumText = track?.album.cleanMetaOrNull()
    val timerLabel: String? = when {
        sleepTimerRemainingTracks != null -> {
            val count = sleepTimerRemainingTracks ?: 0
            if (count == 1) {
                stringResource(R.string.sleep_timer_end_of_track)
            } else {
                stringResource(R.string.sleep_timer_tracks_remaining, count)
            }
        }
        sleepTimerRemainingMs == null -> null
        isSleepTimerEndOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
        else -> TimeUtils.formatDuration(sleepTimerRemainingMs ?: 0L)
    }
    val speedLabel: String? = if (abs(playbackSpeed - 1.0f) > 0.01f || abs(playbackPitch - 1.0f) > 0.01f || slowedReverb) {
        String.format(Locale.US, "%.1fx", playbackSpeed)
    } else null

    val abLoopStatusText: String? = when {
        abLoopStartMs != null && abLoopEndMs != null ->
            "${TimeUtils.formatDuration(abLoopStartMs!!)} – ${TimeUtils.formatDuration(abLoopEndMs!!)}"
        abLoopStartMs != null ->
            "A: ${TimeUtils.formatDuration(abLoopStartMs!!)} → B?"
        else -> null
    }
    val abLoopMenuLabel: String? = when {
        abLoopStartMs == null -> null
        abLoopEndMs == null -> stringResource(R.string.player_ab_loop_set_b)
        else -> stringResource(R.string.player_ab_loop_clear)
    }

    val onRewind10 = { viewModel.seekTo((positionState.value - 10_000L).coerceAtLeast(0L)) }
    val onForward10 = { viewModel.seekTo((positionState.value + 10_000L).coerceAtMost(durationMs)) }

    // The middle of the screen: big artwork, or lyrics (with a compact song row in portrait).
    val stage: @Composable (Modifier, Boolean) -> Unit = { stageModifier, compactIdentity ->
        Box(modifier = stageModifier) {
            androidx.compose.animation.AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) +
                        androidx.compose.animation.scaleIn(initialScale = 0.94f, animationSpec = androidx.compose.animation.core.tween(260)))
                        .togetherWith(
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
                                androidx.compose.animation.scaleOut(targetScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(180))
                        )
                },
                label = "player_stage"
            ) { lyricsOn ->
                if (lyricsOn) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (compactIdentity) {
                            CompactTrackRow(
                                track = track,
                                isFavorite = isFavorite,
                                onToggleFavorite = viewModel::toggleFavorite,
                                audioOutputState = audioOutputState,
                                onOpenAudioOutput = { showAudioOutputSheet = true },
                                artistSplitConfig = artistSplitConfig,
                                onArtistClick = onNavigateToArtist
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
                        val artworkSize = minOf(maxWidth, maxHeight - 8.dp, 430.dp).coerceAtLeast(132.dp)
                        PlayerArtwork(
                            track = track,
                            isPlaying = isPlaying,
                            isVinylMode = isVinylMode,
                            glowColor = animatedBgColor,
                            queue = queue,
                            currentIndex = queueIndex,
                            doubleTapSeekEnabled = doubleTapSeekEnabled,
                            positionProvider = { positionState.value },
                            durationMs = durationMs,
                            onSeek = viewModel::seekTo,
                            onRewind10 = onRewind10,
                            onForward10 = onForward10,
                            onCommit = viewModel::commitCoverPeek,
                            onArtworkClick = { showFullArtworkDialog = true },
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
        color = playerSurfaceColor,
        contentColor = scheme.onSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerBackdrop(
                tint = animatedBgColor,
                glow = scheme.tertiary
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val isLandscapeLayout = maxWidth > maxHeight || maxWidth >= 700.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1120.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = if (isLandscapeLayout) 32.dp else 24.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FullPlayerHeader(
                        isPlaying = isPlaying,
                        subtitle = albumText,
                        isVinylMode = isVinylMode,
                        onCollapse = onCollapse,
                        onToggleVinyl = { viewModel.setVinylMode(!isVinylMode) },
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
                                showSongShareSheet = true
                            }
                        },
                        onEditSongDetails = { track?.let { onNavigateToTagEditor(it.id) } },
                        abLoopLabel = abLoopMenuLabel,
                        onToggleAbLoop = viewModel::toggleAbLoopMarker,
                        onOpenCarMode = { isCarMode = true },
                        onHideSong = {
                            viewModel.hideCurrentTrack { hidden ->
                                Toast.makeText(context, context.getString(R.string.track_hidden_toast, hidden.title), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    if (!showLyrics) {
                        com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                            hintId = if (isVinylMode) {
                                com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.PLAYER_VINYL
                            } else {
                                com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.PLAYER_COVER
                            },
                            text = stringResource(
                                if (isVinylMode) R.string.tip_player_vinyl else R.string.tip_player_cover
                            ),
                            icon = if (isVinylMode) Icons.Filled.Album else Icons.Filled.TouchApp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                    }

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
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    audioOutputState = audioOutputState,
                                    onOpenAudioOutput = { showAudioOutputSheet = true },
                                    artistSplitConfig = artistSplitConfig,
                                    onArtistClick = onNavigateToArtist
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
                                onToggleFavorite = viewModel::toggleFavorite,
                                audioOutputState = audioOutputState,
                                onOpenAudioOutput = { showAudioOutputSheet = true },
                                artistSplitConfig = artistSplitConfig,
                                onArtistClick = onNavigateToArtist
                            )
                        }

                        if (abLoopStatusText != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                onClick = viewModel::toggleAbLoopMarker,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "A–B · $abLoopStatusText",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.player_ab_loop_clear),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.clearAbLoop() },
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
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
private val HeroArtworkShape = RoundedCornerShape(32.dp)

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
            .drawWithCache {
                val topGlow = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.06f),
                    radius = size.maxDimension * 0.9f
                )
                val bottomGlow = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.96f, size.height * 0.88f),
                    radius = size.maxDimension * 0.75f
                )
                val scrim = Brush.verticalGradient(
                    colors = listOf(
                        surface.copy(alpha = 0.30f),
                        surface.copy(alpha = 0.80f),
                        surface
                    )
                )
                onDrawBehind {
                    drawRect(brush = topGlow)
                    drawRect(brush = bottomGlow)
                    drawRect(brush = scrim)
                }
            }
    )
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
    queue: List<Track>,
    currentIndex: Int,
    doubleTapSeekEnabled: Boolean,
    positionProvider: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onCommit: (Int, Int) -> Unit,
    onArtworkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVinylMode) {
        VinylSleeveStage(
            track = track,
            isPlaying = isPlaying,
            queue = queue,
            currentIndex = currentIndex,
            positionProvider = positionProvider,
            durationMs = durationMs,
            onSeek = onSeek,
            onCommit = onCommit,
            onArtworkClick = onArtworkClick,
            modifier = modifier
        )
        return
    }

    val haptic = LocalHapticFeedback.current
    val dragX = remember(currentIndex, queue) { Animatable(0f) }
    var coverWidth by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 200f),
        label = "artwork_breathing"
    )
    val shape = HeroArtworkShape
    val title = track?.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)

    Box(
        modifier = modifier
            .onSizeChanged { coverWidth = it.width.toFloat() }
            .pointerInput(currentIndex, queue) {
                kotlinx.coroutines.coroutineScope {
                    var distance = 0f
                    var motion: kotlinx.coroutines.Job? = null
                    detectHorizontalDragGestures(
                        onDragStart = { motion?.cancel(); distance = dragX.value },
                        onDragEnd = {
                            motion = launch {
                                val target = coverPeekTarget(distance, coverWidth, currentIndex, queue.size, true)
                                if (target != null) {
                                    dragX.animateTo(if (distance > 0) coverWidth else -coverWidth, tween(170))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCommit(currentIndex, target)
                                    dragX.snapTo(0f)
                                } else dragX.animateTo(0f, spring())
                            }
                        },
                        onDragCancel = { motion = launch { dragX.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val proposed = (distance + amount).coerceIn(-coverWidth, coverWidth)
                            distance = if (coverPeekTarget(proposed, coverWidth, currentIndex, queue.size, false) != null) proposed else 0f
                            launch { dragX.snapTo(distance) }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Feathered circular ambient glow behind the cover — cached Brush, zero per-frame allocations.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val glowBrush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isPlaying) 0.34f else 0.16f),
                            glowColor.copy(alpha = if (isPlaying) 0.12f else 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.62f
                    )
                    onDrawBehind {
                        drawCircle(brush = glowBrush)
                    }
                }
        )

        val peekIndex by remember(currentIndex, queue, coverWidth) {
            androidx.compose.runtime.derivedStateOf {
                coverPeekTarget(dragX.value, coverWidth, currentIndex, queue.size, false)
            }
        }
        if (peekIndex != null) {
            val neighbour = queue[peekIndex!!]
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .graphicsLayer {
                        translationX = dragX.value + if (dragX.value > 0) -coverWidth else coverWidth
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
            ) {
                ArtworkImage(
                    uri = neighbour.artworkUri,
                    fallbackKey = neighbour.title,
                    shape = shape,
                    glyphSize = 96.sp,
                    contentDescription = stringResource(R.string.cd_artwork, neighbour.title),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = neighbour.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(16.dp)
                )
            }
        }

        var seekBadge by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(seekBadge) {
            if (seekBadge != null) {
                kotlinx.coroutines.delay(550)
                seekBadge = null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .graphicsLayer {
                    translationX = dragX.value
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = shape,
                shadowElevation = if (isPlaying) 14.dp else 6.dp,
                color = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            ) {
                ArtworkImage(
                    uri = track?.artworkUri,
                    fallbackKey = title,
                    shape = shape,
                    glyphSize = 96.sp,
                    contentDescription = stringResource(R.string.cd_artwork, title),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(doubleTapSeekEnabled, onArtworkClick, onRewind10, onForward10) {
                            detectTapGestures(
                                onTap = { onArtworkClick() },
                                onDoubleTap = if (doubleTapSeekEnabled) { offset ->
                                    if (offset.x < size.width / 2f) {
                                        onRewind10()
                                        seekBadge = "−10s"
                                    } else {
                                        onForward10()
                                        seekBadge = "+10s"
                                    }
                                } else null
                            )
                        }
                )
            }
            seekBadge?.let { label ->
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
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
    val haptic = LocalHapticFeedback.current
    val favoriteContainer by animateColorAsState(
        targetValue = if (isFavorite) scheme.primaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.6f),
        animationSpec = tween(250),
        label = "favorite_container"
    )
    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
        label = "favorite_scale"
    )
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggleFavorite()
        },
        modifier = modifier
            .size(52.dp)
            .background(favoriteContainer, CircleShape)
            .testTag("player_favorite_button")
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
            tint = if (isFavorite) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = heartScale
                    scaleY = heartScale
                }
        )
    }
}

@Composable
private fun AudioOutputButton(
    audioOutputState: com.yasinjahantigh.logarhythm.playback.AudioOutputState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isExternal = audioOutputState.activeKind != com.yasinjahantigh.logarhythm.playback.AudioOutputKind.SPEAKER
    val container by animateColorAsState(
        targetValue = if (isExternal) scheme.secondaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.6f),
        animationSpec = tween(250),
        label = "audio_output_container"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(container, CircleShape)
            .testTag("player_audio_output_button")
    ) {
        Icon(
            imageVector = audioOutputState.activeKind.icon(),
            contentDescription = stringResource(R.string.cd_audio_output_switcher),
            tint = if (isExternal) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Small song row shown above the lyrics (portrait), so the song is never anonymous. */
@Composable
private fun CompactTrackRow(
    track: Track?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    audioOutputState: com.yasinjahantigh.logarhythm.playback.AudioOutputState = com.yasinjahantigh.logarhythm.playback.AudioOutputState(),
    onOpenAudioOutput: (() -> Unit)? = null,
    artistSplitConfig: com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig = com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig(),
    onArtistClick: ((String) -> Unit)? = null
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
            com.yasinjahantigh.logarhythm.ui.components.SplitArtistText(
                rawArtist = track?.artist,
                displayArtist = artist,
                artistSplitConfig = artistSplitConfig,
                onArtistClick = onArtistClick,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onOpenAudioOutput != null) {
                AudioOutputButton(audioOutputState = audioOutputState, onClick = onOpenAudioOutput)
            }
            FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
        }
    }
}

@Composable
fun TrackIdentityAndActions(
    track: Track?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    audioOutputState: com.yasinjahantigh.logarhythm.playback.AudioOutputState = com.yasinjahantigh.logarhythm.playback.AudioOutputState(),
    onOpenAudioOutput: (() -> Unit)? = null,
    artistSplitConfig: com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig = com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig(),
    onArtistClick: ((String) -> Unit)? = null
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
            TrackFormatLabel(track)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(initialDelayMillis = 1800)
            )
            Spacer(modifier = Modifier.height(2.dp))
            com.yasinjahantigh.logarhythm.ui.components.SplitArtistText(
                rawArtist = track?.artist,
                displayArtist = displayArtist,
                artistSplitConfig = artistSplitConfig,
                onArtistClick = onArtistClick,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onOpenAudioOutput != null) {
                AudioOutputButton(audioOutputState = audioOutputState, onClick = onOpenAudioOutput)
            }
            FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
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
            if (f.exists()) String.format(Locale.US, "%.2f MB", f.length().toDouble() / (1024 * 1024)) else null
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
        Toast.makeText(context, context.getString(R.string.cannot_share_file), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FullArtworkDialog(
    track: Track?,
    onDismiss: () -> Unit
) {
    if (track == null) return
    val title = track.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)
    val album = track.album.cleanMetaOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .aspectRatio(1f)
                        .shadow(32.dp, RoundedCornerShape(28.dp), spotColor = Color.White.copy(alpha = 0.25f))
                        .clip(RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkImage(
                        uri = track.artworkUri,
                        fallbackKey = title,
                        shape = RoundedCornerShape(28.dp),
                        glyphSize = 120.sp,
                        contentDescription = stringResource(R.string.cd_artwork, title),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (!album.isNullOrBlank()) "$artist · $album" else artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.16f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
