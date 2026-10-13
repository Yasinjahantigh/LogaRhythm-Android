package com.example.musicplayer.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.musicplayer.domain.discovery.Discovery
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.ui.theme.DisplayFontFamily

@Composable
fun greetingFor(daypart: Discovery.Daypart): String = stringResource(
    when (daypart) {
        Discovery.Daypart.MORNING -> R.string.home_greeting_morning
        Discovery.Daypart.AFTERNOON -> R.string.home_greeting_afternoon
        Discovery.Daypart.EVENING -> R.string.home_greeting_evening
        Discovery.Daypart.NIGHT -> R.string.home_greeting_night
    }
)

/**
 * The one card Home leads with: a queue for this hour, built from what the listener actually
 * plays at this time of day (and topped up with a mood match for a new listener).
 */
@Composable
fun RightNowCard(
    rightNow: Discovery.RightNow,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(
        when (rightNow.daypart) {
            Discovery.Daypart.MORNING -> R.string.home_right_now_morning
            Discovery.Daypart.AFTERNOON -> R.string.home_right_now_afternoon
            Discovery.Daypart.EVENING -> R.string.home_right_now_evening
            Discovery.Daypart.NIGHT -> R.string.home_right_now_night
        }
    )
    val subtitle = stringResource(
        if (rightNow.habitualCount >= 3) R.string.home_right_now_habit else R.string.home_right_now_mood
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("home_right_now_card"),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(start = 22.dp, top = 22.dp, bottom = 22.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_right_now_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("home_right_now_play")
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.home_right_now_play), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            ArtworkStack(tracks = rightNow.tracks.take(3))
        }
    }
}

/** Three overlapping covers: a glanceable preview of what the queue holds. */
@Composable
private fun ArtworkStack(tracks: List<Track>, modifier: Modifier = Modifier) {
    val size = 84.dp
    val step = 30.dp
    val count = tracks.size.coerceAtLeast(1)
    Box(modifier = modifier.width(size + step * (count - 1)).height(size)) {
        // Draw back-to-front so the first track sits on top.
        for (index in tracks.indices.reversed()) {
            val track = tracks[index]
            val shape = RoundedCornerShape(16.dp)
            val cover = Modifier
                .offset(x = step * index)
                .size(size)
                .clip(shape)
            if (!track.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    modifier = cover,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder)
                )
            } else {
                Surface(modifier = cover, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A horizontally scrolling shelf of track cards that plays the whole shelf from the tapped card. */
@Composable
fun TrackShelf(
    tracks: List<Track>,
    tagPrefix: String,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.testTag("${tagPrefix}_row")
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            RecentlyPlayedCard(
                track = track,
                onClick = { onPlay(tracks, index) },
                tagPrefix = tagPrefix
            )
        }
    }
}
