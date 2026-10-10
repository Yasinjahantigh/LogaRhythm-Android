package com.example.musicplayer.feature.stats

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.musicplayer.core.utils.TimeUtils
import com.example.musicplayer.domain.model.ArtistStat

@Composable
fun StatsArtistsTab(
    artists: List<ArtistStat>,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        StatsEmptyTabContent(
            title = stringResource(R.string.stats_empty_title),
            message = stringResource(R.string.stats_empty_desc),
            modifier = modifier
        )
        return
    }

    val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("stats_artists_list"),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp)
    ) {
        itemsIndexed(artists, key = { _, item -> item.artist }) { index, stat ->
            ArtistStatRow(
                rank = index + 1,
                stat = stat,
                onClick = { onArtistClick(stat.artist) }
            )
        }
    }
}

@Composable
private fun ArtistStatRow(
    rank: Int,
    stat: ArtistStat,
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
            .testTag("artist_stat_row_$rank"),
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

        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayArtist,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val playsStr = stringResource(R.string.stats_play_count, stat.playCount)
            val tracksStr = stringResource(R.string.album_track_count, stat.trackCount)
            val timeStr = TimeUtils.formatListeningTimeSummary(stat.totalListenTimeMs)
            Text(
                text = "$tracksStr • $playsStr • $timeStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
