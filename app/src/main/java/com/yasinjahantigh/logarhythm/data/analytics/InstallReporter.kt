package com.yasinjahantigh.logarhythm.data.analytics

import android.content.Context
import android.os.Build
import com.yasinjahantigh.logarhythm.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends one anonymous install record the first time the app runs, so installs from Telegram or
 * any other mirror are counted too. The record is stored once: app updates keep the same
 * preference, so updating never counts as another install. Also queries the total install count
 * across all devices and platforms.
 */
@Singleton
class InstallReporter @Inject constructor(
    @param:ApplicationContext context: Context,
    baseClient: OkHttpClient
) {

    private companion object {
        const val PREFS = "install_stats"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_REPORTED = "reported"
        const val KEY_CACHED_TOTAL = "cached_total_installs"
        const val MAX_FIELD = 48
        const val DEFAULT_HIT_ENDPOINT = "https://logarhythm-installs.yasinjahantigh.workers.dev/hit"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = baseClient.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val hitEndpoint: String = BuildConfig.INSTALL_ENDPOINT.takeIf { it.isNotBlank() } ?: DEFAULT_HIT_ENDPOINT
    val statsEndpoint: String = hitEndpoint.replace(Regex("""/hit/?$"""), "/stats")
    val isConfigured: Boolean = hitEndpoint.isNotBlank()

    private val _totalInstalls = MutableStateFlow<Long?>(
        prefs.getLong(KEY_CACHED_TOTAL, -1L).takeIf { it >= 0 }
    )
    val totalInstalls: StateFlow<Long?> = _totalInstalls.asStateFlow()

    // Robolectric unit tests build and run the real Application; without this guard every CI run
    // would report a few dozen fake installs.
    private val isTestRuntime: Boolean =
        Build.MODEL.orEmpty().contains("robolectric", ignoreCase = true) ||
            Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    fun reportOnce() {
        if (!isConfigured || isTestRuntime) return
        val reported = prefs.getBoolean(KEY_REPORTED, false)
        val installId = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }
        scope.launch {
            if (!reported) {
                val payload = JSONObject().apply {
                    put("installId", installId)
                    put("version", BuildConfig.VERSION_NAME)
                    put("code", BuildConfig.VERSION_CODE)
                    put("manufacturer", field(Build.MANUFACTURER))
                    put("model", field(Build.MODEL))
                    put("android", field(Build.VERSION.RELEASE))
                    put("sdk", Build.VERSION.SDK_INT)
                    put("abi", field(Build.SUPPORTED_ABIS.firstOrNull().orEmpty()))
                }
                val request = Request.Builder()
                    .url(hitEndpoint)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val delivered = runCatching {
                    client.newCall(request).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (delivered) prefs.edit().putBoolean(KEY_REPORTED, true).apply()
            }
            fetchTotalInstalls()
        }
    }

    suspend fun fetchTotalInstalls(): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(statsEndpoint)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    val count = json.optLong("installs", -1L).takeIf { it >= 0 }
                    if (count != null) {
                        prefs.edit().putLong(KEY_CACHED_TOTAL, count).apply()
                        _totalInstalls.value = count
                    }
                    count
                }
            }
        } catch (_: Exception) {
            _totalInstalls.value
        }
    }

    private fun field(value: String?): String =
        value.orEmpty().replace('\n', ' ').replace('\r', ' ').trim().take(MAX_FIELD)
}
