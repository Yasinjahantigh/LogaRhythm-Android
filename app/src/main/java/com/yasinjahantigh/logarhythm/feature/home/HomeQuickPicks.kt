package com.yasinjahantigh.logarhythm.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.designsystem.bounceOnPress

/**
 * Spotify-style compact 2-column grid of up to 6 quick-launch tiles at the top of Home
 * (Favorites, pinned favorite playlists, top Daily Mix, and top artist).
 */
@Composable
fun QuickPicksGrid(
    items: List<QuickPickItem>,
    onItemClick: (QuickPickItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val rows = remember(items) { items.take(6).chunked(2) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("home_quick_picks_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.home_quick_picks_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { item ->
                    QuickPickTile(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickPickTile(
    item: QuickPickItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val displayTitle = when (item.kind) {
        QuickPickKind.FAVORITES -> stringResource(R.string.home_quick_pick_favorites)
        QuickPickKind.DAILY_MIX -> when (item.id) {
            "daily_mix_1" -> stringResource(R.string.daily_mix_title_rotation)
            "daily_mix_2" -> stringResource(R.string.daily_mix_title_rediscover)
            else -> stringResource(R.string.daily_mix_title_discovery)
        }
        QuickPickKind.PLAYLIST, QuickPickKind.ARTIST -> item.title
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        modifier = modifier
            .height(56.dp)
            .bounceOnPress(interactionSource, pressedScale = 0.96f)
            .testTag("home_quick_pick_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.kind == QuickPickKind.FAVORITES) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(scheme.primary, scheme.tertiary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                ArtworkImage(
                    uri = item.artworkUri,
                    fallbackKey = displayTitle,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                    glyphSize = 20.sp,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = if (item.trackCount > 0) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.trackCount > 0) {
                    Text(
                        text = stringResource(R.string.home_quick_pick_tracks_count, item.trackCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
