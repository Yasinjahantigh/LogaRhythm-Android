package com.example.musicplayer.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.musicplayer.domain.model.Album
import com.example.musicplayer.domain.model.Artist
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.feature.library.TrackListItem

@Composable
fun SearchResultsView(
    query: String,
    results: SearchResults,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClearQuery: () -> Unit = {},
    onTrackMenuClick: ((Track) -> Unit)? = null
) {
    if (results.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (results.isEmpty) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_no_results, query),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.search_no_results_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onClearQuery, modifier = Modifier.testTag("search_clear_from_empty_button")) {
                    Text(stringResource(R.string.search_clear_query_action))
                }
            }
        }
    } else {
        val bottomPadding = com.example.musicplayer.ui.designsystem.LocalPlaybackBottomPadding.current
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomPadding + 16.dp),
            modifier = modifier.fillMaxSize().testTag("search_results_list")
        ) {
            if (results.songs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.search_section_songs),
                        count = results.songs.size
                    )
                }
                items(items = results.songs, key = { "song_${it.id}" }) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onMenuClick = onTrackMenuClick?.let { { it(track) } }
                    )
                }
            }

            if (results.albums.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.search_section_albums),
                        count = results.albums.size
                    )
                }
                items(items = results.albums, key = { "album_${it.id}" }) { album ->
                    AlbumSearchItem(album = album, onClick = { onAlbumClick(album.name) })
                }
            }

            if (results.artists.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.search_section_artists),
                        count = results.artists.size
                    )
                }
                items(items = results.artists, key = { "artist_${it.id}" }) { artist ->
                    ArtistSearchItem(artist = artist, onClick = { onArtistClick(artist.name) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumSearchItem(album: Album, onClick: () -> Unit) {
    val displayAlbum = if (album.name.isBlank() ||
        album.name.equals("<unknown>", ignoreCase = true) ||
        album.name.equals("unknown", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_album)
    } else album.name

    val displayArtist = if (album.artist.isBlank() ||
        album.artist.equals("<unknown>", ignoreCase = true) ||
        album.artist.equals("unknown", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else album.artist

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.artworkUri,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_music_placeholder),
            error = painterResource(id = R.drawable.ic_music_placeholder)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayAlbum,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.search_album_metadata, displayArtist, album.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistSearchItem(artist: Artist, onClick: () -> Unit) {
    val displayArtist = if (artist.name.isBlank() ||
        artist.name.equals("<unknown>", ignoreCase = true) ||
        artist.name.equals("unknown", ignoreCase = true)
    ) {
        stringResource(R.string.unknown_artist)
    } else artist.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayArtist,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.artist_metadata, artist.trackCount, artist.albumCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
