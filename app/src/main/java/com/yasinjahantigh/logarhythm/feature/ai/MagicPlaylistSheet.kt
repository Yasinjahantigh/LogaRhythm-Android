package com.yasinjahantigh.logarhythm.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicPlaylistSheet(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MagicPlaylistViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultPlaylistName = stringResource(R.string.magic_playlist_title)
    var playlistName by remember(state.result, defaultPlaylistName) {
        mutableStateOf(
            state.result?.name?.takeIf { it.isNotBlank() } ?: defaultPlaylistName
        )
    }

    fun close() {
        viewModel.reset()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(R.string.magic_playlist_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.magic_playlist_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!settings.isConfigured) {
                Text(
                    text = stringResource(R.string.magic_playlist_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.magic_playlist_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.reset()
                        onOpenSettings()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("magic_playlist_setup_button")
                ) {
                    Text(stringResource(R.string.magic_playlist_open_settings))
                }
                return@Column
            }

            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text(stringResource(R.string.magic_playlist_prompt_label)) },
                placeholder = { Text(stringResource(R.string.magic_playlist_prompt_hint)) },
                minLines = 3,
                maxLines = 5,
                enabled = !state.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("magic_playlist_prompt")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.magic_playlist_count, state.trackCount),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Slider(
                value = state.trackCount.toFloat(),
                onValueChange = { viewModel.setTrackCount(it.roundToInt()) },
                valueRange = MagicPlaylistViewModel.MIN_TRACKS.toFloat()..
                    MagicPlaylistViewModel.MAX_TRACKS.toFloat(),
                steps = MagicPlaylistViewModel.MAX_TRACKS - MagicPlaylistViewModel.MIN_TRACKS - 1,
                enabled = !state.isGenerating
            )

            Text(
                text = stringResource(R.string.magic_playlist_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = viewModel::generate,
                enabled = state.prompt.isNotBlank() && !state.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("magic_playlist_generate")
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(stringResource(R.string.magic_playlist_generating))
                } else {
                    Text(stringResource(R.string.magic_playlist_generate))
                }
            }

            state.failure?.let { failure ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(failure.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("magic_playlist_error")
                )
            }

            state.result?.let { result ->
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.magic_playlist_preview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.magic_playlist_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("magic_playlist_name")
                )
                if (result.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                result.tracks.take(PREVIEW_LIMIT).forEach { track ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (result.tracks.size > PREVIEW_LIMIT) {
                    Text(
                        text = stringResource(
                            R.string.magic_playlist_more_tracks,
                            result.tracks.size - PREVIEW_LIMIT
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.save(playlistName) { close() } },
                    enabled = playlistName.isNotBlank() && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("magic_playlist_save")
                ) {
                    Text(stringResource(R.string.magic_playlist_save))
                }
                TextButton(
                    onClick = viewModel::generate,
                    enabled = !state.isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.magic_playlist_regenerate))
                }
            }
        }
    }
}

private const val PREVIEW_LIMIT = 10
