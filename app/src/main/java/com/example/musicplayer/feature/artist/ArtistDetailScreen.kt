package com.example.musicplayer.feature.artist

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import com.example.R
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.library.TrackListItem
import com.example.musicplayer.feature.playlist.AddToPlaylistDialog
import com.example.musicplayer.feature.playlist.PlaylistViewModel
import com.example.musicplayer.feature.playlist.SongMenuBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAlbum: ((String) -> Unit)? = null,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
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
            onGoToAlbum = if (onNavigateToAlbum != null && menuTrack.album.isNotBlank() && menuTrack.album != "Unknown Album") {
                { onNavigateToAlbum(menuTrack.album) }
            } else null
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

    val displayArtistName = if (viewModel.artistName.isBlank() ||
        viewModel.artistName.equals("<unknown>", ignoreCase = true) ||
        viewModel.artistName.equals("unknown", ignoreCase = true) ||
        viewModel.artistName.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else viewModel.artistName

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("artist_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text(text = displayArtistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("artist_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val artworkUri = tracks.firstOrNull()?.artworkUri
                    if (artworkUri != null) {
                        AsyncImage(
                            model = artworkUri,
                            contentDescription = stringResource(R.string.cd_artwork, displayArtistName),
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_music_placeholder),
                            error = painterResource(id = R.drawable.ic_music_placeholder)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = viewModel.artistName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val albumCount = artist?.albumCount ?: 1
                    Text(
                        text = stringResource(R.string.artist_metadata, tracks.size, albumCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = viewModel::playAll,
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("artist_play_button"),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_play))
                        }
                        FilledTonalButton(
                            onClick = viewModel::shuffleAll,
                            enabled = tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("artist_shuffle_button"),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_shuffle))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty_artist_tracks),
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
