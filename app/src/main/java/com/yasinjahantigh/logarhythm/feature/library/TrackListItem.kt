package com.yasinjahantigh.logarhythm.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.player.EqualizerVisualizer
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.SwipeToPlayNext
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull
import com.yasinjahantigh.logarhythm.ui.theme.LogaRhythmTheme

/**
 * The track row used across the app. Untagged songs get a generated gradient cover, and the
 * playing track is marked with a tinted row and live equalizer bars.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    onMenuClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    // Plain colour switch: hundreds of rows must not each own an animation object.
    val container = when {
        isSelected -> scheme.primaryContainer.copy(alpha = 0.55f)
        isCurrentTrack -> scheme.primaryContainer.copy(alpha = 0.30f)
        else -> Color.Transparent
    }

    val title = track.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track.artist.cleanMetaOrNull()
        ?: track.albumArtist.cleanMetaOrNull()
        ?: stringResource(R.string.unknown_artist)
    val album = track.album.cleanMetaOrNull()
    val subtitle = if (album != null) "$artist · $album" else artist

    SwipeToPlayNext(
        onPlayNext = onPlayNext ?: {},
        enabled = onPlayNext != null && !selectionMode,
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(container)
                .defaultMinSize(minHeight = 64.dp)
                .testTag("track_item_${track.id}")
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(
                    start = 8.dp,
                    end = if (onMenuClick != null && !selectionMode) 0.dp else 12.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) scheme.primary else scheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                ArtworkImage(
                    uri = track.artworkUri,
                    fallbackKey = title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    contentDescription = stringResource(R.string.cd_artwork, title)
                )
                if (isCurrentTrack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerVisualizer(
                            isPlaying = isPlaying,
                            barColor = Color.White,
                            maxHeight = 20.dp,
                            barCount = 4
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentTrack || isSelected) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = TimeUtils.formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )

            if (onMenuClick != null && !selectionMode) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("track_menu_button_${track.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_song_menu, title),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Normal Track Item")
@Composable
private fun TrackListItemPreview() {
    LogaRhythmTheme {
        TrackListItem(
            track = Track(
                id = "local:1",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                durationMs = 354_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "uri"
            ),
            onClick = {},
            isCurrentTrack = false,
            onMenuClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Now Playing Track Item")
@Composable
private fun TrackListItemActivePreview() {
    LogaRhythmTheme(darkTheme = true) {
        TrackListItem(
            track = Track(
                id = "local:2",
                title = "Starboy",
                artist = "The Weeknd feat. Daft Punk",
                album = "Starboy",
                durationMs = 230_000L,
                artworkUri = null,
                sourceType = SourceType.LOCAL,
                uri = "uri"
            ),
            onClick = {},
            isCurrentTrack = true,
            isPlaying = true,
            onMenuClick = {}
        )
    }
}
