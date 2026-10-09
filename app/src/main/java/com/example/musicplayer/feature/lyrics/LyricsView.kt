package com.example.musicplayer.feature.lyrics

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.musicplayer.domain.model.Lyrics
import java.net.URLEncoder

@Composable
fun LyricsView(
    viewModel: LyricsViewModel,
    modifier: Modifier = Modifier
) {
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeLineIndex.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isOnlineEnabled by viewModel.isOnlineEnabled.collectAsStateWithLifecycle()
    val isManualEditOpen by viewModel.isManualEditOpen.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

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

    Surface(
        modifier = modifier
            .fillMaxSize()
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
                        onFetchOnlineClick = viewModel::fetchOnlineLyrics,
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
        contentPadding = PaddingValues(top = 56.dp, bottom = 48.dp, start = 20.dp, end = 20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isCurrent = index == activeIndex
            Text(
                text = line.text,
                style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = 10.dp)
                    .testTag("lyric_line_$index")
            )
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.lyrics_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lyrics_empty_prompt_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Search Online & Google Search
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOnlineEnabled) {
                    FilledTonalButton(
                        onClick = onFetchOnlineClick,
                        modifier = Modifier.testTag("lyrics_fetch_online_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = stringResource(R.string.btn_fetch_online))
                    }
                }

                OutlinedButton(
                    onClick = onSearchGoogleClick,
                    modifier = Modifier.testTag("lyrics_search_google_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = stringResource(R.string.action_search_lyrics_google))
                }
            }

            // Row 2: Paste from clipboard & Import LRC
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPasteClipboardClick,
                    modifier = Modifier.testTag("lyrics_paste_clipboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = stringResource(R.string.action_paste_lyrics))
                }

                FilledTonalButton(
                    onClick = onImportClick,
                    modifier = Modifier.testTag("lyrics_empty_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = stringResource(R.string.btn_import_lrc))
                }
            }

            // Row 3: Manual Entry
            Button(
                onClick = onManualEnterClick,
                modifier = Modifier.testTag("lyrics_enter_manually_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = stringResource(R.string.lyrics_enter_manually_btn))
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
