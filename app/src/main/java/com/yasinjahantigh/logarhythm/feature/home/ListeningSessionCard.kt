package com.yasinjahantigh.logarhythm.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.ui.designsystem.bounceOnPress

/**
 * Compact, prominent quick-action strip right near the top of Home:
 * compact Shuffle + immediate access to Download, Create Playlist, and AI Mix.
 */
@Composable
fun HomeQuickActionsBar(
    onShuffleAll: () -> Unit,
    onDownloadClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onAiMixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("home_quick_actions_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionPill(
            icon = Icons.Filled.Shuffle,
            label = stringResource(R.string.home_shuffle_compact),
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            onClick = onShuffleAll,
            tag = "home_shuffle_all_button"
        )
        QuickActionPill(
            icon = Icons.Filled.CloudDownload,
            label = stringResource(R.string.home_quick_download),
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            onClick = onDownloadClick,
            tag = "home_download_banner"
        )
        QuickActionPill(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = stringResource(R.string.home_quick_new_playlist),
            containerColor = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            onClick = onCreatePlaylistClick,
            tag = "home_create_playlist_button"
        )
        QuickActionPill(
            icon = Icons.Filled.AutoAwesome,
            label = stringResource(R.string.home_quick_ai_mix),
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
            onClick = onAiMixClick,
            tag = "home_ai_banner"
        )
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    tag: String,
    border: BorderStroke? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        interactionSource = interactionSource,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .bounceOnPress(interactionSource, pressedScale = 0.94f)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
