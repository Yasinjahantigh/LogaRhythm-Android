package com.example.musicplayer.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.musicplayer.domain.model.Track

@Composable
fun SongsTabContent(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onTrackMenuClick: ((Track) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val listState = rememberLazyListState()

        FastScroller(
            listState = listState,
            itemCount = tracks.size,
            getItemLetter = { index ->
                tracks.getOrNull(index)?.title?.firstOrNull()?.uppercase() ?: ""
            },
            modifier = modifier
        ) {
            val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
            LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
                modifier = Modifier.fillMaxSize().testTag("track_list")
            ) {
                items(
                    items = tracks,
                    key = { it.id }
                ) { track ->
                    val isCurrent = track.id == currentTrackId
                    TrackListItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onMenuClick = onTrackMenuClick?.let { { it(track) } }
                    )
                }
            }
        }
    }
}
