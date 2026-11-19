package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts dominant vibrant color from album artwork offline for dynamic UI theming.
 * Caches results in memory to avoid repeated bitmap processing and decoding overhead.
 */
object ArtworkColorExtractor {

    // Fast memory cache for extracted artwork colors (key: artworkUri, value: Color 64-bit ULARGE)
    private val colorCache = LruCache<String, Long>(64)

    suspend fun extractDominantColor(
        context: Context,
        artworkUri: String?,
        fallbackColor: Color = Color(0xFF006A60)
    ): Color = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) return@withContext fallbackColor

        synchronized(colorCache) {
            val cached = colorCache.get(artworkUri)
            if (cached != null) {
                return@withContext Color(cached.toULong())
            }
        }

        try {
            // Coil's application-scoped loader reuses memory/disk caches and networking.
            val imageLoader = Coil.imageLoader(context.applicationContext)
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .size(64, 64)
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.drawable.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val color = extractVibrantColorFromBitmap(bitmap, fallbackColor)
                synchronized(colorCache) {
                    colorCache.put(artworkUri, color.value.toLong())
                }
                color
            } else {
                fallbackColor
            }
        } catch (_: Exception) {
            fallbackColor
        }
    }

    fun extractVibrantColorFromBitmap(bitmap: Bitmap, fallbackColor: Color): Color {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return fallbackColor

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var bestR = -1
        var bestG = 0
        var bestB = 0
        var highestScore = -1f

        val step = max(1, min(width, height) / 16)
        val inv255 = 1f / 255f

        var y = 0
        while (y < height) {
            val rowOffset = y * width
            var x = 0
            while (x < width) {
                val pixel = pixels[rowOffset + x]
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha >= 128) {
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF

                    val maxInt = max(r, max(g, b))
                    val minInt = min(r, min(g, b))
                    if (maxInt > 0) {
                        val delta = maxInt - minInt
                        val saturation = delta.toFloat() / maxInt.toFloat()
                        if (saturation > 0.15f) {
                            val lightness = (maxInt + minInt) * (0.5f * inv255)
                            if (lightness in 0.15f..0.85f) {
                                val score = saturation * (1f - kotlin.math.abs(lightness - 0.5f))
                                if (score > highestScore) {
                                    highestScore = score
                                    bestR = r
                                    bestG = g
                                    bestB = b
                                }
                            }
                        }
                    }
                }
                x += step
            }
            y += step
        }

        return if (bestR >= 0) Color(bestR, bestG, bestB) else fallbackColor
    }
}
