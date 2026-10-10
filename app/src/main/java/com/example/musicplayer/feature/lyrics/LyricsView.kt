package com.example.musicplayer.feature.lyrics

import android.content.Intent
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
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
import com.example.musicplayer.domain.model.LyricsCandidate
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.domain.model.Lyrics
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isOnlineEnabled by viewModel.isOnlineEnabled.collectAsStateWithLifecycle()
    val isManualEditOpen by viewModel.isManualEditOpen.collectAsStateWithLifecycle()
    val showCandidatePicker by viewModel.showCandidatePicker.collectAsStateWithLifecycle()
    val lyricsCandidates by viewModel.lyricsCandidates.collectAsStateWithLifecycle()
    val isSearchingCandidates by viewModel.isSearchingCandidates.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importLyricsFromUri(uri)
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
            viewModel.pasteAndSaveLyrics(clipText)
            Toast.makeText(context, context.getString(R.string.lyrics_saved_to_file), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = stringResource(R.string.dialog_delete_lyrics_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_delete_lyrics_confirm,
                        currentTrack?.title.orEmpty()
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLyrics()
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, context.getString(R.string.lyrics_deleted), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
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
                        String.format("[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                    }
                } else it.plainText.orEmpty()
            }.orEmpty(),
            onDismiss = { viewModel.setManualEditOpen(false) },
            onSave = { text -> viewModel.saveManualLyrics(text) },
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
                viewModel.selectCandidate(candidate)
                Toast.makeText(context, context.getString(R.string.lyrics_applied_toast), Toast.LENGTH_SHORT).show()
            },
            onResetToDefault = {
                viewModel.resetLyricsToDefault()
                viewModel.dismissCandidatePicker()
                Toast.makeText(context, context.getString(R.string.lyrics_deleted), Toast.LENGTH_SHORT).show()
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                    SyncedLyricsContent(
                        lyrics = lyrics!!,
                        activeIndex = activeIndex,
                        onSeekTo = viewModel::seekTo
                    )
                }

                else -> {
                    PlainLyricsContent(plainText = lyrics?.plainText.orEmpty())
                }
            }

            // Quick actions in top-end corner when lyrics exist
            if (!isLoading && lyrics != null && !lyrics!!.isEmpty) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.searchCandidatesOnline() },
                        modifier = Modifier.testTag("lyrics_candidates_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageSearch,
                            contentDescription = stringResource(R.string.lyrics_search_candidates_btn),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onSearchGoogleLyrics,
                        modifier = Modifier.testTag("lyrics_google_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = stringResource(R.string.action_search_lyrics_google),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val defaultName = "${currentTrack?.title.orEmpty()}.lrc"
                            exportPicker.launch(defaultName)
                        },
                        modifier = Modifier.testTag("lyrics_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = stringResource(R.string.action_export_lrc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setManualEditOpen(true) },
                        modifier = Modifier.testTag("lyrics_manual_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.lyrics_edit_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*", "text/*")) },
                        modifier = Modifier.testTag("lyrics_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.cd_import_lrc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("lyrics_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete_lyrics),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricsContent(
    lyrics: Lyrics,
    activeIndex: Int,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 64.dp, bottom = 64.dp, start = 20.dp, end = 20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == activeIndex

            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.14f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "lyric_scale_$index"
            )

            val alpha by animateFloatAsState(
                targetValue = if (isCurrent) 1.0f else 0.45f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "lyric_alpha_$index"
            )

            val textColor by animateColorAsState(
                targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 350),
                label = "lyric_color_$index"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyric_line_$index")
                )
            }
        }
    }
}

@Composable
private fun PlainLyricsContent(plainText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = plainText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35f
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = candidate.trackName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
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
                                        val subtitle = listOfNotNull(
                                            candidate.artistName.ifBlank { null },
                                            candidate.albumName.ifBlank { null }
                                        ).joinToString(" • ")

                                        if (subtitle.isNotBlank()) {
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
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
