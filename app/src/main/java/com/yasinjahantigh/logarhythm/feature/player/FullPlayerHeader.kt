package com.yasinjahantigh.logarhythm.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R

/**
 * Slim header: collapse on the left, a live "Now playing" label (with the equaliser) and the
 * album in the middle, and one overflow menu on the right. Lyrics, queue, timer and speed moved
 * down to [PlayerQuickActions] where the thumb is; everything else lives in the menu.
 */
@Composable
fun FullPlayerHeader(
    isPlaying: Boolean,
    subtitle: String?,
    isVinylMode: Boolean,
    onCollapse: () -> Unit,
    onToggleVinyl: () -> Unit,
    onStartRadio: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSearchGoogleLyrics: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onShareSong: () -> Unit,
    onEditSongDetails: () -> Unit,
    onHideSong: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.testTag("collapse_player_button")
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_collapse_player),
                tint = scheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EqualizerVisualizer(
                    isPlaying = isPlaying,
                    barColor = scheme.primary,
                    maxHeight = 12.dp,
                    barCount = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.testTag("player_more_options_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.player_more_options),
                    tint = scheme.onSurface
                )
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_add_to_playlist)) },
                    leadingIcon = { Icon(Icons.Default.PlaylistAddCheck, contentDescription = null) },
                    onClick = { showMoreMenu = false; onAddToPlaylist() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_menu_start_radio)) },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    onClick = { showMoreMenu = false; onStartRadio() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_menu_equalizer)) },
                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    onClick = { showMoreMenu = false; onOpenEqualizer() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(if (isVinylMode) R.string.player_style_artwork else R.string.player_style_vinyl)
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                    onClick = { showMoreMenu = false; onToggleVinyl() },
                    modifier = Modifier.testTag("player_style_toggle")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_track_info)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { showMoreMenu = false; onEditSongDetails() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_search_lyrics_google)) },
                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                    onClick = { showMoreMenu = false; onSearchGoogleLyrics() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_song_details)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = { showMoreMenu = false; onOpenSongDetails() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share_song)) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { showMoreMenu = false; onShareSong() }
                )
                if (onHideSong != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_hide_track)) },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                        onClick = { showMoreMenu = false; onHideSong() }
                    )
                }
            }
        }
    }
}
