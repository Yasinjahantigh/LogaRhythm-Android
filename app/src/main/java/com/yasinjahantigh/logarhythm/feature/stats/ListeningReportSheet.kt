package com.yasinjahantigh.logarhythm.feature.stats

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.ShareCardGenerator
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningReportSheet(
    periodLabel: String,
    totalTimeMs: Long,
    playCount: Int,
    peakHour: Int?,
    topArtists: List<ArtistStat>,
    topTracks: List<TrackStat>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    var sharing by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val vibeRes = when (peakHour) {
        null -> null
        in 0..5 -> R.string.report_vibe_night_owl
        in 6..11 -> R.string.report_vibe_early_bird
        in 12..17 -> R.string.report_vibe_afternoon
        else -> R.string.report_vibe_evening
    }
    val peakHourLabel = peakHour?.let {
        stringResource(R.string.report_peak_hour, String.format(Locale.ROOT, "%02d:00", it))
    }
    val vibeLabel = vibeRes?.let { stringResource(it) }
    val heroArtUri = topTracks.firstOrNull()?.track?.artworkUri

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!heroArtUri.isNullOrBlank()) {
                    ArtworkImage(
                        uri = heroArtUri,
                        fallbackKey = periodLabel,
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column {
                    Text(
                        text = stringResource(R.string.stats_report_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = TimeUtils.formatListeningTimeSummary(totalTimeMs),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            stringResource(R.string.stats_plays, playCount),
                            peakHourLabel,
                            vibeLabel
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            if (topArtists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.stats_top_artists),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                topArtists.take(4).forEachIndexed { idx, stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${idx + 1}. ${stat.artist}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.stats_plays, stat.playCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (topTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.stats_top_tracks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                topTracks.take(4).forEachIndexed { idx, stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${idx + 1}. ${stat.track.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.stats_plays, stat.playCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (sharing) return@Button
                    sharing = true
                    val cardData = ShareCardGenerator.ReportData(
                        headline = context.getString(R.string.stats_report_title),
                        periodLabel = periodLabel,
                        totalTimeLabel = TimeUtils.formatListeningTimeSummary(totalTimeMs),
                        playsLabel = context.getString(R.string.stats_plays, playCount),
                        peakHourLabel = peakHourLabel,
                        vibeLabel = vibeLabel,
                        topArtists = topArtists.take(4).map {
                            it.artist to context.getString(R.string.stats_plays, it.playCount)
                        },
                        topTracks = topTracks.take(4).map {
                            it.track.title to "${it.track.artist} · ${context.getString(R.string.stats_plays, it.playCount)}"
                        },
                        artworkUri = heroArtUri,
                        accentColor = accentArgb
                    )
                    scope.launch {
                        try {
                            val uri = ShareCardGenerator.createListeningReportCard(context, cardData)
                            val bmp = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                                }
                            }
                            if (bmp != null) {
                                previewUri = uri
                                previewBitmap = bmp
                            } else {
                                ShareCardGenerator.launchShareIntent(
                                    context,
                                    uri,
                                    context.getString(R.string.stats_report_share_card)
                                )
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.share_card_failed), Toast.LENGTH_SHORT).show()
                        } finally {
                            sharing = false
                        }
                    }
                },
                enabled = !sharing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (sharing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.stats_report_share_card))
            }
        }
    }

    val activePreview = previewBitmap
    val activeUri = previewUri
    if (activePreview != null && activeUri != null) {
        Dialog(
            onDismissRequest = {
                previewBitmap = null
                previewUri = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.stats_report_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0C0D12)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = activePreview,
                            contentDescription = stringResource(R.string.stats_report_share_card),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(9f / 16f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                previewBitmap = null
                                previewUri = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.close))
                        }
                        Button(
                            onClick = {
                                ShareCardGenerator.launchShareIntent(
                                    context,
                                    activeUri,
                                    context.getString(R.string.stats_report_share_card)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.stats_report_share_card))
                        }
                    }
                }
            }
        }
    }
}
