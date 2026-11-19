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
 * Sends an anonymous install / heartbeat ping (deduplicated by persistent installId)
 * so installs from GitHub, Telegram, APK shares, etc. are all counted accurately,
 * along with active user counts (without double-counting app updates or debug builds).
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
        const val KEY_LAST_PING_AT = "last_ping_at"
        const val KEY_CACHED_TOTAL = "cached_total_installs"
        const val HEARTBEAT_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val MAX_FIELD = 48
        const val DEFAULT_HIT_ENDPOINT = "https://logarhythm-installs.yasinjahantigh.workers.dev/hit"
    }

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        baseClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val hitEndpoint: String = BuildConfig.INSTALL_ENDPOINT.takeIf { it.isNotBlank() } ?: DEFAULT_HIT_ENDPOINT
    val statsEndpoint: String = hitEndpoint.replace(Regex("""/hit/?$"""), "/stats")
    val isConfigured: Boolean = hitEndpoint.isNotBlank()

    private val _totalInstalls = MutableStateFlow<Long?>(null)
    val totalInstalls: StateFlow<Long?> = _totalInstalls.asStateFlow()

    private val isTestRuntime: Boolean =
        Build.MODEL.orEmpty().contains("robolectric", ignoreCase = true) ||
            Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    fun reportOnce() {
        if (!isConfigured || isTestRuntime || BuildConfig.DEBUG) return
        scope.launch {
            val cached = prefs.getLong(KEY_CACHED_TOTAL, -1L).takeIf { it >= 0 }
            if (cached != null && _totalInstalls.value == null) {
                _totalInstalls.value = cached
            }
            val reported = prefs.getBoolean(KEY_REPORTED, false)
            val lastPingAt = prefs.getLong(KEY_LAST_PING_AT, 0L)
            val now = System.currentTimeMillis()
            val needsPing = !reported || (now - lastPingAt >= HEARTBEAT_INTERVAL_MS)

            val installId = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_INSTALL_ID, it).apply()
            }
            if (needsPing) {
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
                if (delivered) {
                    prefs.edit()
                        .putBoolean(KEY_REPORTED, true)
                        .putLong(KEY_LAST_PING_AT, now)
                        .apply()
                }
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
