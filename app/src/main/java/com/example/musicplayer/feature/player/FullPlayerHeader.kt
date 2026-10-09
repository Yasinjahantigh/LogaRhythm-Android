package com.example.musicplayer.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.musicplayer.core.utils.TimeUtils
import java.util.Locale

@Composable
fun FullPlayerHeader(
    showLyrics: Boolean,
    playbackSpeed: Float,
    sleepTimerRemainingMs: Long?,
    isSleepTimerEndOfTrack: Boolean,
    onCollapse: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenSleepTimerDialog: () -> Unit,
    onOpenQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSearchGoogleLyrics: () -> Unit,
    onOpenSongDetails: () -> Unit,
    onShareSong: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTimerActive = sleepTimerRemainingMs != null
    var showMoreMenu by remember { mutableStateOf(false) }

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
                modifier = Modifier.size(32.dp)
            )
        }

        // Center: Now playing title or active sleep timer badge
        if (isTimerActive) {
            val timerText = if (isSleepTimerEndOfTrack) {
                stringResource(R.string.sleep_timer_end_of_track)
            } else {
                TimeUtils.formatDuration(sleepTimerRemainingMs)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clickable(onClick = onOpenSleepTimerDialog)
                    .testTag("player_sleep_timer_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.now_playing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Speed button
            IconButton(
                onClick = onOpenSpeedDialog,
                modifier = Modifier.testTag("player_speed_button")
            ) {
                if (kotlin.math.abs(playbackSpeed - 1.0f) > 0.01f) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", playbackSpeed),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(R.string.cd_playback_speed),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Sleep timer button
            IconButton(
                onClick = onOpenSleepTimerDialog,
                modifier = Modifier.testTag("player_sleep_timer_button")
            ) {
                BadgedBox(
                    badge = {
                        if (isTimerActive) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = stringResource(R.string.cd_sleep_timer),
                        tint = if (isTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lyrics toggle button
            IconButton(
                onClick = onToggleLyrics,
                modifier = Modifier.testTag("player_lyrics_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = stringResource(R.string.cd_lyrics),
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Queue button
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.testTag("player_queue_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.cd_queue)
                )
            }

            // More Options Menu
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.testTag("player_more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.player_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_menu_equalizer)) },
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenEqualizer()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_menu_start_radio)) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onStartRadio()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_playlist)) },
                        leadingIcon = { Icon(Icons.Default.PlaylistAddCheck, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_search_lyrics_google)) },
                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onSearchGoogleLyrics()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_song_details)) },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSongDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share_song)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onShareSong()
                        }
                    )
                }
            }
        }
    }
}
