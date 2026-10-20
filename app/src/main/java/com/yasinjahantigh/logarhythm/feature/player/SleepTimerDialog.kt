package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import kotlin.math.roundToInt

private enum class SleepTimerSelectionMode {
    MINUTES,
    TRACKS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    remainingMs: Long?,
    remainingTracks: Int? = null,
    isEndOfTrack: Boolean = false,
    onSetTimerMinutes: (Int) -> Unit,
    onSetTimerTracks: (Int) -> Unit = {},
    onSetEndOfTrack: () -> Unit = { onSetTimerTracks(1) },
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTimerActive = remainingMs != null || remainingTracks != null

    var selectedMode by remember {
        mutableStateOf(
            if (remainingTracks != null || isEndOfTrack) SleepTimerSelectionMode.TRACKS
            else SleepTimerSelectionMode.MINUTES
        )
    }

    var minutesSliderValue by remember {
        val initialMinutes = if (remainingMs != null && remainingTracks == null) {
            ((remainingMs + 59_999L) / 60_000L).coerceIn(5L, 120L).toFloat()
        } else {
            30f
        }
        mutableFloatStateOf(initialMinutes)
    }

    var tracksSliderValue by remember {
        val initialTracks = (remainingTracks ?: 3).coerceIn(1, 30).toFloat()
        mutableFloatStateOf(initialTracks)
    }

    val currentMinutes = minutesSliderValue.roundToInt().coerceIn(5, 120)
    val currentTracks = tracksSliderValue.roundToInt().coerceIn(1, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sleep_timer_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Active Timer Status Card
                if (isTimerActive) {
                    val statusText = when {
                        remainingTracks != null -> {
                            if (remainingTracks == 1) {
                                stringResource(R.string.sleep_timer_one_track)
                            } else {
                                stringResource(R.string.sleep_timer_tracks_remaining, remainingTracks)
                            }
                        }
                        isEndOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
                        remainingMs != null -> TimeUtils.formatDuration(remainingMs)
                        else -> ""
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("sleep_timer_active_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.sleep_timer_active, statusText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(
                                onClick = onCancelTimer,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.sleep_timer_cancel))
                            }
                        }
                    }
                }

                // 1. Minutes Slider Card (نوار اول: تعداد دقیقه)
                val isMinutesSelected = selectedMode == SleepTimerSelectionMode.MINUTES
                OutlinedCard(
                    onClick = { selectedMode = SleepTimerSelectionMode.MINUTES },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isMinutesSelected) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isMinutesSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sleep_timer_card_minutes")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isMinutesSelected,
                                onClick = { selectedMode = SleepTimerSelectionMode.MINUTES },
                                modifier = Modifier.testTag("sleep_timer_radio_minutes")
                            )
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = if (isMinutesSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.sleep_timer_tab_minutes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isMinutesSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.sleep_timer_minutes_count, currentMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("sleep_timer_minutes_value")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Minutes Slider (نوار دقیقه)
                        Slider(
                            value = minutesSliderValue,
                            onValueChange = {
                                selectedMode = SleepTimerSelectionMode.MINUTES
                                minutesSliderValue = it
                            },
                            valueRange = 5f..120f,
                            steps = 22, // 5 min increments
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sleep_timer_minutes_slider")
                        )

                        // Quick Minute Chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(15, 30, 45, 60, 90).forEach { presetMinutes ->
                                FilterChip(
                                    selected = isMinutesSelected && currentMinutes == presetMinutes,
                                    onClick = {
                                        selectedMode = SleepTimerSelectionMode.MINUTES
                                        minutesSliderValue = presetMinutes.toFloat()
                                    },
                                    label = {
                                        Text(stringResource(R.string.sleep_timer_minutes_count, presetMinutes))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("sleep_timer_chip_$presetMinutes")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Track Count Slider Card (نوار دوم: تعداد ترک)
                val isTracksSelected = selectedMode == SleepTimerSelectionMode.TRACKS
                OutlinedCard(
                    onClick = { selectedMode = SleepTimerSelectionMode.TRACKS },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isTracksSelected) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isTracksSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sleep_timer_card_tracks")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = isTracksSelected,
                                onClick = { selectedMode = SleepTimerSelectionMode.TRACKS },
                                modifier = Modifier.testTag("sleep_timer_radio_tracks")
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = if (isTracksSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.sleep_timer_tab_tracks),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isTracksSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (currentTracks == 1) {
                                    stringResource(R.string.sleep_timer_one_track)
                                } else {
                                    stringResource(R.string.sleep_timer_tracks_count, currentTracks)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("sleep_timer_tracks_value")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Tracks Slider (نوار تعداد ترک)
                        Slider(
                            value = tracksSliderValue,
                            onValueChange = {
                                selectedMode = SleepTimerSelectionMode.TRACKS
                                tracksSliderValue = it
                            },
                            valueRange = 1f..30f,
                            steps = 28, // 1 track increments
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sleep_timer_tracks_slider")
                        )

                        // Quick Track Count Chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1, 2, 3, 5, 10).forEach { presetTracks ->
                                val chipLabel = if (presetTracks == 1) {
                                    stringResource(R.string.sleep_timer_end_of_track)
                                } else {
                                    stringResource(R.string.sleep_timer_tracks_count, presetTracks)
                                }
                                FilterChip(
                                    selected = isTracksSelected && currentTracks == presetTracks,
                                    onClick = {
                                        selectedMode = SleepTimerSelectionMode.TRACKS
                                        tracksSliderValue = presetTracks.toFloat()
                                    },
                                    label = { Text(chipLabel) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("sleep_timer_track_chip_$presetTracks")
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == SleepTimerSelectionMode.MINUTES) {
                        onSetTimerMinutes(currentMinutes)
                    } else {
                        onSetTimerTracks(currentTracks)
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("sleep_timer_confirm_button")
            ) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("sleep_timer_dismiss_button")
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
