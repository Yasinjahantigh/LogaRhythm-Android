package com.example.musicplayer.core.utils

import android.content.Context
import android.graphics.Bitmap
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
 */
object ArtworkColorExtractor {

    suspend fun extractDominantColor(
        context: Context,
        artworkUri: String?,
        fallbackColor: Color = Color(0xFF006A60)
    ): Color = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) return@withContext fallbackColor
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
                extractVibrantColorFromBitmap(bitmap, fallbackColor)
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

        var bestColor = fallbackColor
        var highestScore = -1f

        val step = max(1, min(width, height) / 16)

        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel shr 24) and 0xFF
                if (alpha < 128) continue

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val rf = r / 255f
                val gf = g / 255f
                val bf = b / 255f

                val maxC = max(rf, max(gf, bf))
                val minC = min(rf, min(gf, bf))
                val delta = maxC - minC

                val saturation = if (maxC == 0f) 0f else delta / maxC
                val lightness = (maxC + minC) / 2f

                // Prefer saturated colors with balanced lightness
                if (saturation > 0.15f && lightness in 0.15f..0.85f) {
                    val score = saturation * (1f - kotlin.math.abs(lightness - 0.5f))
                    if (score > highestScore) {
                        highestScore = score
                        bestColor = Color(r, g, b)
                    }
                }
            }
        }

        return bestColor
    }
}
