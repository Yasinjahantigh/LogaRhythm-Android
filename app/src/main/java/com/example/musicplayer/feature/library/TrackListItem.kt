package com.example.musicplayer.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.R
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.player.CuteEqualizerVisualizer
import com.example.musicplayer.ui.components.ArtworkImage
import com.example.musicplayer.ui.components.cleanMetaOrNull
import com.example.musicplayer.ui.theme.MusicPlayerTheme

/**
 * The one track row used across the whole app.
 *
 * - Artwork always has something to show: real cover, or a gradient + monogram generated from the
 *   title, so lists stay colourful even for untagged files.
 * - The track that is playing is unmistakable: tinted row, accent-coloured title, and live
 *   equaliser bars over its cover that stop when playback pauses.
 * - Subtitle is "artist · album" (album only when known); duration sits next to the menu so it is
 *   never pushed out by a long name.
 */
@Composable
fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    onMenuClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    // Plain colour switch: hundreds of rows must not each own an animation object.
    val container = if (isCurrentTrack) scheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent

    val title = track.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
    val artist = track.artist.cleanMetaOrNull() ?: stringResource(R.string.unknown_artist)
    val album = track.album.cleanMetaOrNull()
    val subtitle = if (album != null) "$artist · $album" else artist

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .defaultMinSize(minHeight = 64.dp)
            .testTag("track_item_${track.id}")
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = if (onMenuClick != null) 0.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                    CuteEqualizerVisualizer(
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
                color = if (isCurrentTrack) scheme.primary else scheme.onSurface,
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

        if (onMenuClick != null) {
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

@Preview(showBackground = true, name = "Normal Track Item")
@Composable
private fun TrackListItemPreview() {
    MusicPlayerTheme {
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
    MusicPlayerTheme(darkTheme = true) {
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
