package com.yasinjahantigh.logarhythm.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yasinjahantigh.logarhythm.core.artwork.ArtworkVersionStore
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track

/**
 * Single source of truth for converting domain Track to Media3 MediaItem.
 */
object MediaItemMapper {

    private const val EXTRA_DURATION_MS = "duration_ms"

    fun toMediaItem(track: Track): MediaItem {
        val extras = Bundle().apply {
            putLong(EXTRA_DURATION_MS, track.durationMs)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setExtras(extras)
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

    fun fallbackTrack(item: MediaItem): Track? {
        val id = item.mediaId.takeIf { it.isNotBlank() } ?: return null
        val meta = item.mediaMetadata
        val sourceType = if (id.startsWith("web:")) SourceType.WEB else SourceType.LOCAL
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        val durationMs = meta.extras?.getLong(EXTRA_DURATION_MS, 0L)?.coerceAtLeast(0L) ?: 0L
        return Track(
            id = id,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            album = meta.albumTitle?.toString().orEmpty(),
            durationMs = durationMs,
            artworkUri = meta.artworkUri?.toString(),
            sourceType = sourceType,
            uri = uri
        )
    }
}
