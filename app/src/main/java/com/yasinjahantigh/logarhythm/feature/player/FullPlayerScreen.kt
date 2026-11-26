package com.yasinjahantigh.logarhythm.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
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
    val artistInfo by viewModel.currentArtistInfo.collectAsStateWithLifecycle()
    val artistsInfo by viewModel.currentArtistsInfo.collectAsStateWithLifecycle()
    val uiVisibility by viewModel.uiVisibility.collectAsStateWithLifecycle()
    val isInstrumentalSilence by lyricsViewModel.isInstrumentalSilence.collectAsStateWithLifecycle()

    var showLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(showLyrics) {
        com.yasinjahantigh.logarhythm.data.analytics.AppDiagnosticContext.onLyricsOpenChanged(showLyrics)
    }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var showFullArtworkDialog by remember { mutableStateOf(false) }
    var showSongShareSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAboutArtistSheet by remember { mutableStateOf(false) }
    var isCarMode by remember { mutableStateOf(false) }

    val track = currentTrack
    val nextTrack = remember(queue, queueIndex) { queue.getOrNull(queueIndex + 1) }
    val silencePulseActive = isPlaying && isInstrumentalSilence
    val context = LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()

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

    if (showDeleteConfirmDialog && track != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_song_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_delete_song_confirm,
                        track.title.cleanMetaOrNull() ?: track.title
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetTrack = track
                        showDeleteConfirmDialog = false
                        mediaStoreConsent.delete(
                            track = targetTrack,
                            onApproved = { approvedTrack ->
                                viewModel.deleteCurrentTrack(approvedTrack) { deleted ->
                                    Toast.makeText(
                                        context,
                                        context.getString(if (deleted) R.string.song_deleted else R.string.track_delete_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onCancelled = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.track_delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("player_delete_confirm_button")
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    modifier = Modifier.testTag("player_delete_cancel_button")
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showAboutArtistSheet && track != null) {
        AboutArtistSheet(
            track = track,
            artistsInfo = artistsInfo.ifEmpty { listOfNotNull(artistInfo) },
            onPlayTrack = { t -> viewModel.play(listOf(t), 0) },
            onOpenArtistPage = onNavigateToArtist,
            onDismiss = { showAboutArtistSheet = false }
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
    // Subtle tint into the base surface so artwork color enriches the stage without turning controls muddy.
    val playerSurfaceColor = remember(animatedBgColor, surfaceColor) {
        val blended = androidx.core.graphics.ColorUtils.blendARGB(
            surfaceColor.toArgb(), animatedBgColor.toArgb(), 0.14f
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

    val primaryArtistPhotoUri = artistsInfo.firstOrNull()?.photoUri

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
                                onOpenAudioOutput = if (uiVisibility.playerShowOutputButton) {
                                    { showAudioOutputSheet = true }
                                } else null,
                                artistPhotoUri = primaryArtistPhotoUri,
                                showArtistBadge = uiVisibility.showArtistPortraitBadges,
                                onOpenAboutArtist = { showAboutArtistSheet = true }
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
                            silencePulseActive = silencePulseActive,
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
                glow = scheme.tertiary,
                silencePulseActive = silencePulseActive
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val isLandscapeLayout = maxWidth > maxHeight || maxWidth >= 700.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1120.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = if (isLandscapeLayout) 32.dp else 22.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FullPlayerHeader(
                        isPlaying = isPlaying,
                        subtitle = albumText?.takeUnless {
                            it.equals(track?.artist.cleanMetaOrNull(), ignoreCase = true)
                        },
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
                        },
                        onDeleteSong = if (track != null) {
                            { showDeleteConfirmDialog = true }
                        } else null,
                        nextTrackTitle = nextTrack?.title.cleanMetaOrNull(),
                        nextTrackArtworkUri = nextTrack?.artworkUri,
                        showNextInHeader = uiVisibility.playerShowUpNextHeader,
                        onOpenQueue = viewModel::openQueue
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
                                    onOpenAudioOutput = if (uiVisibility.playerShowOutputButton) {
                                        { showAudioOutputSheet = true }
                                    } else null,
                                    showFormatBadge = uiVisibility.playerShowFormatBadge,
                                    showArtistBadge = uiVisibility.showArtistPortraitBadges,
                                    artistPhotoUri = primaryArtistPhotoUri,
                                    onOpenAboutArtist = { showAboutArtistSheet = true }
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
                                    showSeekButtons = uiVisibility.playerShowSeekButtons,
                                    useWaveform = uiVisibility.playerWaveformSeekbar,
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
                                if (uiVisibility.playerShowQuickActions) {
                                    PlayerQuickActions(
                                        showLyrics = showLyrics,
                                        onToggleLyrics = { showLyrics = !showLyrics },
                                        timerLabel = timerLabel,
                                        onOpenTimer = { showSleepTimerDialog = true },
                                        speedLabel = speedLabel,
                                        onOpenSpeed = { showSpeedDialog = true },
                                        onOpenQueue = viewModel::openQueue
                                    )
                                }
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
                                onOpenAudioOutput = if (uiVisibility.playerShowOutputButton) {
                                    { showAudioOutputSheet = true }
                                } else null,
                                showFormatBadge = uiVisibility.playerShowFormatBadge,
                                showArtistBadge = uiVisibility.showArtistPortraitBadges,
                                artistPhotoUri = primaryArtistPhotoUri,
                                onOpenAboutArtist = { showAboutArtistSheet = true }
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
                            showSeekButtons = uiVisibility.playerShowSeekButtons,
                            useWaveform = uiVisibility.playerWaveformSeekbar,
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
                            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                        )

                        if (uiVisibility.playerShowQuickActions) {
                            PlayerQuickActions(
                                showLyrics = showLyrics,
                                onToggleLyrics = { showLyrics = !showLyrics },
                                timerLabel = timerLabel,
                                onOpenTimer = { showSleepTimerDialog = true },
                                speedLabel = speedLabel,
                                onOpenSpeed = { showSpeedDialog = true },
                                onOpenQueue = viewModel::openQueue,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
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
    silencePulseActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val surface = MaterialTheme.colorScheme.surface
    val pulseMix by animateFloatAsState(
        targetValue = if (silencePulseActive) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "backdrop_silence_mix"
    )
    val breathTransition = rememberInfiniteTransition(label = "backdrop_silence_breath")
    val breathWave by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdrop_silence_wave"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val topGlow = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.48f), tint.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.maxDimension * 0.78f
                )
                val bottomGlow = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.82f),
                    radius = size.maxDimension * 0.65f
                )
                val silenceHalo = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.42f), glow.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.36f),
                    radius = size.maxDimension * 0.72f
                )
                val scrim = Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.20f),
                    0.48f to surface.copy(alpha = 0.68f),
                    0.78f to surface.copy(alpha = 0.94f),
                    1f to surface
                )
                onDrawBehind {
                    drawRect(brush = topGlow)
                    drawRect(brush = bottomGlow)
                    if (pulseMix > 0.01f) {
                        drawRect(
                            brush = silenceHalo,
                            alpha = (pulseMix * (0.35f + 0.65f * breathWave)).coerceIn(0f, 1f)
                        )
                    }
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
    silencePulseActive: Boolean,
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
        val haloMix by animateFloatAsState(
            targetValue = if (silencePulseActive) 1f else 0f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "artwork_silence_mix"
        )
        val haloTransition = rememberInfiniteTransition(label = "artwork_silence_halo")
        val haloWave by haloTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "artwork_silence_wave"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val pulseScale = 1f + haloMix * (0.04f + 0.08f * haloWave)
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
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
                    val silenceRingBrush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.45f),
                            glowColor.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.68f
                    )
                    onDrawBehind {
                        drawCircle(brush = glowBrush)
                        if (haloMix > 0.01f) {
                            drawCircle(
                                brush = silenceRingBrush,
                                alpha = (haloMix * (0.35f + 0.65f * haloWave)).coerceIn(0f, 1f)
                            )
                        }
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
        targetValue = if (isFavorite) scheme.primary.copy(alpha = 0.20f) else scheme.onSurface.copy(alpha = 0.08f),
        animationSpec = tween(250),
        label = "favorite_container"
    )
    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
        label = "favorite_scale"
    )
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggleFavorite()
        },
        modifier = modifier
            .size(42.dp)
            .background(favoriteContainer, CircleShape)
            .testTag("player_favorite_button")
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
            tint = if (isFavorite) scheme.primary else scheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier
                .size(21.dp)
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
        targetValue = if (isExternal) scheme.primary.copy(alpha = 0.16f) else scheme.onSurface.copy(alpha = 0.08f),
        animationSpec = tween(250),
        label = "audio_output_container"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(42.dp)
            .background(container, CircleShape)
            .testTag("player_audio_output_button")
    ) {
        Icon(
            imageVector = audioOutputState.activeKind.icon(),
            contentDescription = stringResource(R.string.cd_audio_output_switcher),
            tint = if (isExternal) scheme.primary else scheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ArtistActionPill(
    artistName: String,
    artistPhotoUri: String?,
    showArtistBadge: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 2.dp)
    ) {
        if (showArtistBadge) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (!artistPhotoUri.isNullOrBlank()) {
                    ArtworkImage(
                        uri = artistPhotoUri,
                        fallbackKey = artistName,
                        shape = CircleShape,
                        glyphSize = 10.sp,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = artistName,
            style = textStyle,
            color = scheme.primary.copy(alpha = 0.94f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.player_about_artist),
                tint = scheme.primary.copy(alpha = 0.72f),
                modifier = Modifier.size(16.dp)
            )
        }
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
    artistPhotoUri: String? = null,
    showArtistBadge: Boolean = true,
    onOpenAboutArtist: (() -> Unit)? = null
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
            ArtistActionPill(
                artistName = artist,
                artistPhotoUri = artistPhotoUri,
                showArtistBadge = showArtistBadge,
                textStyle = MaterialTheme.typography.bodyMedium,
                onClick = if (track != null) onOpenAboutArtist else null
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
    showFormatBadge: Boolean = true,
    showArtistBadge: Boolean = true,
    artistPhotoUri: String? = null,
    onOpenAboutArtist: (() -> Unit)? = null
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 2400,
                    spacing = MarqueeSpacing(48.dp),
                    velocity = 28.dp
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            ArtistActionPill(
                artistName = displayArtist,
                artistPhotoUri = artistPhotoUri,
                showArtistBadge = showArtistBadge,
                textStyle = MaterialTheme.typography.titleMedium,
                onClick = if (track != null) onOpenAboutArtist else null,
                modifier = Modifier.testTag("player_about_artist_pill")
            )
            if (showFormatBadge) {
                Spacer(modifier = Modifier.height(2.dp))
                TrackFormatLabel(track)
            }
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
