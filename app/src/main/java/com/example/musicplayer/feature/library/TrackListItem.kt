package com.example.musicplayer.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.domain.model.SourceType
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.ui.designsystem.AppDimensions
import com.example.musicplayer.ui.designsystem.AppShapes
import com.example.musicplayer.ui.designsystem.AppSpacing
import com.example.musicplayer.ui.theme.MusicPlayerTheme

/**
 * Standard unified track row component across the entire app.
 * Guarantees fixed artwork size, consistent text offset, duration visibility,
 * and localized fallbacks for unknown metadata.
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
    val containerBg = if (isCurrentTrack) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        Color.Transparent
    }

    val displayTitle = when {
        track.title.isBlank() ||
            track.title.equals("<unknown>", ignoreCase = true) ||
            track.title.equals("unknown", ignoreCase = true) ||
            track.title.equals("null", ignoreCase = true) -> stringResource(R.string.unknown_title)
        else -> track.title
    }

    val displayArtist = when {
        track.artist.isBlank() ||
            track.artist.equals("<unknown>", ignoreCase = true) ||
            track.artist.equals("unknown", ignoreCase = true) ||
            track.artist.equals("null", ignoreCase = true) -> stringResource(R.string.unknown_artist)
        else -> track.artist
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimensions.minTouchTarget)
            .background(containerBg)
            .testTag("track_item_${track.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.normal, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed Artwork Thumbnail
        Box(
            modifier = Modifier
                .size(AppDimensions.artworkRow)
                .clip(AppShapes.roundedSm)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.cd_artwork, displayTitle),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder)
            )

            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing),
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(AppDimensions.iconMd)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(AppSpacing.normal))

        // Text details with duration guaranteed to be fully visible
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = TimeUtils.formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (onMenuClick != null) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(AppDimensions.minTouchTarget)
                    .testTag("track_menu_button_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_song_menu, displayTitle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppDimensions.iconMd)
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
                artist = "Queen (A Night at the Opera Album Edition With Very Long Name)",
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
