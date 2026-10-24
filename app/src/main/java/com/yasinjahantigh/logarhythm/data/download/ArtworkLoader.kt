package com.yasinjahantigh.logarhythm.data.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.max

/** Fetches cover art for embedding; failures simply leave the file without artwork. */
@Singleton
class ArtworkLoader @Inject constructor(
    private val client: OkHttpClient
) {

    suspend fun load(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bytes = runCatching { readImage(response) }.getOrNull()
                        if (continuation.isActive) continuation.resume(bytes)
                    }
                }
            })
        }
    }

    private fun readImage(response: Response): ByteArray? {
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        if (body.contentLength() > MAX_BYTES) return null
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) return null
                output.write(buffer, 0, read)
            }
        }
        return normalizeCover(output.toByteArray())
    }

    /**
     * YouTube/SoundCloud serve WebP as often as JPEG. Re-encode everything as a JPEG no larger
     * than 1200 px so every tag engine and the FLAC/MP4 cover limits accept it.
     */
    private fun normalizeCover(bytes: ByteArray): ByteArray? {
        if (bytes.size < 16) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val largest = max(decoded.width, decoded.height)
        val scaled = if (largest > MAX_EDGE) {
            val ratio = MAX_EDGE.toFloat() / largest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else decoded
        if (scaled !== decoded) decoded.recycle()

        val flat = if (scaled.hasAlpha()) {
            Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also { canvasBitmap ->
                Canvas(canvasBitmap).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(scaled, 0f, 0f, null)
                }
            }
        } else scaled
        if (flat !== scaled) scaled.recycle()

        val output = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, 90, output)
        flat.recycle()
        return output.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        const val MAX_DECODE_EDGE = 2400
        const val MAX_EDGE = 1200
    }
}
