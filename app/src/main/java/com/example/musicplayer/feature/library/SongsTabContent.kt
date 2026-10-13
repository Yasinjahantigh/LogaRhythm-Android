package com.example.musicplayer.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding

/**
 * All songs. The list opens with a Play / Shuffle bar (the two things people do first), then the
 * tracks. The fast scroller counts that bar as item 0 so its letter bubble stays in sync.
 */
@Composable
fun SongsTabContent(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onTrackMenuClick: ((Track) -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        LibraryEmptyHint(
            text = stringResource(R.string.library_empty_title),
            icon = Icons.Filled.MusicNote,
            modifier = modifier
        )
        return
    }

    val listState = rememberLazyListState()
    val bottomPadding = LocalPlaybackBottomPadding.current

    FastScroller(
        listState = listState,
        itemCount = tracks.size + 1,
        getItemLetter = { index ->
            tracks.getOrNull((index - 1).coerceAtLeast(0))?.title?.firstOrNull()?.uppercase() ?: ""
        },
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize().testTag("track_list")
        ) {
            item(key = "play_shuffle_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlayAll?.invoke() },
                        enabled = onPlayAll != null,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("library_play_all_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(
                            text = stringResource(R.string.btn_play),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    FilledTonalButton(
                        onClick = { onShuffleAll?.invoke() },
                        enabled = onShuffleAll != null,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("library_shuffle_all_button")
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.btn_shuffle),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
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
