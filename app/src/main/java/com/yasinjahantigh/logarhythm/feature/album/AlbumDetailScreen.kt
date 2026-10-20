package com.yasinjahantigh.logarhythm.feature.album

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.WindowInsets
import com.yasinjahantigh.logarhythm.ui.components.DetailHero
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToArtist: ((String) -> Unit)? = null,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (selectedTrackForMenu != null) {
        val menuTrack = selectedTrackForMenu!!
        SongMenuBottomSheet(
            track = menuTrack,
            onDismissRequest = { selectedTrackForMenu = null },
            onPlayNext = { viewModel.playNext(menuTrack) },
            onAddToQueue = { viewModel.addToQueue(menuTrack) },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            onGoToArtist = if (onNavigateToArtist != null && menuTrack.artist.isNotBlank() && menuTrack.artist != "Unknown Artist") {
                { onNavigateToArtist(menuTrack.artist) }
            } else null,
            onHideTrack = {
                viewModel.hideTrack(menuTrack)
                Toast.makeText(context, context.getString(R.string.track_hidden_toast, menuTrack.title), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddToPlaylistDialog && selectedTrackForMenu != null) {
        val targetTrack = selectedTrackForMenu!!
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { playlist ->
                playlistViewModel.addTrackToPlaylist(playlist.id, targetTrack.id)
                showAddToPlaylistDialog = false
                Toast.makeText(context, context.getString(R.string.added_to_playlist, playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylist = { name ->
                playlistViewModel.createPlaylist(name)
            }
        )
    }

    val displayAlbumName = if (viewModel.albumName.isBlank() ||
        viewModel.albumName.equals("<unknown>", ignoreCase = true) ||
        viewModel.albumName.equals("unknown", ignoreCase = true) ||
        viewModel.albumName.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_album)
    } else viewModel.albumName

    val rawArtistName = album?.artist ?: tracks.firstOrNull()?.artist.orEmpty()
    val displayArtistName = if (rawArtistName.isBlank() ||
        rawArtistName.equals("<unknown>", ignoreCase = true) ||
        rawArtistName.equals("unknown", ignoreCase = true) ||
        rawArtistName.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else rawArtistName

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("album_detail_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { innerPadding ->
        val bottomPadding = com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding.current

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                DetailHero(
                    title = displayAlbumName,
                    subtitle = displayArtistName,
                    onSubtitleClick = onNavigateToArtist?.let { nav -> { nav(displayArtistName) } },
                    meta = stringResource(R.string.album_track_count, tracks.size),
                    artworkUri = album?.artworkUri ?: tracks.firstOrNull()?.artworkUri,
                    onBack = onNavigateBack,
                    onPlay = viewModel::playAll,
                    onShuffle = viewModel::shuffleAll,
                    actionsEnabled = tracks.isNotEmpty(),
                    backTag = "album_back_button",
                    playTag = "album_play_button",
                    shuffleTag = "album_shuffle_button"
                )
            }

            if (tracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty_album_tracks),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(items = tracks, key = { it.id }) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    TrackListItem(
                        track = track,
                        onClick = { viewModel.playTrack(track) },
                        isCurrentTrack = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onMenuClick = { selectedTrackForMenu = track }
                    )
                }
            }
        }
    }
}
