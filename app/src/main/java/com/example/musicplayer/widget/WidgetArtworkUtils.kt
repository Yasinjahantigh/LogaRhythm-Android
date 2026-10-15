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

        // 1. Try embedded picture via MediaMetadataRetriever
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

                val reqSize = 256
                var sampleSize = 1
                while (options.outWidth / (sampleSize * 2) >= reqSize && options.outHeight / (sampleSize * 2) >= reqSize) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOptions)
                if (bitmap != null) return@withContext bitmap
            }
        } catch (_: Exception) {
            // Cannot extract embedded picture
        }

        // 2. Fall back to scoped storage loadThumbnail on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return@withContext context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
            } catch (_: Exception) {
                // Fall back to direct stream decode
            }
        }

        // 3. Fall back to content resolver stream
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return@withContext BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {}

        null
    }
}
