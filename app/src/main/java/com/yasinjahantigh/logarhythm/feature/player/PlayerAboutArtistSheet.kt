package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.AudioFormatInfo
import com.yasinjahantigh.logarhythm.core.utils.AudioFormatReader
import com.yasinjahantigh.logarhythm.core.utils.FileUtils
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull
import java.io.File
import java.util.Locale

/**
 * Compact Spotify-inspired strip below PlayerQuickActions:
 * shows "Next: <track>" (tap opens Queue) and "About artist" (tap opens AboutArtistSheet).
 */
@Composable
fun PlayerBottomDiscoveryStrip(
    nextTrack: Track?,
    hasArtistInfo: Boolean,
    onOpenQueue: () -> Unit,
    onOpenAboutArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (nextTrack == null && !hasArtistInfo) return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (nextTrack != null) {
            val nextTitle = nextTrack.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
            Surface(
                onClick = onOpenQueue,
                shape = RoundedCornerShape(16.dp),
                color = scheme.surfaceContainerHigh.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("player_up_next_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkImage(
                        uri = nextTrack.artworkUri,
                        fallbackKey = nextTitle,
                        shape = RoundedCornerShape(8.dp),
                        glyphSize = 14.sp,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_up_next_label, nextTitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (hasArtistInfo) {
            Surface(
                onClick = onOpenAboutArtist,
                shape = RoundedCornerShape(16.dp),
                color = scheme.secondaryContainer.copy(alpha = 0.75f),
                modifier = Modifier
                    .then(if (nextTrack == null) Modifier.fillMaxWidth() else Modifier)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("player_about_artist_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.player_about_artist),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutArtistSheet(
    track: Track,
    artistInfo: PlayerArtistInfo?,
    onPlayTrack: (Track) -> Unit,
    onOpenArtistPage: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val formatInfo by produceState<AudioFormatInfo?>(initialValue = null, track.id, track.uri) {
        value = AudioFormatReader.read(context, track)
    }
    val fileSizeMb = remember(track.id, track.filePath, track.uri) {
        val path = track.filePath.takeIf { it.isNotBlank() } ?: FileUtils.resolveTrackPath(context, track)
        if (path != null) {
            val file = File(path)
            if (file.exists()) String.format(Locale.US, "%.2f MB", file.length().toDouble() / (1024.0 * 1024.0)) else null
        } else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("player_about_artist_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (artistInfo != null) {
                Text(
                    text = stringResource(R.string.player_about_artist_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ArtworkImage(
                                uri = artistInfo.photoUri,
                                fallbackKey = artistInfo.artistName,
                                shape = CircleShape,
                                glyphSize = 32.sp,
                                contentDescription = artistInfo.artistName,
                                modifier = Modifier.size(72.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artistInfo.artistName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.player_artist_library_tracks, artistInfo.libraryTrackCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = scheme.surfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = artistInfo.playCount.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.player_artist_your_plays),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = scheme.surfaceContainer,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = TimeUtils.formatListeningTimeSummary(artistInfo.totalListenTimeMs),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.player_artist_listen_time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (onOpenArtistPage != null) {
                            FilledTonalButton(
                                onClick = {
                                    onDismiss()
                                    onOpenArtistPage(artistInfo.artistName)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("about_artist_open_page_button")
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.player_artist_open_page))
                            }
                        }
                    }
                }

                if (artistInfo.topTracks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.player_artist_top_tracks, artistInfo.artistName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        artistInfo.topTracks.forEach { topTrack ->
                            val cleanTitle = topTrack.title.cleanMetaOrNull() ?: stringResource(R.string.unknown_title)
                            val cleanAlbum = topTrack.album.cleanMetaOrNull()
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (topTrack.id == track.id) scheme.primaryContainer.copy(alpha = 0.55f)
                                else scheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onPlayTrack(topTrack)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ArtworkImage(
                                        uri = topTrack.artworkUri,
                                        fallbackKey = cleanTitle,
                                        shape = RoundedCornerShape(10.dp),
                                        glyphSize = 18.sp,
                                        contentDescription = null,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cleanTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!cleanAlbum.isNullOrBlank()) {
                                            Text(
                                                text = cleanAlbum,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_play),
                                        tint = scheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Technical Audio Specs Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.player_audio_quality_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val info = formatInfo
                    if (info == null && fileSizeMb == null) {
                        Text(
                            text = stringResource(R.string.player_audio_quality_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                        info?.container?.let { SpecRow(stringResource(R.string.player_spec_format), it) }
                        info?.codec?.let { SpecRow(stringResource(R.string.player_spec_codec), it) }
                        val bitrateText = when {
                            info == null -> null
                            info.lossless -> stringResource(R.string.player_format_lossless)
                            info.bitrateKbps != null && info.bitrateKbps > 0 ->
                                (if (info.bitrateEstimated) "~" else "") + "${info.bitrateKbps} kbps"
                            else -> null
                        }
                        bitrateText?.let { SpecRow(stringResource(R.string.player_spec_bitrate), it) }
                        info?.sampleRateHz?.takeIf { it > 0 }?.let { hz ->
                            val srText = if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000.0)
                            SpecRow(stringResource(R.string.player_spec_sample_rate), srText)
                        }
                        fileSizeMb?.let { SpecRow(stringResource(R.string.player_spec_file_size), it) }
                        SpecRow(stringResource(R.string.detail_source), track.sourceType.name)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
