package com.yasinjahantigh.logarhythm.feature.lyrics

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.ShareCardGenerator
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.launch

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
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()

    val candidateLines = remember(lyrics) {
        if (lyrics.lines.isNotEmpty()) {
            lyrics.lines.map { it.text.trim() }.filter { it.isNotBlank() }
        } else {
            lyrics.plainText.orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    var selectedIndices by remember(candidateLines, initialFocusIndex) {
        val start = initialFocusIndex.coerceIn(0, (candidateLines.size - 1).coerceAtLeast(0))
        val end = (start + 1).coerceAtMost((candidateLines.size - 1).coerceAtLeast(0))
        mutableStateOf(
            if (candidateLines.isEmpty()) emptySet()
            else (start..end).toSet()
        )
    }
    var sharing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(candidateLines) { index, line ->
                    val checked = index in selectedIndices
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedIndices = if (checked) {
                                    selectedIndices - index
                                } else if (selectedIndices.size < 4) {
                                    selectedIndices + index
                                } else {
                                    selectedIndices
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                onClick = {
                    val chosen = selectedIndices.sorted().mapNotNull { candidateLines.getOrNull(it) }
                    if (chosen.isEmpty() || sharing) return@Button
                    sharing = true
                    scope.launch {
                        try {
                            val uri = ShareCardGenerator.createLyricCard(
                                context,
                                ShareCardGenerator.LyricCardData(
                                    songTitle = track.title,
                                    artist = track.artist,
                                    lines = chosen,
                                    artworkUri = track.artworkUri,
                                    accentColor = accentArgb
                                )
                            )
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
                enabled = selectedIndices.isNotEmpty() && !sharing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
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
