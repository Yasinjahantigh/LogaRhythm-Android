package com.yasinjahantigh.logarhythm.core.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
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
 * Custom Coil fetcher for loading audio album artwork in highest possible quality.
 * Prioritizes extracting the original embedded picture from the audio file tags
 * at full 32-bit (ARGB_8888) resolution (supporting up to 2048px for large screen & full player displays).
 * Falls back to scoped storage thumbnails or content streams only when embedded artwork is absent.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    /** Edge length needed on screen; list rows request ~150px, full player / detail requests up to 1440px+. */
    private val targetEdge: Int = maxOf(
        options.size.width.pxOrElse { 1080 },
        options.size.height.pxOrElse { 1080 }
    ).coerceIn(128, 2048)

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Prioritize original embedded picture from audio metadata for pristine high resolution
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            try {
                retriever.release()
            } catch (_: Exception) {}

            if (picture != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)

                // Only downsample if image genuinely exceeds the target size or 2048px
                var sample = 1
                val maxLimit = maxOf(targetEdge, 1080).coerceAtMost(2048)
                while (bounds.outWidth / (sample * 2) >= maxLimit && bounds.outHeight / (sample * 2) >= maxLimit) {
                    sample *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOptions)
                if (bitmap != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = sample > 1,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Exception) {
            // Embedded picture extraction failed or not available for this URI
        }

        // 2. Fall back to scoped storage loadThumbnail on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = resolver.loadThumbnail(uri, Size(targetEdge, targetEdge), null)
                return@withContext DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            } catch (_: Exception) {
                // Fall back to direct stream decode
            }
        }

        // 3. Fall back to direct stream decode (e.g. for image file URIs)
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Exception) {
            // No stream available
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
