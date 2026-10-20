package com.yasinjahantigh.logarhythm.feature.lyrics

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

@Composable
fun LyricsView(
    viewModel: LyricsViewModel,
    modifier: Modifier = Modifier,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {}
) {
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeLineIndex.collectAsStateWithLifecycle()
    val offsetMs by viewModel.offsetMs.collectAsStateWithLifecycle()
    val isHidden by viewModel.isHidden.collectAsStateWithLifecycle()
    val isMismatch by viewModel.lyricsMismatch.collectAsStateWithLifecycle()
    var showOffsetDialog by remember { mutableStateOf(false) }

    if (showOffsetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOffsetDialog = false },
            title = { Text(stringResource(R.string.lyrics_offset_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = String.format(java.util.Locale.ROOT, "%+.1f s", offsetMs / 1000f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.lyrics_offset_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.FilledTonalButton(onClick = { viewModel.nudgeOffset(-500L) }) {
                            Text(stringResource(R.string.lyrics_offset_later))
                        }
                        androidx.compose.material3.FilledTonalButton(onClick = { viewModel.nudgeOffset(500L) }) {
                            Text(stringResource(R.string.lyrics_offset_earlier))
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showOffsetDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.resetOffset() }) {
                    Text(stringResource(R.string.lyrics_offset_reset))
                }
            }
        )
    }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isOnlineEnabled by viewModel.isOnlineEnabled.collectAsStateWithLifecycle()
    val isManualEditOpen by viewModel.isManualEditOpen.collectAsStateWithLifecycle()
    val showCandidatePicker by viewModel.showCandidatePicker.collectAsStateWithLifecycle()
    val lyricsCandidates by viewModel.lyricsCandidates.collectAsStateWithLifecycle()
    val isSearchingCandidates by viewModel.isSearchingCandidates.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var trackForLyricsDeletion by remember { mutableStateOf<com.yasinjahantigh.logarhythm.domain.model.Track?>(null) }

    val context = LocalContext.current
    val mediaStoreConsent = rememberMediaStoreConsent()
    val requestLyricsWrite: ((com.yasinjahantigh.logarhythm.domain.model.Track) -> Unit) -> Unit = { action ->
        val targetTrack = currentTrack
        if (targetTrack != null) {
            mediaStoreConsent.write(
                track = targetTrack,
                onApproved = { action(targetTrack) }, // Bind the consent to the exact requested track.
                onCancelled = { viewModel.reportSaveFailure() }
            )
        } else viewModel.reportSaveFailure()
    }
    LaunchedEffect(saveMessage) {
        val message = when (saveMessage) {
            "embedded" -> context.getString(R.string.lyrics_embedded_success)
            "cached_only" -> context.getString(R.string.lyrics_cached_only)
            "failed" -> context.getString(R.string.lyrics_embed_failed)
            "deleted" -> context.getString(R.string.lyrics_deleted)
            "hidden" -> context.getString(R.string.lyrics_hidden_in_app)
            "delete_failed" -> context.getString(R.string.lyrics_delete_failed)
            "reset_failed" -> context.getString(R.string.lyrics_reset_failed)
            else -> null
        }
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeSaveMessage()
        }
    }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            requestLyricsWrite { track -> viewModel.importLyricsFromUri(uri, track) }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportLyricsToUri(uri)
            Toast.makeText(context, context.getString(R.string.lyrics_saved_to_file), Toast.LENGTH_SHORT).show()
        }
    }

    val onSearchGoogleLyrics: () -> Unit = {
        val title = currentTrack?.title.orEmpty().trim()
        val artist = currentTrack?.artist.orEmpty().trim()
        val query = "$title $artist lyrics"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
        context.startActivity(intent)
    }

    val onPasteFromClipboard: () -> Unit = {
        val clipText = clipboardManager.getText()?.text
        if (!clipText.isNullOrBlank()) {
            requestLyricsWrite { track -> viewModel.pasteAndSaveLyrics(clipText, track) }
        } else {
            Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteConfirmDialog) {
        val doomed = trackForLyricsDeletion
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                trackForLyricsDeletion = null
            },
            title = { Text(text = stringResource(R.string.dialog_delete_lyrics_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_delete_lyrics_confirm, doomed?.title.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Most common case: the lyrics are simply wrong. One tap, no system prompt.
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            trackForLyricsDeletion = null
                            if (doomed != null) viewModel.deleteLyrics(doomed, removeFromFile = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_delete_in_app_button")
                    ) {
                        Text(text = stringResource(R.string.lyrics_remove_in_app))
                    }
                    Text(
                        text = stringResource(R.string.lyrics_remove_in_app_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Also strips the copy stored inside the audio file; needs the system's write consent.
                    OutlinedButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            trackForLyricsDeletion = null
                            if (doomed != null) {
                                mediaStoreConsent.write(
                                    track = doomed,
                                    onApproved = { viewModel.deleteLyrics(doomed, removeFromFile = true) },
                                    onCancelled = { viewModel.deleteLyrics(doomed, removeFromFile = false) }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_delete_everywhere_button")
                    ) {
                        Text(text = stringResource(R.string.lyrics_remove_everywhere))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        trackForLyricsDeletion = null
                    }
                ) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (isManualEditOpen) {
        ManualLyricsDialog(
            initialContent = lyrics?.let {
                if (it.isSynced) {
                    it.lines.joinToString("\n") { line ->
                        val min = line.timestampMs / 60_000L
                        val sec = (line.timestampMs % 60_000L) / 1_000L
                        val frac = (line.timestampMs % 1_000L) / 10L
                        String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                    }
                } else it.plainText.orEmpty()
            }.orEmpty(),
            onDismiss = { viewModel.setManualEditOpen(false) },
            onSave = { text -> requestLyricsWrite { track -> viewModel.saveManualLyrics(text, track) } },
            onSearchGoogle = onSearchGoogleLyrics,
            onPasteClipboard = {
                val clipText = clipboardManager.getText()?.text
                if (!clipText.isNullOrBlank()) {
                    clipText
                } else null
            }
        )
    }

    if (showCandidatePicker) {
        LyricsCandidatePickerDialog(
            initialTitle = currentTrack?.title.orEmpty(),
            initialArtist = currentTrack?.artist.orEmpty(),
            candidates = lyricsCandidates,
            isSearching = isSearchingCandidates,
            onDismiss = viewModel::dismissCandidatePicker,
            onSearch = { title, artist -> viewModel.searchCandidatesOnline(title, artist) },
            onSelectCandidate = { candidate ->
                requestLyricsWrite { track -> viewModel.selectCandidate(candidate, track) }
            },
            onResetToDefault = {
                viewModel.resetLyricsToDefault(currentTrack)
                viewModel.dismissCandidatePicker()
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val current = swipeOffsetX.value
                            if (current < -120f) {
                                onNext()
                            } else if (current > 120f) {
                                onPrevious()
                            }
                            swipeOffsetX.animateTo(0f, spring())
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { swipeOffsetX.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            swipeOffsetX.snapTo((swipeOffsetX.value + dragAmount).coerceIn(-200f, 200f))
                        }
                    }
                )
            }
            .testTag("lyrics_view"),
        color = Color.Transparent,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.lyrics_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                isHidden -> {
                    HiddenLyricsContent(onRestore = viewModel::restoreLyrics)
                }

                lyrics == null || lyrics?.isEmpty == true -> {
                    LyricsEmptyContent(
                        isOnlineEnabled = isOnlineEnabled,
                        onImportClick = { filePicker.launch(arrayOf("*/*", "text/*")) },
                        onFetchOnlineClick = { viewModel.searchCandidatesOnline() },
                        onSearchGoogleClick = onSearchGoogleLyrics,
                        onPasteClipboardClick = onPasteFromClipboard,
                        onManualEnterClick = { viewModel.setManualEditOpen(true) }
                    )
                }

                lyrics?.isSynced == true -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isMismatch) {
                            MismatchBanner(
                                onHide = { viewModel.deleteLyrics(removeFromFile = false) },
                                onFindOthers = { viewModel.searchCandidatesOnline() }
                            )
                        }
                        SyncedLyricsContent(
                            lyrics = lyrics!!,
                            activeIndex = activeIndex,
                            offsetMs = offsetMs,
                            positionFlow = viewModel.livePositionMs,
                            onSeekTo = viewModel::seekTo
                        )
                    }
                }

                else -> {
                    PlainLyricsContent(plainText = lyrics?.plainText.orEmpty())
                }
            }

            // Slim header: what kind of lyrics these are and where they came from on the leading
            // side; the one action people reach for (find another version) plus an overflow menu.
            if (!isLoading && lyrics != null && !lyrics!!.isEmpty) {
                var showLyricsMenu by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (lyrics!!.isSynced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(if (lyrics!!.isSynced) R.string.lyrics_synced_badge else R.string.lyrics_plain_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lyrics!!.isSynced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        val sourceLabel = when (lyrics!!.source) {
                            "embedded" -> stringResource(R.string.lyrics_source_embedded)
                            "companion" -> stringResource(R.string.lyrics_source_companion)
                            "user_manual" -> stringResource(R.string.lyrics_source_manual)
                            "imported_file" -> stringResource(R.string.lyrics_source_imported)
                            "user_clipboard" -> stringResource(R.string.lyrics_source_clipboard)
                            "lrclib", "netease", "kugou", "lyrics_ovh" -> stringResource(R.string.lyrics_source_online, lyrics!!.source!!.uppercase())
                            else -> stringResource(R.string.lyrics_source_cached)
                        }

                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("lyrics_source_badge")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                trackForLyricsDeletion = currentTrack
                                showDeleteConfirmDialog = trackForLyricsDeletion != null
                            },
                            modifier = Modifier.size(44.dp).testTag("lyrics_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete_lyrics),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(
                            onClick = { viewModel.searchCandidatesOnline() },
                            modifier = Modifier.size(44.dp).testTag("lyrics_candidates_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                                contentDescription = stringResource(R.string.lyrics_search_candidates_btn),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showLyricsMenu = true },
                                modifier = Modifier.size(44.dp).testTag("lyrics_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.player_more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showLyricsMenu,
                                onDismissRequest = { showLyricsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_edit_action)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        viewModel.setManualEditOpen(true)
                                    },
                                    modifier = Modifier.testTag("lyrics_manual_edit_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cd_import_lrc)) },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        filePicker.launch(arrayOf("*/*", "text/*"))
                                    },
                                    modifier = Modifier.testTag("lyrics_import_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_export_lrc)) },
                                    leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        exportPicker.launch("${currentTrack?.title.orEmpty()}.lrc")
                                    },
                                    modifier = Modifier.testTag("lyrics_export_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_search_lyrics_google)) },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        onSearchGoogleLyrics()
                                    },
                                    modifier = Modifier.testTag("lyrics_google_search_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_offset_action)) },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        showOffsetDialog = true
                                    },
                                    modifier = Modifier.testTag("lyrics_offset_button")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fades the top and bottom edges of a scrolling area into the background so lines dissolve
 * instead of being cut off by a hard edge. Works on any background (no colour matching needed).
 */
private fun Modifier.fadingEdges(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        if (topPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = 0f,
                    endY = topPx
                ),
                size = Size(size.width, topPx),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - bottomPx,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - bottomPx),
                size = Size(size.width, bottomPx),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Synced lyrics, set like a poster: big, bold, start-aligned lines (so Persian hugs the right
 * edge and English the left, line by line, via content-based text direction). The active line is
 * full strength; lines already sung recede the most, upcoming lines stay readable. No bouncing or
 * scaling, so long Persian lines never jitter or clip; the motion is a calm scroll and a fade.
 */
@Composable
private fun SyncedLyricsContent(
    lyrics: Lyrics,
    activeIndex: Int,
    offsetMs: Long,
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    val lineStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = if (isLandscape) 22.sp else 26.sp,
        lineHeight = if (isLandscape) 34.sp else 42.sp,
        textDirection = TextDirection.Content
    )
    val onSurface = MaterialTheme.colorScheme.onSurface

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = if (isLandscape) 44.dp else 56.dp,
            bottom = if (isLandscape) 96.dp else 200.dp,
            start = 8.dp,
            end = 8.dp
        ),
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 40.dp, bottom = 72.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == activeIndex
            val targetAlpha = when {
                isCurrent -> 1f
                activeIndex < 0 -> 0.5f
                index < activeIndex -> if (activeIndex - index == 1) 0.34f else 0.24f
                index - activeIndex == 1 -> 0.62f
                index - activeIndex == 2 -> 0.5f
                else -> 0.4f
            }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                label = "lyric_alpha_$index"
            )

            val lineModifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSeekTo((line.timestampMs - offsetMs).coerceAtLeast(0L)) }
                .graphicsLayer { this.alpha = alpha }
                .padding(horizontal = 14.dp, vertical = if (isLandscape) 7.dp else 11.dp)
                .testTag("lyric_line_$index")
            if (isCurrent && line.words.isNotEmpty()) {
                KaraokeLine(
                    line = line,
                    offsetMs = offsetMs,
                    positionFlow = positionFlow,
                    style = lineStyle,
                    color = onSurface,
                    modifier = lineModifier
                )
            } else {
                Text(
                    text = line.text,
                    style = lineStyle,
                    color = onSurface,
                    textAlign = TextAlign.Start,
                    modifier = lineModifier
                )
            }
        }
    }
}

/** Word-by-word highlight for enhanced LRC. Only this line collects the playback position. */
@Composable
private fun KaraokeLine(
    line: com.yasinjahantigh.logarhythm.domain.model.LyricLine,
    offsetMs: Long,
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier
) {
    val position by positionFlow.collectAsStateWithLifecycle()
    val now = position + offsetMs
    val text = androidx.compose.ui.text.buildAnnotatedString {
        line.words.forEach { word ->
            val alpha = if (now >= word.startMs) 1f else 0.42f
            withStyle(androidx.compose.ui.text.SpanStyle(color = color.copy(alpha = alpha))) { append(word.text) }
        }
    }
    Text(text = text, style = style, textAlign = TextAlign.Start, modifier = modifier)
}

@Composable
private fun MismatchBanner(onHide: () -> Unit, onFindOthers: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("lyrics_mismatch_banner")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.lyrics_mismatch_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(onClick = onFindOthers) {
                    Text(stringResource(R.string.lyrics_mismatch_find_others))
                }
                androidx.compose.material3.TextButton(onClick = onHide) {
                    Text(stringResource(R.string.lyrics_mismatch_hide))
                }
            }
        }
    }
}

