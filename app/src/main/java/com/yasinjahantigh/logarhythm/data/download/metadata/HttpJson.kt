package com.yasinjahantigh.logarhythm.data.download.metadata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Small JSON GET helper with a short retry for transient API failures. */
internal object HttpJson {

    suspend fun get(client: OkHttpClient, url: String): JSONObject? = withContext(Dispatchers.IO) {
        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        return@withContext runCatching { JSONObject(body) }.getOrNull()
                    }
                    if (response.code != 429 && response.code < 500) return@withContext null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // Retry below.
            }
            attempt++
            if (attempt < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * attempt)
        }
        null
    }

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 400L
}
