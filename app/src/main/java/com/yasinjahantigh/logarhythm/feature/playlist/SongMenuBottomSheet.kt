package com.yasinjahantigh.logarhythm.feature.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.io.File
import java.util.Locale
import com.yasinjahantigh.logarhythm.core.utils.FileUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.model.Track
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuBottomSheet(
    track: Track,
    onDismissRequest: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onViewLyrics: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDeleteTrack: (() -> Unit)? = null,
    onEditTrack: (() -> Unit)? = null,
    onHideTrack: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    var showDetailsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showHideConfirmDialog by remember { mutableStateOf(false) }

    if (showHideConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHideConfirmDialog = false },
            title = { Text(text = stringResource(R.string.dialog_hide_song_title)) },
            text = { Text(text = stringResource(R.string.dialog_hide_song_confirm, track.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        showHideConfirmDialog = false
                        onHideTrack?.invoke()
                        onDismissRequest()
                    }
                ) {
                    Text(text = stringResource(R.string.btn_hide))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirmDialog = false }) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showDetailsDialog) {
        SongDetailsDialog(
            track = track,
            onDismiss = { showDetailsDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = stringResource(R.string.dialog_delete_song_title)) },
            text = { Text(text = stringResource(R.string.dialog_delete_song_confirm, track.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteTrack?.invoke()
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier.testTag("song_menu_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Track Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.cd_artwork, track.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp),
                        placeholder = painterResource(R.drawable.ic_music_placeholder),
                        error = painterResource(R.drawable.ic_music_placeholder)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • ${TimeUtils.formatDuration(track.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Play next
            SongMenuOption(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = stringResource(R.string.action_play_next),
                tag = "menu_play_next",
                onClick = { onPlayNext(); onDismissRequest() }
            )

            // Add to queue
            SongMenuOption(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = stringResource(R.string.action_add_to_queue),
                tag = "menu_add_to_queue",
                onClick = { onAddToQueue(); onDismissRequest() }
            )

            // Favorite toggle
            if (onToggleFavorite != null) {
                SongMenuOption(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    title = stringResource(if (isFavorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    tag = "menu_toggle_favorite",
                    iconTint = if (isFavorite) MaterialTheme.colorScheme.primary else null,
                    onClick = { onToggleFavorite(); onDismissRequest() }
                )
            }

            // Add to playlist
            SongMenuOption(
                icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                title = stringResource(R.string.action_add_to_playlist),
                tag = "menu_add_to_playlist",
                onClick = { onAddToPlaylist(); onDismissRequest() }
            )

            // Remove from playlist (when viewing playlist tracks)
            if (onRemoveFromPlaylist != null) {
                SongMenuOption(
                    icon = Icons.Default.DeleteOutline,
                    title = stringResource(R.string.action_remove_from_playlist),
                    tag = "menu_remove_from_playlist",
                    onClick = { onRemoveFromPlaylist(); onDismissRequest() }
                )
            }

            // View / Edit Lyrics
            if (onViewLyrics != null) {
                SongMenuOption(
                    icon = Icons.Default.Lyrics,
                    title = stringResource(R.string.action_view_lyrics),
                    tag = "menu_view_lyrics",
                    onClick = { onViewLyrics(); onDismissRequest() }
                )
            }

            // Search Lyrics on Google
            SongMenuOption(
                icon = Icons.Default.OpenInBrowser,
                title = stringResource(R.string.action_search_lyrics_google),
                tag = "menu_google_lyrics",
                onClick = {
                    val query = "${track.title} ${track.artist} lyrics"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"))
                    context.startActivity(intent)
                    onDismissRequest()
                }
            )

            // Go to album
            if (onGoToAlbum != null && track.album.isNotBlank() && track.album != "Unknown Album") {
                SongMenuOption(
                    icon = Icons.Default.Album,
                    title = stringResource(R.string.action_go_to_album),
                    tag = "menu_go_to_album",
                    onClick = { onGoToAlbum(); onDismissRequest() }
                )
            }

            // Go to artist
            if (onGoToArtist != null && track.artist.isNotBlank() && track.artist != "Unknown Artist") {
                SongMenuOption(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.action_go_to_artist),
                    tag = "menu_go_to_artist",
                    onClick = { onGoToArtist(); onDismissRequest() }
                )
            }

            // Edit song metadata
            if (onEditTrack != null) {
                SongMenuOption(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.action_edit_track_info),
                    tag = "menu_edit_track",
                    onClick = { onEditTrack(); onDismissRequest() }
                )
            }

            // Song Information / Details
            SongMenuOption(
                icon = Icons.Default.Info,
                title = stringResource(R.string.action_song_details),
                tag = "menu_song_details",
                onClick = { showDetailsDialog = true }
            )

            // Share song
            SongMenuOption(
                icon = Icons.Default.Share,
                title = stringResource(R.string.action_share_song),
                tag = "menu_share_song",
                onClick = {
                    shareAudioTrack(context, track)
                    onDismissRequest()
                }
            )

            // Hide song from library
            if (onHideTrack != null) {
                SongMenuOption(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.action_hide_track),
                    tag = "menu_hide_song",
                    onClick = { showHideConfirmDialog = true }
                )
            }

            // Delete song from device
            SongMenuOption(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.action_delete_song),
                tag = "menu_delete_song",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // MediaStore's system confirmation is the authoritative prompt.
                        onDeleteTrack?.invoke()
                        onDismissRequest()
                    } else {
                        showDeleteConfirmDialog = true
                    }
                }
            )
        }
    }
}

@Composable
private fun SongMenuOption(
    icon: ImageVector,
    title: String,
    tag: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (iconTint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SongDetailsDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val filePath = remember(track) { FileUtils.resolveTrackPath(context, track) }
    val fileSizeMb = remember(filePath) {
        if (filePath != null) {
            val f = File(filePath)
            if (f.exists()) String.format(Locale.US, "%.2f MB", f.length().toDouble() / (1024 * 1024)) else null
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.dialog_song_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = stringResource(R.string.detail_title), value = track.title)
                DetailRow(label = stringResource(R.string.detail_artist), value = track.artist)
                DetailRow(label = stringResource(R.string.detail_album), value = track.album)
                DetailRow(label = stringResource(R.string.detail_duration), value = TimeUtils.formatDuration(track.durationMs))
                DetailRow(label = stringResource(R.string.detail_source), value = track.sourceType.name)
                if (fileSizeMb != null) {
                    DetailRow(label = stringResource(R.string.detail_file_size), value = fileSizeMb)
                }
                if (filePath != null) {
                    DetailRow(label = stringResource(R.string.detail_file_path), value = filePath)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_close))
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareAudioTrack(context: Context, track: Track) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            if (track.uri.isNotBlank()) {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri))
            }
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist}")
            putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share_song)))
    } catch (_: Exception) {
        Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
    }
}
