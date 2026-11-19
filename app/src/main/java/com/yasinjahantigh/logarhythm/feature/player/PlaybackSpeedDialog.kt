package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun roundedPlaybackSpeed(speed: Float): Float =
    (speed.coerceIn(0.5f, 2f) * 20f).roundToInt() / 20f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    currentPitch: Float = 1.0f,
    onPitchSelected: (Float) -> Unit = {},
    slowedReverbEnabled: Boolean = false,
    onToggleSlowedReverb: (Boolean) -> Unit = {},
    onReset: (() -> Unit)? = null
) {
    val presetSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    // Hold smooth local slider state while dragging so ExoPlayer SonicAudioProcessor is never
    // flooded with 60 IPC calls/sec during drag, applying cleanly on release or preset tap.
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(roundedPlaybackSpeed(currentSpeed)) }
    var sliderPitch by remember(currentPitch) { mutableFloatStateOf(roundedPlaybackSpeed(currentPitch)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("playback_speed_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.playback_speed_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.speed_multiplier, String.format(Locale.US, "%.2f", sliderSpeed)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Slider(
                    value = sliderSpeed,
                    onValueChange = { sliderSpeed = roundedPlaybackSpeed(it) },
                    onValueChangeFinished = { onSpeedSelected(roundedPlaybackSpeed(sliderSpeed)) },
                    valueRange = 0.5f..2.0f,
                    steps = 29,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playback_speed_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.speed_multiplier, "0.5"), style = MaterialTheme.typography.labelSmall)
                    Text(text = stringResource(R.string.speed_multiplier, "2.0"), style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetSpeeds.forEach { speed ->
                        val isSelected = abs(sliderSpeed - speed) < 0.01f
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                sliderSpeed = speed
                                onSpeedSelected(speed)
                            },
                            label = { Text(text = stringResource(R.string.speed_multiplier, speed.toString())) },
                            modifier = Modifier.testTag("speed_chip_$speed")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Pitch control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.playback_pitch_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.speed_multiplier, String.format(Locale.US, "%.2f", sliderPitch)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = sliderPitch,
                    onValueChange = { sliderPitch = roundedPlaybackSpeed(it) },
                    onValueChangeFinished = { onPitchSelected(roundedPlaybackSpeed(sliderPitch)) },
                    valueRange = 0.5f..2.0f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth()
                )

                // Slowed + Reverb 1-tap toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.playback_slowed_reverb),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = slowedReverbEnabled,
                        onCheckedChange = { enabled ->
                            sliderSpeed = if (enabled) 0.85f else 1.0f
                            sliderPitch = if (enabled) 0.88f else 1.0f
                            onToggleSlowedReverb(enabled)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    sliderSpeed = 1.0f
                    sliderPitch = 1.0f
                    if (onReset != null) {
                        onReset()
                    } else {
                        onToggleSlowedReverb(false)
                        onSpeedSelected(1.0f)
                        onPitchSelected(1.0f)
                    }
                },
                modifier = Modifier.testTag("speed_reset_button")
            ) {
                Text(stringResource(R.string.speed_reset))
            }
        }
    )
}
