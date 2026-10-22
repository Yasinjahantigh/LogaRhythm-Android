package com.yasinjahantigh.logarhythm.data.download

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
        return output.toByteArray().takeIf(::isSupported)
    }

    private fun isSupported(bytes: ByteArray): Boolean {
        val jpeg = bytes.size > 3 &&
            (bytes[0].toInt() and 0xff) == 0xFF && (bytes[1].toInt() and 0xff) == 0xD8
        val png = bytes.size > 8 &&
            (bytes[0].toInt() and 0xff) == 0x89 &&
            bytes[1].toInt() == 'P'.code &&
            bytes[2].toInt() == 'N'.code &&
            bytes[3].toInt() == 'G'.code
        return jpeg || png
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
