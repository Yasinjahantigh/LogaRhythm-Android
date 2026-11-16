package com.yasinjahantigh.logarhythm.feature.playlist

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.data.audio.AudioTrimmerEngine
import com.yasinjahantigh.logarhythm.data.waveform.WaveformStore
import com.yasinjahantigh.logarhythm.domain.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioTrimmerViewModel @Inject constructor(
    val trimmerEngine: AudioTrimmerEngine
) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimmerSheet(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: AudioTrimmerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs = track.durationMs.coerceAtLeast(10_000L)
    var range by remember(track.id) { mutableStateOf(0f..durationMs.toFloat()) }
    var waveform by remember(track.id) { mutableStateOf<FloatArray?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        waveform = WaveformStore.load(context, track.id)
    }

    val previewPlayer = remember(track.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
            prepare()
        }
    }

    DisposableEffect(track.id) {
        onDispose { previewPlayer.release() }
    }

    LaunchedEffect(previewing, range) {
        if (!previewing) {
            previewPlayer.pause()
            return@LaunchedEffect
        }
        val start = range.start.toLong()
        val end = range.endInclusive.toLong()
        previewPlayer.seekTo(start)
        previewPlayer.play()
        while (isActive && previewing) {
            if (previewPlayer.currentPosition >= end) {
                previewPlayer.seekTo(start)
            }
            delay(150L)
        }
    }

    val bars = remember(waveform) {
        waveform?.take(64)?.toList() ?: List(48) { idx ->
            0.25f + 0.6f * kotlin.math.abs(kotlin.math.sin(idx * 0.45f))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                text = stringResource(R.string.trimmer_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${track.title} · ${track.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))
            val activeColor = MaterialTheme.colorScheme.primary
            val mutedColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val count = bars.size
                val barW = size.width / (count * 1.4f)
                val gap = barW * 0.4f
                val startFrac = range.start / durationMs.toFloat()
                val endFrac = range.endInclusive / durationMs.toFloat()
                bars.forEachIndexed { idx, amp ->
                    val frac = idx.toFloat() / count
                    val inSel = frac in startFrac..endFrac
                    val barH = (amp.coerceIn(0.1f, 1f) * size.height)
                    val left = idx * (barW + gap)
                    drawRoundRect(
                        color = if (inSel) activeColor else mutedColor,
                        topLeft = Offset(left, (size.height - barH) / 2f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            RangeSlider(
                value = range,
                onValueChange = { next ->
                    if (next.endInclusive - next.start >= 1_500f) range = next
                },
                valueRange = 0f..durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(TimeUtils.formatDuration(range.start.toLong()), style = MaterialTheme.typography.labelMedium)
                Text(
                    TimeUtils.formatDuration((range.endInclusive - range.start).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(TimeUtils.formatDuration(range.endInclusive.toLong()), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(14.dp))
            FilledTonalButton(
                onClick = { previewing = !previewing },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (previewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (previewing) R.string.trimmer_stop_preview else R.string.trimmer_preview
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (saving) return@OutlinedButton
                        previewing = false
                        saving = true
                        scope.launch {
                            val res = viewModel.trimmerEngine.trimAndSave(
                                track = track,
                                startMs = range.start.toLong(),
                                endMs = range.endInclusive.toLong(),
                                asRingtone = false
                            )
                            saving = false
                            val msg = if (res.uri != null) {
                                context.getString(R.string.trimmer_saved_music)
                            } else {
                                res.error ?: context.getString(R.string.trimmer_failed)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (res.uri != null) onDismiss()
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.trimmer_save_clip))
                }

                Button(
                    onClick = {
                        if (saving) return@Button
                        previewing = false
                        saving = true
                        scope.launch {
                            val res = viewModel.trimmerEngine.trimAndSave(
                                track = track,
                                startMs = range.start.toLong(),
                                endMs = range.endInclusive.toLong(),
                                asRingtone = true
                            )
                            saving = false
                            if (res.uri != null) {
                                val msg = if (res.ringtoneAppliedDirectly) {
                                    context.getString(R.string.trimmer_ringtone_set)
                                } else {
                                    context.getString(R.string.trimmer_ringtone_saved_only)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (!res.ringtoneAppliedDirectly && !Settings.System.canWrite(context)) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        )
                                    }
                                }
                                onDismiss()
                            } else {
                                Toast.makeText(
                                    context,
                                    res.error ?: context.getString(R.string.trimmer_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.trimmer_save_ringtone))
                }
            }
        }
    }
}
