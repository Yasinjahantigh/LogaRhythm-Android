package com.yasinjahantigh.logarhythm.feature.download

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_NETWORK_RETRY_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_SAVED_WITHOUT_TAGS_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DOWNLOAD_TAG_WRITE_FAILED_DETAIL
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage

@Composable
fun DownloadHistoryTab(
    history: List<DownloadItem>,
    bottomPadding: Dp,
    onPlay: (DownloadItem) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onShare: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onEditTags: ((DownloadItem) -> Unit)? = null
) {
    if (history.isEmpty()) {
        EmptyHistory(bottomPadding = bottomPadding, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "history_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.download_history_count, history.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (history.any { it.isFinished }) {
                    TextButton(
                        onClick = onClearFinished,
                        modifier = Modifier.testTag("download_clear_finished_button")
                    ) {
                        Text(text = stringResource(R.string.download_history_clear))
                    }
                }
            }
        }
        items(history, key = { it.id }) { item ->
            DownloadHistoryItem(
                item = item,
                onPlay = onPlay,
                onOpen = onOpen,
                onShare = onShare,
                onCancel = onCancel,
                onRetry = onRetry,
                onRemove = onRemove,
                onEditTags = onEditTags
            )
        }
    }
}

@Composable
private fun DownloadHistoryItem(
    item: DownloadItem,
    onPlay: (DownloadItem) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onShare: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onEditTags: ((DownloadItem) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_history_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            ArtworkImage(
                uri = item.artworkUrl ?: item.contentUri,
                fallbackKey = item.title,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                contentDescription = stringResource(R.string.cd_artwork, item.title)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(item)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(item.createdAt).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.isActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (item.status == DownloadStatus.DOWNLOADING && item.progress > 0) {
                        LinearProgressIndicator(
                            progress = { item.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = downloadStatusLabel(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.status == DownloadStatus.FAILED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = downloadErrorText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.status == DownloadStatus.QUEUED &&
                    item.statusDetail == DOWNLOAD_NETWORK_RETRY_DETAIL
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.download_network_retrying),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.usedFallback) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.download_fallback_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (item.status == DownloadStatus.SAVED) {
                    val tagWarning = when (item.statusDetail) {
                        DOWNLOAD_SAVED_WITHOUT_TAGS_DETAIL -> R.string.download_saved_without_tags
                        DOWNLOAD_TAG_WRITE_FAILED_DETAIL -> R.string.download_tag_write_failed
                        else -> null
                    }
                    if (tagWarning != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(tagWarning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                if (item.isActive) {
                    IconButton(
                        onClick = { onCancel(item) },
                        modifier = Modifier.testTag("download_cancel_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.download_cancel)
                        )
                    }
                } else {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("download_menu_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.cd_download_more)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (item.status == DownloadStatus.SAVED) {
                            if (onEditTags != null && item.mediaStoreId != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_edit_tags)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = { menuExpanded = false; onEditTags(item) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_play)) },
                                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                                onClick = { menuExpanded = false; onPlay(item) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_open_with)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = { menuExpanded = false; onOpen(item) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_share)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = { menuExpanded = false; onShare(item) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_retry)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = { menuExpanded = false; onRetry(item) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_delete)) },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                            onClick = { menuExpanded = false; onRemove(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(item: DownloadItem) {
    val container = when (item.status) {
        DownloadStatus.SAVED -> MaterialTheme.colorScheme.primaryContainer
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (item.status) {
        DownloadStatus.SAVED -> MaterialTheme.colorScheme.onPrimaryContainer
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Text(
            text = downloadStatusLabel(item),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EmptyHistory(bottomPadding: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.download_history_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.download_history_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
