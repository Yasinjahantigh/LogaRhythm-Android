package com.example.musicplayer.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.R

@Composable
fun FullPlayerControls(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onRewind10: () -> Unit,
    onFastForward10: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick tools row: Shuffle, Rewind 10, Fast Forward 10, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(48.dp).testTag("player_shuffle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.cd_shuffle),
                    tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onRewind10,
                modifier = Modifier.size(48.dp).testTag("player_rewind_10_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = stringResource(R.string.cd_rewind_10),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onFastForward10,
                modifier = Modifier.size(48.dp).testTag("player_forward_10_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Forward10,
                    contentDescription = stringResource(R.string.cd_forward_10),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onCycleRepeat,
                modifier = Modifier.size(48.dp).testTag("player_repeat_button")
            ) {
                val isRepeatOne = repeatMode == Player.REPEAT_MODE_ONE
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                Icon(
                    imageVector = if (isRepeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = stringResource(if (isRepeatOne) R.string.cd_repeat_one else R.string.cd_repeat),
                    tint = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main playback row: Previous, Large Play/Pause, Next
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(56.dp).testTag("player_previous_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.cd_skip_prev),
                    modifier = Modifier.size(40.dp)
                )
            }

            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(76.dp).testTag("player_play_pause_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    modifier = Modifier.size(42.dp)
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(56.dp).testTag("player_next_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.cd_skip_next),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}