@Composable
private fun HiddenLyricsContent(onRestore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.lyrics_hidden_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lyrics_hidden_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.FilledTonalButton(
            onClick = onRestore,
            modifier = Modifier.testTag("lyrics_restore_button")
        ) { Text(stringResource(R.string.lyrics_restore)) }
    }
}

@Composable
private fun PlainLyricsContent(plainText: String) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 32.dp, bottom = 40.dp)
            .verticalScroll(rememberScrollState())
            .padding(
                top = if (isLandscape) 44.dp else 56.dp,
                bottom = if (isLandscape) 32.dp else 48.dp,
                start = 22.dp,
                end = 22.dp
            )
    ) {
        Text(
            text = plainText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp,
                lineHeight = 33.sp,
                textDirection = TextDirection.Content
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun LyricsEmptyContent(
    isOnlineEnabled: Boolean,
    onImportClick: () -> Unit,
    onFetchOnlineClick: () -> Unit,
    onSearchGoogleClick: () -> Unit,
    onPasteClipboardClick: () -> Unit,
    onManualEnterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.lyrics_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lyrics_empty_prompt_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Spacious vertical layout preventing cramped rows and text overlaps
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isOnlineEnabled) {
                FilledTonalButton(
                    onClick = onFetchOnlineClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("lyrics_fetch_online_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.lyrics_search_candidates_btn),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            OutlinedButton(
                onClick = onSearchGoogleClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lyrics_search_google_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_search_lyrics_google),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onPasteClipboardClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lyrics_paste_clipboard_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_paste_lyrics),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lyrics_empty_import_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_import_lrc),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            TextButton(
                onClick = onManualEnterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("lyrics_enter_manually_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lyrics_enter_manually_btn),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ManualLyricsDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSearchGoogle: () -> Unit,
    onPasteClipboard: () -> String?
) {
    var text by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.lyrics_manual_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.lyrics_manual_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = onPasteClipboard()
                            if (clip != null) text = clip
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_paste_lyrics), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onSearchGoogle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_search_lyrics_google), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.lyrics_manual_dialog_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp)
                        .testTag("manual_lyrics_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("manual_lyrics_save_btn")
            ) {
                Text(text = stringResource(R.string.save_lyrics))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun LyricsCandidatePickerDialog(
    initialTitle: String,
    initialArtist: String,
    candidates: List<LyricsCandidate>,
    isSearching: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String, String) -> Unit,
    onSelectCandidate: (LyricsCandidate) -> Unit,
    onResetToDefault: () -> Unit
) {
    var titleQuery by remember { mutableStateOf(initialTitle) }
    var artistQuery by remember { mutableStateOf(initialArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.lyrics_candidates_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Search inputs so user can adjust messy title/artist tags
                OutlinedTextField(
                    value = titleQuery,
                    onValueChange = { titleQuery = it },
                    label = { Text(stringResource(R.string.lyrics_custom_query_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_title_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = artistQuery,
                    onValueChange = { artistQuery = it },
                    label = { Text(stringResource(R.string.lyrics_custom_artist_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_artist_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onSearch(titleQuery, artistQuery) },
                    enabled = titleQuery.isNotBlank() && !isSearching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_search_btn")
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_loading))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_search_custom_btn))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    candidates.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.lyrics_candidates_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            candidates.forEach { candidate ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectCandidate(candidate) }
                                        .testTag("candidate_item_${candidate.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val candidateTitle = candidate.trackName.takeUnless {
                                            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("<unknown>", ignoreCase = true)
                                        } ?: stringResource(R.string.unknown_title)

                                        val cleanArtist = candidate.artistName.takeUnless {
                                            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("<unknown>", ignoreCase = true) || it.equals("unknown artist", ignoreCase = true)
                                        }
                                        val cleanAlbum = candidate.albumName.takeUnless {
                                            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("<unknown>", ignoreCase = true)
                                        }
                                        val subtitle = listOfNotNull(cleanArtist, cleanAlbum).joinToString(" • ").ifBlank {
                                            stringResource(R.string.unknown_artist)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = candidateTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (candidate.isSynced)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        if (candidate.isSynced) R.string.lyrics_synced_badge
                                                        else R.string.lyrics_plain_badge
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (candidate.isSynced)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = candidate.sourceName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            FilledTonalButton(
                                                onClick = { onSelectCandidate(candidate) },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.lyrics_search_select),
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onResetToDefault,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.lyrics_reset_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        }
    )
}
