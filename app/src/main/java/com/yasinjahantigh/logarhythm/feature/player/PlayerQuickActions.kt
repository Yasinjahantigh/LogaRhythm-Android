package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Lyrics, queue, sleep timer and speed controls with live labels for active states. */
@Composable
fun PlayerQuickActions(
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    timerLabel: String?,
    onOpenTimer: () -> Unit,
    speedLabel: String?,
    onOpenSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        QuickAction(
            icon = Icons.Default.Lyrics,
            label = stringResource(R.string.player_action_lyrics),
            active = showLyrics,
            onClick = onToggleLyrics,
            modifier = Modifier.weight(1f).testTag("player_lyrics_toggle_button")
        )
        QuickAction(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = stringResource(R.string.player_action_queue),
            active = false,
            onClick = onOpenQueue,
            modifier = Modifier.weight(1f).testTag("player_queue_button")
        )
        QuickAction(
            icon = Icons.Default.Bedtime,
            label = timerLabel ?: stringResource(R.string.player_action_timer),
            active = timerLabel != null,
            onClick = onOpenTimer,
            modifier = Modifier.weight(1f).testTag("player_sleep_timer_button")
        )
        QuickAction(
            icon = Icons.Default.Speed,
            label = speedLabel ?: stringResource(R.string.player_action_speed),
            active = speedLabel != null,
            onClick = onOpenSpeed,
            modifier = Modifier.weight(1f).testTag("player_speed_button")
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (active) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
        label = "quick_action_container"
    )
    val tint = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
