package com.example.musicplayer.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.musicplayer.domain.model.DailyMix
import com.example.musicplayer.domain.model.Track

@Composable
fun DailyMixCard(
    mix: DailyMix,
    onPlayMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleRes = when (mix.id) {
        "daily_mix_1" -> R.string.daily_mix_title_rotation
        "daily_mix_2" -> R.string.daily_mix_title_rediscover
        "daily_mix_3" -> R.string.daily_mix_title_discovery
        else -> null
    }
    val descriptionFallbacks = mapOf(
        "Familiar tracks and favorites" to R.string.daily_mix_desc_rotation,
        "Music worth hearing again" to R.string.daily_mix_desc_rediscover,
        "Less-played corners of your library" to R.string.daily_mix_desc_discovery
    )
    val displayTitle = if (titleRes != null) stringResource(titleRes) else mix.title
    val descriptionRes = descriptionFallbacks[mix.description]
    val displayDescription = if (descriptionRes != null) stringResource(descriptionRes) else mix.description

    Card(
        modifier = modifier
            .width(236.dp)
            .clickable(onClick = onPlayMix)
            .testTag("daily_mix_card_${mix.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MixArtworkCollage(tracks = mix.tracks.take(4), modifier = Modifier.size(68.dp))
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onPlayMix,
                        modifier = Modifier.testTag("play_mix_btn_${mix.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.daily_mix_play, displayTitle),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.library_track_count, mix.tracks.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MixArtworkCollage(tracks: List<Track>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(2) { column ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(2) { row ->
                    val index = column * 2 + row
                    val track = tracks.getOrNull(index)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (!track?.artworkUri.isNullOrBlank()) {
                            AsyncImage(
                                model = track?.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = painterResource(R.drawable.ic_music_placeholder),
                                error = painterResource(R.drawable.ic_music_placeholder)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = if ((column + row) % 2 == 0)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (index == 0) Icons.Filled.AutoAwesome else Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
