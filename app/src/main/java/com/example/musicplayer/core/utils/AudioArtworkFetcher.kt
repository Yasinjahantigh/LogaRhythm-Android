package com.example.musicplayer.core.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom Coil fetcher for loading audio album artwork.
 * Uses ContentResolver.loadThumbnail on API 29+ with fallback to MediaMetadataRetriever
 * on older versions or when scoped storage thumbnail generation is unavailable.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    /** Edge length actually needed on screen; list rows ask for ~150px, the player for ~1000px. */
    private val targetEdge: Int = maxOf(
        options.size.width.pxOrElse { 512 },
        options.size.height.pxOrElse { 512 }
    ).coerceIn(128, 768)

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = resolver.loadThumbnail(uri, Size(targetEdge, targetEdge), null)
                return@withContext DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            } catch (_: Exception) {
                // Fall back to embedded picture extraction
            }
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            try {
                retriever.release()
            } catch (_: Exception) {
            }

            if (picture != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetEdge && bounds.outHeight / (sample * 2) >= targetEdge) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(
                    picture, 0, picture.size, BitmapFactory.Options().apply { inSampleSize = sample }
                )
                if (bitmap != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Exception) {
            // No embedded artwork available
        }

        null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            val authority = data.authority
            if (scheme == ContentResolver.SCHEME_CONTENT &&
                (authority == "media" || authority?.contains("media") == true)
            ) {
                return AudioArtworkFetcher(context, data, options)
            }
            return null
        }
    }
}
