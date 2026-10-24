package com.yasinjahantigh.logarhythm.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object CoverBitmap {
    private const val DECODE_EDGE = 2048
    private const val OUTPUT_EDGE = 1024

    suspend fun decode(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return@withContext decoded
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Source rectangle currently visible inside the square crop viewport. */
    fun visibleRect(source: Bitmap, containerPx: Float, zoom: Float, offsetX: Float, offsetY: Float): Rect {
        val baseScale = max(containerPx / source.width, containerPx / source.height)
        val scale = baseScale * zoom
        val drawnWidth = source.width * scale
        val drawnHeight = source.height * scale
        val left = (containerPx - drawnWidth) / 2f + offsetX
        val top = (containerPx - drawnHeight) / 2f + offsetY
        val size = (containerPx / scale).roundToInt().coerceAtLeast(1)
        val x = (-left / scale).roundToInt().coerceIn(0, (source.width - size).coerceAtLeast(0))
        val y = (-top / scale).roundToInt().coerceIn(0, (source.height - size).coerceAtLeast(0))
        return Rect(x, y, size)
    }

    fun cropToSquare(source: Bitmap, containerPx: Float, zoom: Float, offsetX: Float, offsetY: Float): ByteArray? {
        return try {
            val rect = visibleRect(source, containerPx, zoom, offsetX, offsetY)
            val cropped = Bitmap.createBitmap(source, rect.x, rect.y, rect.size, rect.size)
            val scaled = Bitmap.createScaledBitmap(cropped, OUTPUT_EDGE, OUTPUT_EDGE, true)
            if (scaled !== cropped) cropped.recycle()
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            scaled.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun maxOffset(source: Bitmap, containerPx: Float, zoom: Float): Pair<Float, Float> {
        val baseScale = max(containerPx / source.width, containerPx / source.height)
        val scale = baseScale * zoom
        val maxX = ((source.width * scale - containerPx) / 2f).coerceAtLeast(0f)
        val maxY = ((source.height * scale - containerPx) / 2f).coerceAtLeast(0f)
        return maxX to maxY
    }

    class Rect(val x: Int, val y: Int, val size: Int)
}
