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

    suspend fun cover(context: Context, uri: String?, edge: Int = 320): Bitmap? = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        cached(context, "cover", uri, edge)?.let { return@withContext it }
        val source = AudioBitmapLoader.loadAudioArtwork(context, Uri.parse(uri), edge) ?: return@withContext null
        val scaled = fit(source, edge)
        store(context, "cover", uri, scaled, edge)
        scaled
    }

    /**
     * Soft focus backdrop: a heavy downsample followed by a bilinear upscale rebuilds smooth
     * gradients without the cost of a real blur. RGB_565 keeps the bitmap small enough for the
     * RemoteViews binder limit while staying visually clean under the dark scrim.
     */
    suspend fun blurred(context: Context, uri: String?, edge: Int = 480): Bitmap? = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        cached(context, "blurred", uri, edge, Bitmap.Config.RGB_565)?.let { return@withContext it }
        val source = AudioBitmapLoader.loadAudioArtwork(context, Uri.parse(uri), edge) ?: return@withContext null
        val base = fit(source, edge)
        val tiny = Bitmap.createScaledBitmap(
            base,
            (base.width / 4).coerceAtLeast(8),
            (base.height / 4).coerceAtLeast(8),
            true
        )
        val blurred = Bitmap.createScaledBitmap(tiny, base.width, base.height, true)
            .copy(Bitmap.Config.RGB_565, false)
        store(context, "blurred", uri, blurred, edge)
        blurred
    }

    suspend fun thumbnail(context: Context, uri: String?, edge: Int = 96): Bitmap? = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        cached(context, "thumb", uri, edge)?.let { return@withContext it }
        val source = AudioBitmapLoader.loadAudioArtwork(context, Uri.parse(uri), edge) ?: return@withContext null
        val scaled = fit(source, edge)
        store(context, "thumb", uri, scaled, edge)
        scaled
    }

    private fun fit(source: Bitmap, edge: Int): Bitmap {
        val longest = maxOf(source.width, source.height).coerceAtLeast(1)
        if (longest <= edge) return source
        val scale = edge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun cached(
        context: Context,
        variant: String,
        uri: String,
        targetEdge: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        val base = baseFile(context, variant, uri, targetEdge)
        val marker = File(base.parentFile, "${base.name}.uri")
        if (!base.isFile || !marker.isFile || marker.readText() != uri) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = config }
        return BitmapFactory.decodeFile(base.absolutePath, options)
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
