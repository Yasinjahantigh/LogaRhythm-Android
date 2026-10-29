package com.yasinjahantigh.logarhythm.data.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.yasinjahantigh.logarhythm.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the public GitHub release feed and installs the split APK that matches the device ABI.
 * The updater intentionally has no server of its own: the release page is the source of truth,
 * so a tampered app cannot invent versions and Android still enforces the signing key.
 */
@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext context: Context,
    baseClient: OkHttpClient
) {

    private companion object {
        const val API_URL =
            "https://api.github.com/repos/Yasinjahantigh/LogaRhythm-Android/releases/latest"
        const val PREFS = "update_prefs"
        const val KEY_SKIPPED_VERSION = "skipped_version"
        const val KEY_LAST_AUTO_CHECK = "last_auto_check"
        const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val VERSION_TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = baseClient.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val checking = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)

    private val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    val installedVersion: String = packageInfo.versionName.orEmpty()
    private val installedVersionCode: Int =
        PackageInfoCompat.getLongVersionCode(packageInfo).toInt()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _lastRelease = MutableStateFlow<AppRelease?>(null)
    val lastRelease: StateFlow<AppRelease?> = _lastRelease.asStateFlow()

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    private val _autoCheckEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true))
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_ENABLED, enabled).apply()
        _autoCheckEnabled.value = enabled
    }

    /** Throttled check that runs when the app opens; errors stay silent. */
    fun autoCheck() {
        if (!_autoCheckEnabled.value) return
        val last = prefs.getLong(KEY_LAST_AUTO_CHECK, 0L)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (!checking.compareAndSet(false, true)) return
        if (!silent) _state.value = UpdateState.Checking
        scope.launch {
            try {
                val release = runCatching { fetchLatestRelease() }.getOrNull()
                if (release == null) {
                    _state.value = UpdateState.Hidden
                    if (!silent) _messages.emit(R.string.update_check_failed)
                    return@launch
                }
                _lastRelease.value = release
                if (silent) {
                    prefs.edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply()
                }
                when {
                    release.versionCode <= installedVersionCode -> {
                        _state.value = UpdateState.Hidden
                        if (!silent) _messages.emit(R.string.update_up_to_date)
                    }
                    silent && prefs.getString(KEY_SKIPPED_VERSION, null) == release.versionName ->
                        _state.value = UpdateState.Hidden
                    else -> _state.value = UpdateState.Available(release)
                }
            } finally {
                checking.set(false)
            }
        }
    }

    fun download() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        } ?: return
        if (!downloading.compareAndSet(false, true)) return
        _state.value = UpdateState.Downloading(release, -1f)
        scope.launch {
            val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, release.apkName)
            try {
                fetchApk(release, dir, target)
                _state.value = UpdateState.Hidden
                install(target)
            } catch (_: Exception) {
                target.delete()
                _state.value = UpdateState.Failed(release)
            } finally {
                downloading.set(false)
            }
        }
    }

    fun dismiss() {
        val available = _state.value as? UpdateState.Available
        if (available != null) {
            prefs.edit().putString(KEY_SKIPPED_VERSION, available.release.versionName).apply()
        }
        _state.value = UpdateState.Hidden
    }

    private fun fetchApk(release: AppRelease, dir: File, target: File) {
        val response = client.newCall(Request.Builder().url(release.apkUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            val body = it.body ?: throw IOException("empty body")
            val total = body.contentLength().takeIf { length -> length > 0 } ?: release.apkSizeBytes
            dir.listFiles()?.forEach { old -> if (old != target) old.delete() }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = if (total > 0) (copied * 100 / total).toInt() else -1
                        if (percent != lastPercent && percent % 2 == 0) {
                            lastPercent = percent
                            _state.value = UpdateState.Downloading(
                                release,
                                if (percent >= 0) percent / 100f else -1f
                            )
                        }
                    }
                }
            }
            if (total > 0 && target.length() != total) throw IOException("truncated download")
        }
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent) }.isSuccess
        if (!launched) {
            _state.value = UpdateState.Failed(_lastRelease.value)
            scope.launch { _messages.emit(R.string.update_install_failed) }
        }
    }

    private fun fetchLatestRelease(): AppRelease? {
        val request = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val match = VERSION_TAG.matchEntire(tag.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            val versionCode = major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
            val versionName = tag.trim().removePrefix("v")

            val assets = json.optJSONArray("assets") ?: JSONArray()
            val apks = ArrayList<ApkAsset>()
            var downloads = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
                downloads += asset.optLong("download_count")
                apks += ApkAsset(name, url, asset.optLong("size"))
            }
            if (apks.isEmpty()) return null

            val abi = deviceAbi()
            val apk = apks.firstOrNull { it.name.endsWith("-$abi.apk") }
                ?: apks.maxByOrNull { it.size }
                ?: return null
            return AppRelease(
                versionName = versionName,
                versionCode = versionCode,
                notes = json.optString("body").trim(),
                apkUrl = apk.url,
                apkName = apk.name,
                apkSizeBytes = apk.size,
                abi = abi,
                downloads = downloads
            )
        }
    }

    private fun deviceAbi(): String {
        val supported = Build.SUPPORTED_ABIS
        return supported.firstOrNull { it in KNOWN_ABIS } ?: supported.firstOrNull() ?: "arm64-v8a"
    }

    private data class ApkAsset(val name: String, val url: String, val size: Long)
}
