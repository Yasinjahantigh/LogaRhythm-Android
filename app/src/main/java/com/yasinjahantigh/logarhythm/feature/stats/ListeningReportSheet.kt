package com.yasinjahantigh.logarhythm.feature.stats

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.ShareCardGenerator
import com.yasinjahantigh.logarhythm.core.utils.ShareCardOptions
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.StatsSortBy
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.ui.components.ShareCardCustomizePanel
import com.yasinjahantigh.logarhythm.ui.components.ShareCardLivePreviewBox
import kotlinx.coroutines.launch
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
    onDismiss: () -> Unit,
    sortBy: StatsSortBy = StatsSortBy.PLAY_COUNT
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultAccent = MaterialTheme.colorScheme.primary.toArgb()

    val (defaultFontKey, defaultCustomPath) = com.yasinjahantigh.logarhythm.ui.components.rememberShareDefaultFont()
    var userPickedFont by remember { mutableStateOf(false) }
    var options by remember {
        mutableStateOf(
            ShareCardOptions(
                accentColor = defaultAccent,
                fontKey = defaultFontKey,
                customFontPath = defaultCustomPath
            )
        )
    }
    LaunchedEffect(defaultFontKey, defaultCustomPath) {
        if (!userPickedFont) {
            options = options.copy(fontKey = defaultFontKey, customFontPath = defaultCustomPath)
        }
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

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

    val headline = stringResource(R.string.stats_report_title)
    val playsText = stringResource(R.string.stats_plays, playCount)
    val cardData = remember(
        headline, periodLabel, totalTimeMs, playsText, peakHourLabel, vibeLabel,
        topArtists, topTracks, heroArtUri, options, sortBy
    ) {
        ShareCardGenerator.ReportData(
            headline = headline,
            periodLabel = periodLabel,
            totalTimeLabel = TimeUtils.formatListeningTimeSummary(totalTimeMs),
            playsLabel = playsText,
            peakHourLabel = peakHourLabel,
            vibeLabel = vibeLabel,
            topArtists = topArtists.take(4).map {
                val metric = if (sortBy == StatsSortBy.LISTEN_TIME) {
                    TimeUtils.formatListeningTimeSummary(it.totalListenTimeMs)
                } else {
                    context.getString(R.string.stats_plays, it.playCount)
                }
                it.artist to metric
            },
            topTracks = topTracks.take(4).map {
                val metric = if (sortBy == StatsSortBy.LISTEN_TIME) {
                    TimeUtils.formatListeningTimeSummary(it.totalListenTimeMs)
                } else {
                    context.getString(R.string.stats_plays, it.playCount)
                }
                it.track.title to "${it.track.artist} · $metric"
            },
            artworkUri = heroArtUri,
            accentColor = options.accentColor,
            options = options
        )
    }

    LaunchedEffect(cardData) {
        rendering = true
        previewBitmap = runCatching {
            ShareCardGenerator.renderListeningReportPreview(context, cardData)
        }.getOrNull()
        rendering = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("listening_report_studio_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Live graphic card preview right inside the sheet!
            ShareCardLivePreviewBox(
                previewBitmap = previewBitmap,
                aspect = options.aspect,
                isRendering = rendering
            )

            // Full customization: Theme, Aspect Ratio (9:16 / 4:5 / 1:1), Accent Color, Font, Text Size
            ShareCardCustomizePanel(
                options = options,
                onOptionsChange = { next ->
                    if (next.fontKey != options.fontKey || next.customFontPath != options.customFontPath) {
                        userPickedFont = true
                    }
                    options = next
                },
                defaultAccentColor = defaultAccent,
                customFontPath = defaultCustomPath
            )

            // Compact summary card underneath for quick scanning
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${TimeUtils.formatListeningTimeSummary(totalTimeMs)}  ·  $playsText",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (topArtists.isNotEmpty()) {
                        Text(
                            text = topArtists.take(3).joinToString(" · ") { it.artist },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (sharing) return@Button
                    sharing = true
                    scope.launch {
                        try {
                            val uri = ShareCardGenerator.createListeningReportCard(context, cardData)
                            ShareCardGenerator.launchShareIntent(
                                context,
                                uri,
                                context.getString(R.string.stats_report_share_card)
                            )
                            onDismiss()
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.share_card_failed), Toast.LENGTH_SHORT).show()
                        } finally {
                            sharing = false
                        }
                    }
                },
                enabled = !sharing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("listening_report_share_button")
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
}
