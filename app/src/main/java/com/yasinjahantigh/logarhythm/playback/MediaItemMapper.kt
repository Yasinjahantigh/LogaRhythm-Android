package com.yasinjahantigh.logarhythm.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yasinjahantigh.logarhythm.core.artwork.ArtworkVersionStore
import com.yasinjahantigh.logarhythm.domain.model.Track

/**
 * Single source of truth for converting domain Track to Media3 MediaItem.
 */
object MediaItemMapper {

    fun toMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                track.artworkUri?.let { uriString ->
                    // A cover edit bumps the version, which gives system UI and Media3 a new key
                    // instead of showing the cached old bitmap.
                    val version = ArtworkVersionStore.versionOf(uriString)
                    val versioned = if (version > 0) "$uriString?v=$version" else uriString
                    setArtworkUri(versioned.toUri())
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
