package com.yasinjahantigh.logarhythm.data.analytics

import android.content.Context
import android.os.Build
import com.yasinjahantigh.logarhythm.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * preference, so updating never counts as another install. The random install id is the only
 * identifier; no account, library, file names or advertising id are involved, and nothing is
 * sent when the build has no endpoint configured.
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
        const val MAX_FIELD = 48
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = baseClient.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean = BuildConfig.INSTALL_ENDPOINT.isNotBlank()

    // Robolectric unit tests build and run the real Application; without this guard every CI run
    // would report a few dozen fake installs.
    private val isTestRuntime: Boolean =
        Build.MODEL.orEmpty().contains("robolectric", ignoreCase = true) ||
            Build.FINGERPRINT.orEmpty().contains("robolectric", ignoreCase = true)

    fun reportOnce() {
        if (!isConfigured || isTestRuntime) return
        if (prefs.getBoolean(KEY_REPORTED, false)) return
        val installId = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }
        scope.launch {
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
                .url(BuildConfig.INSTALL_ENDPOINT)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val delivered = runCatching {
                client.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (delivered) prefs.edit().putBoolean(KEY_REPORTED, true).apply()
        }
    }

    private fun field(value: String?): String =
        value.orEmpty().replace('\n', ' ').replace('\r', ' ').trim().take(MAX_FIELD)
}
