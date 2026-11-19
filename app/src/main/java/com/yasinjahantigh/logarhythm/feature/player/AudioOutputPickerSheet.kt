package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.playback.AudioOutputDevice
import com.yasinjahantigh.logarhythm.playback.AudioOutputKind
import com.yasinjahantigh.logarhythm.playback.AudioOutputState

internal fun AudioOutputKind.icon(): ImageVector = when (this) {
    AudioOutputKind.BLUETOOTH -> Icons.Filled.BluetoothAudio
    AudioOutputKind.WIRED -> Icons.Filled.Headphones
    AudioOutputKind.USB -> Icons.Filled.Usb
    AudioOutputKind.SPEAKER -> Icons.Filled.PhoneAndroid
}

@Composable
internal fun AudioOutputDevice.displayName(): String =
    name.takeIf { it.isNotBlank() } ?: when (kind) {
        AudioOutputKind.SPEAKER -> stringResource(R.string.audio_output_speaker)
        AudioOutputKind.WIRED -> stringResource(R.string.audio_output_wired)
        AudioOutputKind.USB -> stringResource(R.string.audio_output_usb)
        AudioOutputKind.BLUETOOTH -> stringResource(R.string.audio_output_bluetooth)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputPickerSheet(
    state: AudioOutputState,
    onRefresh: () -> Unit,
    onSelectDevice: (AudioOutputDevice) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenSystemSwitcher: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    var localVolume by remember(state.volumeFraction) { mutableFloatStateOf(state.volumeFraction) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("audio_output_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = state.activeKind.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_output_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.audio_output_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            state.devices.forEach { device ->
                val containerColor = if (device.isActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val contentColor = if (device.isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectDevice(device) }
                        .testTag("audio_output_device_${device.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = device.kind.icon(),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.displayName(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val kindLabel = when (device.kind) {
                                AudioOutputKind.SPEAKER -> stringResource(R.string.audio_output_speaker)
                                AudioOutputKind.WIRED -> stringResource(R.string.audio_output_wired)
                                AudioOutputKind.USB -> stringResource(R.string.audio_output_usb)
                                AudioOutputKind.BLUETOOTH -> stringResource(R.string.audio_output_bluetooth)
                            }
                            if (device.name.isNotBlank() && !device.name.equals(kindLabel, ignoreCase = true)) {
                                Text(
                                    text = kindLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (device.isActive) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.audio_output_active_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Quick media volume control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.audio_output_volume_label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Slider(
                    value = localVolume,
                    onValueChange = {
                        localVolume = it
                        onVolumeChange(it)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("audio_output_volume_slider")
                )
            }

            // Native Android System Output Switcher button (reliable for A2DP/Bluetooth/Speaker switching)
            FilledTonalButton(
                onClick = onOpenSystemSwitcher,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("audio_output_system_switcher_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SpeakerGroup,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.audio_output_system_switcher))
            }
        }
    }
}
