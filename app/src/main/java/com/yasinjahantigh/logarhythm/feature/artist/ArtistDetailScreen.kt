package com.yasinjahantigh.logarhythm.feature.artist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Album
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.components.DetailHero
import com.yasinjahantigh.logarhythm.ui.components.cleanMetaOrNull

@Composable
fun ArtistDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAlbum: ((String, String) -> Unit)? = null,
    onNavigateToArtist: ((String) -> Unit)? = null,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    val topTracks = profile?.topTracks.orEmpty()
    val albums = profile?.albums.orEmpty()
    val appearsOn = profile?.appearsOn.orEmpty()

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
                { onNavigateToAlbum(menuTrack.album, menuTrack.albumArtist.ifBlank { menuTrack.artist }) }
            } else null,
            onGoToArtist = if (onNavigateToArtist != null && menuTrack.artist.isNotBlank() && menuTrack.artist != "Unknown Artist") {
                { artistName -> onNavigateToArtist(artistName) }
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

    val displayArtistName = if (viewModel.artistName.isBlank() ||
        viewModel.artistName.equals("<unknown>", ignoreCase = true) ||
        viewModel.artistName.equals("unknown", ignoreCase = true) ||
        viewModel.artistName.equals("null", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else viewModel.artistName

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("artist_detail_screen"),
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { innerPadding ->
        val bottomPadding = com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding.current

        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                DetailHero(
                    title = displayArtistName,
                    meta = stringResource(
                        R.string.artist_metadata,
                        tracks.size,
                        profile?.artist?.albumCount ?: albums.size
                    ),
                    artworkUri = tracks.firstOrNull()?.artworkUri,
                    round = true,
                    onBack = onNavigateBack,
                    onPlay = viewModel::playAll,
                    onShuffle = viewModel::shuffleAll,
                    actionsEnabled = tracks.isNotEmpty(),
                    backTag = "artist_back_button",
                    playTag = "artist_play_button",
                    shuffleTag = "artist_shuffle_button"
                )
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
                if (topTracks.isNotEmpty()) {
                    item(key = "header_top") {
                        ArtistSectionHeader(stringResource(R.string.artist_section_top_tracks))
                    }
                    items(items = topTracks, key = { "top_${it.id}" }) { track ->
                        ArtistTrackRow(track, currentTrack, isPlaying, viewModel) {
                            selectedTrackForMenu = track
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item(key = "header_albums") {
                        ArtistSectionHeader(stringResource(R.string.artist_section_albums))
                    }
                    item(key = "albums_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = albums, key = { it.id }) { album ->
                                ArtistAlbumCard(album = album, onNavigateToAlbum = onNavigateToAlbum)
                            }
                        }
                    }
                }

                if (appearsOn.isNotEmpty()) {
                    item(key = "header_appears_on") {
                        ArtistSectionHeader(stringResource(R.string.artist_section_appears_on))
                    }
                    items(items = appearsOn, key = { "appears_${it.id}" }) { track ->
                        ArtistTrackRow(track, currentTrack, isPlaying, viewModel) {
                            selectedTrackForMenu = track
                        }
                    }
                }

                item(key = "header_all") {
                    ArtistSectionHeader(stringResource(R.string.artist_section_all_tracks))
                }
                items(items = tracks, key = { "all_${it.id}" }) { track ->
                    ArtistTrackRow(track, currentTrack, isPlaying, viewModel) {
                        selectedTrackForMenu = track
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistTrackRow(
    track: Track,
    currentTrack: Track?,
    isPlaying: Boolean,
    viewModel: ArtistDetailViewModel,
    onMenu: () -> Unit
) {
    val isCurrent = currentTrack?.id == track.id
    TrackListItem(
        track = track,
        onClick = { viewModel.playTrack(track) },
        isCurrentTrack = isCurrent,
        isPlaying = isCurrent && isPlaying,
        onMenuClick = onMenu,
        onPlayNext = { viewModel.playNext(track) }
    )
}

@Composable
private fun ArtistSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun ArtistAlbumCard(album: Album, onNavigateToAlbum: ((String, String) -> Unit)?) {
    val name = album.name.cleanMetaOrNull() ?: stringResource(R.string.unknown_album)
    Column(
        modifier = Modifier
            .width(138.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = onNavigateToAlbum != null) { onNavigateToAlbum?.invoke(album.name, album.artist) }
    ) {
        ArtworkImage(
            uri = album.artworkUri,
            fallbackKey = name,
            modifier = Modifier.size(138.dp),
            shape = RoundedCornerShape(18.dp),
            glyphSize = 34.sp,
            contentDescription = stringResource(R.string.cd_album_item, name)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (album.year > 0) album.year.toString() else stringResource(
                R.string.album_track_count,
                album.trackCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
