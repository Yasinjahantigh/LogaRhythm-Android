package com.yasinjahantigh.logarhythm.feature.lyrics

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.ShareCardGenerator
import com.yasinjahantigh.logarhythm.core.utils.ShareCardOptions
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.ui.components.ShareCardCustomizePanel
import com.yasinjahantigh.logarhythm.ui.components.ShareCardLivePreviewBox
import kotlinx.coroutines.launch

/**
 * Lyric quote card studio:
 * - Snapshots initialFocusIndex ONCE when opened so live song playback NEVER resets the user's selected lines.
 * - Step 1: Pick up to 5 lyric lines.
 * - Step 2: Live customizable preview (theme, color, aspect ratio 9:16 / 4:5 / 1:1, text size) & share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricCardShareDialog(
    track: Track,
    lyrics: Lyrics,
    initialFocusIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultAccent = MaterialTheme.colorScheme.primary.toArgb()

    // Snapshot track, lines, and focus index once when dialog opens so ongoing song playback
    // NEVER changes or clears the lines the user is selecting.
    val snapshotTrack = remember { track }
    val candidateLines = remember {
        if (lyrics.lines.isNotEmpty()) {
            lyrics.lines.map { it.text.trim() }.filter { it.isNotBlank() }
        } else {
            lyrics.plainText.orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
        }
    }
    val initialSnapshotIndex = remember {
        initialFocusIndex.coerceIn(0, (candidateLines.size - 1).coerceAtLeast(0))
    }

    var selectedIndices by remember {
        val start = initialSnapshotIndex
        val end = (start + 1).coerceAtMost((candidateLines.size - 1).coerceAtLeast(0))
        mutableStateOf(
            if (candidateLines.isEmpty()) emptySet()
            else (start..end).toSet()
        )
    }

    // 0 = Pick lines, 1 = Live Customize & Preview Studio
    var step by remember { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(ShareCardOptions(accentColor = defaultAccent)) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val chosenLines = remember(selectedIndices, candidateLines) {
        selectedIndices.sorted().mapNotNull { candidateLines.getOrNull(it) }
    }

    val cardData = remember(snapshotTrack, chosenLines, options) {
        ShareCardGenerator.LyricCardData(
            songTitle = snapshotTrack.title,
            artist = snapshotTrack.artist,
            lines = chosenLines,
            artworkUri = snapshotTrack.artworkUri,
            accentColor = options.accentColor,
            options = options
        )
    }

    LaunchedEffect(step, cardData) {
        if (step == 1 && chosenLines.isNotEmpty()) {
            rendering = true
            previewBitmap = runCatching {
                ShareCardGenerator.renderLyricCardPreview(context, cardData)
            }.getOrNull()
            rendering = false
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialSnapshotIndex - 1).coerceAtLeast(0)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        AnimatedContent(
            targetState = step,
            label = "lyric_share_step"
        ) { currentStep ->
            if (currentStep == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .testTag("lyric_share_picker_step")
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_share_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.lyrics_share_card_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(candidateLines) { index, line ->
                            val checked = index in selectedIndices
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedIndices = if (checked) {
                                            selectedIndices - index
                                        } else if (selectedIndices.size < 5) {
                                            selectedIndices + index
                                        } else {
                                            selectedIndices
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { step = 1 },
                        enabled = selectedIndices.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("lyric_share_preview_next_button")
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.share_card_preview_customize))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                        .testTag("lyric_share_studio_step"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { step = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                        Text(
                            text = stringResource(R.string.lyrics_share_card_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    ShareCardLivePreviewBox(
                        previewBitmap = previewBitmap,
                        aspect = options.aspect,
                        isRendering = rendering
                    )

                    ShareCardCustomizePanel(
                        options = options,
                        onOptionsChange = { options = it },
                        defaultAccentColor = defaultAccent
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { step = 0 },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(0.85f)
                        ) {
                            Text(stringResource(R.string.share_card_edit_lines))
                        }
                        Button(
                            onClick = {
                                if (chosenLines.isEmpty() || sharing) return@Button
                                sharing = true
                                scope.launch {
                                    try {
                                        val uri = ShareCardGenerator.createLyricCard(context, cardData)
                                        ShareCardGenerator.launchShareIntent(
                                            context,
                                            uri,
                                            context.getString(R.string.lyrics_share_card_title)
                                        )
                                        onDismiss()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, context.getString(R.string.share_card_failed), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        sharing = false
                                    }
                                }
                            },
                            enabled = !sharing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.15f).testTag("lyric_share_confirm_button")
                        ) {
                            if (sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.lyrics_share_card_action))
                        }
                    }
                }
            }
        }
    }
}
