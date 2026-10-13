package com.example.musicplayer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelectKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectKey(key)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (key == selectedKey),
                            onClick = {
                                onSelectKey(key)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun ExcludedFoldersDialog(
    allFolders: List<String>,
    excludedFolders: Set<String>,
    onToggleFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_excluded_folders_dialog_title)) },
        text = {
            if (allFolders.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_folders_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(allFolders, key = { it }) { folder ->
                        val isExcluded = excludedFolders.contains(folder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleFolder(folder) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isExcluded,
                                onCheckedChange = { onToggleFolder(folder) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = folder, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@androidx.compose.runtime.Composable
fun SeparatorsDialog(
    current: List<String>,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.example.musicplayer.domain.library.ArtistSplitter.serializeSeparators(current))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_separators_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    androidx.compose.ui.res.stringResource(com.example.R.string.settings_separators_hint),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    maxLines = 8,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(text); onDismiss() }) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_save))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { onReset(); onDismiss() }) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_reset_default))
            }
        }
    )
}

@androidx.compose.runtime.Composable
fun HiddenLyricsDialog(
    tracks: List<com.example.musicplayer.domain.model.Track>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_hidden_lyrics_title)) },
        text = {
            if (tracks.isEmpty()) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_hidden_lyrics_empty))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = androidx.compose.ui.Modifier.heightIn(max = 360.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                androidx.compose.material3.Text(track.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                androidx.compose.material3.Text(
                                    track.artist,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.TextButton(onClick = { onRestore(track.id) }) {
                                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.lyrics_restore))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_close))
            }
        }
    )
}
