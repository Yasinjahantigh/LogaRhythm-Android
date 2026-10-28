package com.yasinjahantigh.logarhythm.feature.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.AlbumStat

@Composable
fun StatsAlbumsTab(
    albums: List<AlbumStat>,
    onAlbumClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        StatsEmptyTabContent(
            title = stringResource(R.string.stats_empty_title),
            message = stringResource(R.string.stats_empty_desc),
            modifier = modifier
        )
        return
    }

    val bottomPadding = com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding.current
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("stats_albums_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp)
    ) {
        itemsIndexed(albums, key = { _, item -> "${item.album}_${item.artist}" }) { index, stat ->
            AlbumStatRow(
                rank = index + 1,
                stat = stat,
                onClick = { onAlbumClick(stat.album, stat.artist) }
            )
        }
    }
}

@Composable
private fun AlbumStatRow(
    rank: Int,
    stat: AlbumStat,
    onClick: () -> Unit
) {
    val rankContainerColor = when (rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rankContentColor = when (rank) {
        1 -> MaterialTheme.colorScheme.onPrimaryContainer
        2 -> MaterialTheme.colorScheme.onSecondaryContainer
        3 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val displayAlbum = if (stat.album.isBlank() ||
        stat.album.equals("<unknown>", ignoreCase = true) ||
        stat.album.equals("unknown", ignoreCase = true) ||
        stat.album.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_album)
    } else stat.album

    val displayArtist = if (stat.artist.isBlank() ||
        stat.artist.equals("<unknown>", ignoreCase = true) ||
        stat.artist.equals("unknown", ignoreCase = true) ||
        stat.artist.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else stat.artist

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("album_stat_row_$rank"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = rankContainerColor,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = rankContentColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (stat.artworkUri != null) {
            AsyncImage(
                model = stat.artworkUri,
                contentDescription = stringResource(R.string.cd_artwork, displayAlbum),
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayAlbum,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val playsStr = stringResource(R.string.stats_play_count, stat.playCount)
            val timeStr = TimeUtils.formatListeningTimeSummary(stat.totalListenTimeMs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " • $playsStr • $timeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
