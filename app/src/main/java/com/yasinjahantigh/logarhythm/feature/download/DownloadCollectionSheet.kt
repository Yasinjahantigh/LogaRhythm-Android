package com.yasinjahantigh.logarhythm.feature.download

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadCollectionSheet(
    state: DownloadCollectionUiState,
    history: List<DownloadItem>,
    isArtistFollowed: Boolean = false,
    onToggleFollowArtist: () -> Unit = {},
    previewState: com.yasinjahantigh.logarhythm.data.download.StreamPreviewState =
        com.yasinjahantigh.logarhythm.data.download.StreamPreviewState(),
    onTogglePreview: ((DownloadSearchResult) -> Unit)? = null,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadAll: (List<DownloadSearchResult>) -> Unit,
    onTrackClick: (DownloadSearchResult) -> Unit,
    onAlbumClick: (DownloadSearchResult) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArtist = state.header.kind == DownloadResultKind.ARTIST

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .animateContentSize()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    uri = state.header.thumbnailUrl,
                    fallbackKey = state.header.title,
                    modifier = Modifier.size(64.dp),
                    shape = if (isArtist) CircleShape else RoundedCornerShape(16.dp),
                    contentDescription = state.header.title
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.header.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        if (state.header.artist.isNotBlank()) append(state.header.artist)
                        if (state.tracks.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.download_collection_tracks_count, state.tracks.size))
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.fromCache) {
                        Text(
                            text = stringResource(R.string.download_cached_offline_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isArtist) {
                    OutlinedButton(
                        onClick = onToggleFollowArtist,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isArtistFollowed) R.string.artist_following else R.string.artist_follow
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.download_refresh_collection)
                    )
                }
            }

            if (state.tracks.isNotEmpty()) {
                Button(
                    onClick = { onDownloadAll(state.tracks) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.download_all_tracks_action, state.tracks.size),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (state.loading && state.tracks.isEmpty() && state.albums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null && state.tracks.isEmpty() && state.albums.isEmpty()) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.albums.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.download_category_album),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(state.albums, key = { "col_alb_${it.id}" }) { alb ->
                                    Surface(
                                        modifier = Modifier
                                            .width(128.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onAlbumClick(alb) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            ArtworkImage(
                                                uri = alb.thumbnailUrl,
                                                fallbackKey = alb.title,
                                                icon = Icons.Filled.Album,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(104.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                contentDescription = alb.title
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = alb.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.tracks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.download_category_track),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                        items(state.tracks, key = { "col_trk_${it.source.raw}_${it.id}" }) { track ->
                            val activeItem = history.firstOrNull {
                                it.sourceUrl == track.url || (it.videoId == track.id && it.source == track.source)
                            }
                            val pKey = "${track.kind.name}_${track.source.raw}_${track.id}"
                            DownloadResultCard(
                                result = track,
                                activeItem = activeItem,
                                isPreviewLoading = previewState.trackKey == pKey && previewState.isLoading,
                                isPreviewPlaying = previewState.trackKey == pKey && previewState.isPlaying,
                                onTogglePreview = onTogglePreview?.let { cb -> { cb(track) } },
                                onClick = { onTrackClick(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}
