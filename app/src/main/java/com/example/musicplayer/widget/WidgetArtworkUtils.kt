package com.example.musicplayer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility for safely loading scaled album artwork thumbnails for the Glance widget
 * on background threads without blocking widget render loops.
 */
object WidgetArtworkUtils {

    suspend fun loadThumbnail(context: Context, uri: Uri?): Bitmap? = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return@withContext context.contentResolver.loadThumbnail(uri, Size(160, 160), null)
            } catch (_: Exception) {
                // Fall back to MediaMetadataRetriever
            }
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture
            try {
                retriever.release()
            } catch (_: Exception) {}

            if (picture != null) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(picture, 0, picture.size, options)

                val reqSize = 160
                var sampleSize = 1
                while (options.outWidth / sampleSize > reqSize || options.outHeight / sampleSize > reqSize) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                return@withContext BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOptions)
            }
        } catch (_: Exception) {
            // Cannot extract embedded picture
        }

        null
    }
}
