package com.yasinjahantigh.logarhythm.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.yasinjahantigh.logarhythm.core.artwork.ArtworkVersionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * Custom Media3 BitmapLoader that extracts embedded audio album artwork from content URIs
 * for system media notifications and lock screen media controls.
 */
@OptIn(UnstableApi::class)
class AudioBitmapLoader(private val context: Context) : BitmapLoader {

    private val cache = LruCache<String, Bitmap>(16)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                future.set(bitmap)
            } else {
                future.setException(IllegalArgumentException("Failed to decode artwork byte array"))
            }
        } catch (e: Exception) {
            future.setException(e)
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val cacheKey = "$uri#v${ArtworkVersionStore.versionOf(uri.toString())}"
        synchronized(cache) {
            val cached = cache.get(cacheKey)
            if (cached != null) {
                return Futures.immediateFuture(cached)
            }
        }

        val future = SettableFuture.create<Bitmap>()
        Dispatchers.IO.asExecutor().execute {
            try {
                val bitmap = loadAudioArtwork(context, uri, targetEdge = 512)
                if (bitmap != null) {
                    synchronized(cache) {
                        cache.put(cacheKey, bitmap)
                    }
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Unable to decode artwork for $uri"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData!!)
        }
        if (metadata.artworkUri != null) {
            return loadBitmap(metadata.artworkUri!!)
        }
        return null
    }

    companion object {
        fun loadAudioArtwork(context: Context, uri: Uri, targetEdge: Int = 512): Bitmap? {
            // 1. Try embedded picture via MediaMetadataRetriever (original high-quality cover art)
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val picture = retriever.embeddedPicture
                try { retriever.release() } catch (_: Exception) {}

                if (picture != null) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= targetEdge && bounds.outHeight / (sample * 2) >= targetEdge) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, opts)
                    if (bitmap != null) return bitmap
                }
            } catch (_: Exception) {}

            // 2. Try scoped storage loadThumbnail on Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(targetEdge, targetEdge), null)
                } catch (_: Exception) {}
            }

            // 3. Try standard image stream decoding if URI points to image data
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) return bitmap
                }
            } catch (_: Exception) {}

            return null
        }
    }
}
