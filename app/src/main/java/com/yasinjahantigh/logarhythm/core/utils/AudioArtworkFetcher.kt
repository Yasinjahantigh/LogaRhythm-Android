package com.yasinjahantigh.logarhythm.core.utils

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.pxOrElse
import com.yasinjahantigh.logarhythm.core.artwork.ArtworkVersionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer

/**
 * Custom Coil fetcher for loading audio album artwork cleanly and efficiently.
 * Uses MediaStore's native thumbnail cache for small list rows, returns embedded pictures as
 * [SourceResult] so Coil applies exact target sizing, RGB_565 when allowed, and DiskCache
 * persistence, and remembers coverless tracks in a versioned negative cache to avoid repeat I/O.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
    private val maxHeroEdge: Int
) : Fetcher {

    /** Edge length needed on screen; list rows request ~150px, full player / detail requests up to maxHeroEdge. */
    private val targetEdge: Int = maxOf(
        options.size.width.pxOrElse { 720 },
        options.size.height.pxOrElse { 720 }
    ).coerceIn(128, maxHeroEdge)

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val version = ArtworkVersionStore.versionOf(uriString)
        val negativeKey = "$uriString#v$version"
        synchronized(missingArtCache) {
            if (missingArtCache.get(negativeKey) == true) {
                return@withContext null
            }
        }

        val resolver = context.contentResolver
        val isThumbnail = targetEdge <= 320

        // Fast path for list thumbnails: MediaStore maintains pre-scaled system thumbnails in native code.
        if (isThumbnail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val thumb = resolver.loadThumbnail(uri, Size(targetEdge, targetEdge), null)
                return@withContext DrawableResult(
                    drawable = BitmapDrawable(context.resources, thumb),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (_: Exception) {
                // Fall back to embedded extraction below
            }
        }

        // Embedded picture extraction: always release retriever in finally and hand raw encoded
        // bytes to Coil via SourceResult so DiskCache, RGB_565, and exact target sizing all apply.
        val picture: ByteArray? = run {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } catch (_: Exception) {
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
            }
        }

        if (picture != null && picture.isNotEmpty()) {
            val buffer = Buffer().write(picture)
            return@withContext SourceResult(
                source = ImageSource(buffer, context),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        }

        // Fall back to scoped storage loadThumbnail on Android Q+
        if (!isThumbnail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = resolver.loadThumbnail(uri, Size(targetEdge, targetEdge), null)
                return@withContext DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (_: Exception) {
                // Fall back to direct stream decode
            }
        }

        // Fall back to direct stream decode (e.g. for image file URIs), downsampled to targetEdge.
        try {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null && bytes.isNotEmpty()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    return@withContext SourceResult(
                        source = ImageSource(Buffer().write(bytes), context),
                        mimeType = null,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Exception) {
            // No stream available
        }

        synchronized(missingArtCache) {
            missingArtCache.put(negativeKey, true)
        }
        null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        private val maxHeroEdge: Int by lazy {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am?.isLowRamDevice == true) 1024 else 1440
        }

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            val authority = data.authority
            if (scheme == ContentResolver.SCHEME_CONTENT &&
                (authority == "media" || authority?.contains("media") == true)
            ) {
                return AudioArtworkFetcher(context, data, options, maxHeroEdge)
            }
            return null
        }
    }

    companion object {
        private val missingArtCache = LruCache<String, Boolean>(512)
    }
}
