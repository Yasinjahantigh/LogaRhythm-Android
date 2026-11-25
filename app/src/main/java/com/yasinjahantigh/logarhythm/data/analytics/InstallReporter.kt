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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal, transparent telemetry & user-initiated feedback client:
 * - One-time anonymous install ping (`installId`, `version`, `code`, `channel` — no hardware IDs or 24h heartbeats).
 * - Optional crash reports (`/crash`), shake bug reports (`/report`), Suggestions (`/feedback`), and weekly stats (`/listening-weekly`).
 */
@Singleton
class InstallReporter @Inject constructor(
    @param:ApplicationContext context: Context,
    baseClient: OkHttpClient
) {

    enum class DeliveryResult {
        DELIVERED,
        RETRY,
        REJECTED_PERMANENT
    }

    private companion object {
        const val PREFS = "install_stats"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_REPORTED = "reported"
        const val KEY_CACHED_TOTAL = "cached_total_installs"
        const val DEFAULT_HIT_ENDPOINT = "https://logarhythm-installs.yasinjahantigh.workers.dev/hit"
    }

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        baseClient.newBuilder()
            .callTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    val hitEndpoint: String = BuildConfig.INSTALL_ENDPOINT.takeIf { it.isNotBlank() } ?: DEFAULT_HIT_ENDPOINT
    val statsEndpoint: String = endpointFor("stats")
    val crashEndpoint: String = endpointFor("crash")
    val reportEndpoint: String = endpointFor("report")
    val feedbackEndpoint: String = endpointFor("feedback")
    val weeklyStatsEndpoint: String = endpointFor("listening-weekly")
    val isConfigured: Boolean = hitEndpoint.isNotBlank()

    private fun endpointFor(path: String): String =
        hitEndpoint.replace(Regex("""/hit/?$"""), "/$path")

    private val _totalInstalls = MutableStateFlow<Long?>(null)
    val totalInstalls: StateFlow<Long?> = _totalInstalls.asStateFlow()

    private val isTestRuntime: Boolean =
        Build.MODEL.orEmpty().contains("robolectric", ignoreCase = true) ||
            Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    fun getOrCreateInstallId(): String =
        prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }

    /** Sends a single minimal install ping once per installation (never repeated on a 24h timer). */
    fun reportOnce() {
        if (!isConfigured || isTestRuntime || BuildConfig.DEBUG) return
        scope.launch {
            val cached = prefs.getLong(KEY_CACHED_TOTAL, -1L).takeIf { it >= 0 }
            if (cached != null && _totalInstalls.value == null) {
                _totalInstalls.value = cached
            }
            val reported = prefs.getBoolean(KEY_REPORTED, false)
            if (!reported) {
                val installId = getOrCreateInstallId()
                val payload = JSONObject().apply {
                    put("installId", installId)
                    put("version", BuildConfig.VERSION_NAME)
                    put("code", BuildConfig.VERSION_CODE)
                    put("channel", BuildConfig.DISTRIBUTION_CHANNEL)
                }
                val delivered = postJson(hitEndpoint, payload) == DeliveryResult.DELIVERED
                if (delivered) {
                    prefs.edit().putBoolean(KEY_REPORTED, true).apply()
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

    suspend fun sendCrashReport(payload: JSONObject): DeliveryResult = withContext(Dispatchers.IO) {
        if (!isConfigured || isTestRuntime) return@withContext DeliveryResult.DELIVERED
        postJson(crashEndpoint, payload)
    }

    suspend fun sendUserReport(
        category: String,
        description: String,
        contact: String?,
        recentLogs: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("installId", getOrCreateInstallId())
            put("version", BuildConfig.VERSION_NAME)
            put("code", BuildConfig.VERSION_CODE)
            put("channel", BuildConfig.DISTRIBUTION_CHANNEL)
            put("category", category.trim().take(48))
            put("description", AppLogBuffer.sanitizeMultiline(description, 2500))
            if (!contact.isNullOrBlank()) put("contact", contact.trim().take(120))
            put("recentLogs", AppLogBuffer.sanitizeMultiline(recentLogs, 8000))
        }
        when (postJson(reportEndpoint, payload)) {
            DeliveryResult.DELIVERED -> Result.success(Unit)
            DeliveryResult.REJECTED_PERMANENT -> Result.failure(IllegalArgumentException("Invalid report payload"))
            DeliveryResult.RETRY -> Result.failure(IllegalStateException("Network unavailable"))
        }
    }

    suspend fun sendSuggestion(
        category: String,
        text: String,
        contact: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("installId", getOrCreateInstallId())
            put("version", BuildConfig.VERSION_NAME)
            put("code", BuildConfig.VERSION_CODE)
            put("channel", BuildConfig.DISTRIBUTION_CHANNEL)
            put("category", category.trim().take(48))
            put("text", AppLogBuffer.sanitizeMultiline(text, 2500))
            if (!contact.isNullOrBlank()) put("contact", contact.trim().take(120))
        }
        when (postJson(feedbackEndpoint, payload)) {
            DeliveryResult.DELIVERED -> Result.success(Unit)
            DeliveryResult.REJECTED_PERMANENT -> Result.failure(IllegalArgumentException("Invalid suggestion payload"))
            DeliveryResult.RETRY -> Result.failure(IllegalStateException("Network unavailable"))
        }
    }

    suspend fun sendWeeklyStats(
        week: String,
        totalPlays: Int,
        totalMinutes: Long,
        topTracks: List<Triple<String, String, Int>>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured || isTestRuntime) return@withContext true
        val tracksArray = JSONArray().apply {
            for ((title, artist, plays) in topTracks.take(5)) {
                put(JSONObject().apply {
                    put("title", title.take(60))
                    put("artist", artist.take(60))
                    put("plays", plays)
                })
            }
        }
        val payload = JSONObject().apply {
            put("week", week)
            put("installId", getOrCreateInstallId())
            put("version", BuildConfig.VERSION_NAME)
            put("channel", BuildConfig.DISTRIBUTION_CHANNEL)
            put("totalPlays", totalPlays)
            put("totalMinutes", totalMinutes)
            put("topTracks", tracksArray)
        }
        postJson(weeklyStatsEndpoint, payload) != DeliveryResult.RETRY
    }

    private fun postJson(url: String, payload: JSONObject): DeliveryResult {
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> DeliveryResult.DELIVERED
                    resp.code in 400..499 && resp.code != 429 -> DeliveryResult.REJECTED_PERMANENT
                    else -> DeliveryResult.RETRY
                }
            }
        } catch (_: Exception) {
            DeliveryResult.RETRY
        }
    }
}
