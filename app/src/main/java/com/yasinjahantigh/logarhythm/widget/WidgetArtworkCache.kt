package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.yasinjahantigh.logarhythm.playback.AudioBitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal object WidgetArtworkCache {
    suspend fun cover(context: Context, uri: String?): Bitmap? = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        cached(context, "cover", uri, 320)?.let { return@withContext it }
        val source = AudioBitmapLoader.loadAudioArtwork(context, Uri.parse(uri), 320) ?: return@withContext null
        val scale = 320f / maxOf(source.width, source.height).coerceAtLeast(1)
        val cover = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else source
        store(context, "cover", uri, cover, 320)
        cover
    }

    suspend fun blurred(context: Context, uri: String?): Bitmap? = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        cached(context, "blurred", uri, 320)?.let { return@withContext it }
        val source = AudioBitmapLoader.loadAudioArtwork(context, Uri.parse(uri), 320) ?: return@withContext null
        val tinyWidth = (source.width / 10).coerceAtLeast(12)
        val tinyHeight = (source.height / 10).coerceAtLeast(12)
        val tiny = Bitmap.createScaledBitmap(source, tinyWidth, tinyHeight, true)
        val targetWidth = source.width.coerceAtMost(480)
        val targetHeight = source.height.coerceAtMost(480)
        val blurred = Bitmap.createScaledBitmap(tiny, targetWidth, targetHeight, true)
        store(context, "blurred", uri, blurred, 320)
        blurred
    }

    suspend fun thumbnail(context: Context, uri: String?): Bitmap? = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        cached(context, "thumb", uri, 96)?.let { return@withContext it }
        val source = AudioBitmapLoader.loadAudioArtwork(context, Uri.parse(uri), 96) ?: return@withContext null
        val scale = 96f / maxOf(source.width, source.height).coerceAtLeast(1)
        val thumbnail = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else source
        store(context, "thumb", uri, thumbnail, 96)
        thumbnail
    }

    private fun cached(context: Context, variant: String, uri: String, targetEdge: Int): Bitmap? {
        val base = baseFile(context, variant, uri, targetEdge)
        val marker = File(base.parentFile, "${base.name}.uri")
        if (!base.isFile || !marker.isFile || marker.readText() != uri) return null
        return BitmapFactory.decodeFile(base.absolutePath)
    }

    private fun store(context: Context, variant: String, uri: String, bitmap: Bitmap, targetEdge: Int) {
        val base = baseFile(context, variant, uri, targetEdge)
        FileOutputStream(base).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(base.parentFile, "${base.name}.uri").writeText(uri)
    }

    private fun baseFile(context: Context, variant: String, uri: String, targetEdge: Int): File {
        val key = "${uri.hashCode().toUInt().toString(16)}_$targetEdge.png"
        return File(context.cacheDir, "widget_${variant}_$key")
    }
}
