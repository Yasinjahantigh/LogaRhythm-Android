package com.yasinjahantigh.logarhythm.data.analytics

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import com.yasinjahantigh.logarhythm.BuildConfig
import com.yasinjahantigh.logarhythm.core.utils.PermissionUtils
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.DownloadDao
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.PlaylistDao
import com.yasinjahantigh.logarhythm.playback.AudioOutputManager
import com.yasinjahantigh.logarhythm.playback.PlayerController
import com.yasinjahantigh.logarhythm.ui.theme.normalizeFontKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures a sanitized, self-contained snapshot of live device, UI, typography, playback,
 * lyrics, library, and network state at the exact moment a user reports a bug or views logs —
 * so developers can diagnose visual/UX/state issues even when zero error logs were written.
 */
@Singleton
class DiagnosticSnapshotCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val playerController: PlayerController,
    private val audioOutputManager: AudioOutputManager,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val appLogBuffer: AppLogBuffer
) {

    suspend fun collectStateSnapshot(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder(2048)

        // 1. Device, Display, Locale, Memory, Network, Permissions
        val cfg = context.resources.configuration
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(memInfo) }
        val runtime = Runtime.getRuntime()
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val maxHeapMb = runtime.maxMemory() / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val nightMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val orientation = if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        val layoutDir = if (cfg.layoutDirection == View.LAYOUT_DIRECTION_RTL) "RTL" else "LTR"
        val appLocalePref = runCatching { preferencesManager.appLanguage.first() }.getOrDefault("?")
        val effectiveLocale = cfg.locales.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
            ?: Locale.getDefault().toLanguageTag()

        val netSummary = describeNetwork()
        val audioPerm = runCatching { PermissionUtils.hasAudioPermission(context) }.getOrDefault(false)
        val notifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        sb.appendLine("=== DEVICE & SYSTEM ===")
        sb.appendLine("App: v${BuildConfig.VERSION_NAME} (code=${BuildConfig.VERSION_CODE}, ${BuildConfig.DISTRIBUTION_CHANNEL}/${BuildConfig.BUILD_TYPE})")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) | Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | abi=${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
        sb.appendLine("Display: ${cfg.screenWidthDp}x${cfg.screenHeightDp}dp (${cfg.densityDpi}dpi, $orientation) | sysFontScale=${cfg.fontScale} | night=$nightMode")
        sb.appendLine("Locale: pref=$appLocalePref | active=$effectiveLocale | sys=${Locale.getDefault().toLanguageTag()} | dir=$layoutDir")
        sb.appendLine("Memory: heap=${usedHeapMb}/${maxHeapMb}MB | ram=${availRamMb}/${totalRamMb}MB (lowRam=${am?.isLowRamDevice == true})")
        sb.appendLine("Net/Perms: $netSummary | audioPerm=$audioPerm | notifPerm=$notifPerm")

        // 2. Navigation & Current Screen Context
        val ui = AppDiagnosticContext.snapshot()
        sb.appendLine("=== UI & NAVIGATION ===")
        sb.appendLine("Screen: route=${ui.currentRoute} | playerExpanded=${ui.playerExpanded} | queueOpen=${ui.queueOpen} | lyricsOpen=${ui.lyricsOpen}")
        if (ui.navTrail.isNotEmpty()) {
            sb.appendLine("NavTrail: ${ui.navTrail.joinToString(" -> ")}")
        }

        // 3. Theme & Typography State
        val theme = runCatching { preferencesManager.themePreferences.first() }.getOrNull()
        val vinylMode = runCatching { preferencesManager.vinylMode.first() }.getOrDefault(false)
        val compactMini = runCatching { preferencesManager.isCompactMiniPlayer.first() }.getOrDefault(false)
        val lyricsSpacing = runCatching { preferencesManager.lyricsLineSpacing.first() }.getOrDefault(12f)
        val translateLang = runCatching { preferencesManager.lyricsTranslateLang.first() }.getOrDefault("")
        val fontCheck = describeFontResolution(theme?.appFont, theme?.customFontPath)

        sb.appendLine("=== THEME & TYPOGRAPHY ===")
        if (theme != null) {
            sb.appendLine(
                "Theme: mode=${theme.themeMode} | style=${theme.themeStyle} | preset=${theme.themePresetId} | " +
                    "round=${theme.cornerRoundness} | customBg=${theme.customBackgroundUri != null} | compactMini=$compactMini | vinyl=$vinylMode"
            )
            sb.appendLine("Font: appFont=${theme.appFont} (norm=${normalizeFontKey(theme.appFont)}) | $fontCheck")
        }
        sb.appendLine("LyricsPrefs: lineSpacingDp=$lyricsSpacing | translateLang=${translateLang.ifBlank { "off" }}")

        // 4. Playback, Audio & Output State
        val track = playerController.currentTrack.value
        val queue = playerController.queue.value
        val qIdx = playerController.queueIndex.value
        val eq = playerController.equalizerState.value
        val out = audioOutputManager.state.value
        val replayGain = runCatching { preferencesManager.replayGainMode.first() }.getOrDefault("off")
        val fadeSec = runCatching { preferencesManager.fadeBetweenSongsSec.first() }.getOrDefault(0)
        val skipSilence = runCatching { preferencesManager.skipSilence.first() }.getOrDefault(false)

        sb.appendLine("=== PLAYBACK & AUDIO ===")
        sb.appendLine(
            "Playback: playing=${playerController.isPlaying.value} | pos=${playerController.position.value / 1000}s/${playerController.duration.value / 1000}s | " +
                "queue=${queue.size} (idx=$qIdx) | shuffle=${playerController.shuffle.value} | repeat=${playerController.repeatMode.value}"
        )
        if (track != null) {
            val ext = track.filePath.substringAfterLast('.', "").lowercase().ifBlank { "unknown" }
            sb.appendLine(
                "Track: id=${track.id} | src=${track.sourceType.name} | ext=$ext | dur=${track.durationMs}ms | " +
                    "art=${!track.artworkUri.isNullOrBlank()} | titleOk=${track.title.isNotBlank()} | artistOk=${track.artist.isNotBlank()} | albumOk=${track.album.isNotBlank()}"
            )
        } else {
            sb.appendLine("Track: none")
        }
        sb.appendLine(
            "Audio: speed=${playerController.playbackSpeed.value} | pitch=${playerController.playbackPitch.value} | " +
                "slowed=${playerController.slowedReverb.value} | eq=${eq.isEnabled}(preset=${eq.currentPreset}) | " +
                "rg=$replayGain | fade=${fadeSec}s | skipSilence=$skipSilence | output=${out.activeKind.name}(vol=${(out.volumeFraction * 100).toInt()}%)"
        )

        // 5. Lyrics, Library & Downloads State
        val lyrSrc = runCatching { preferencesManager.lyricsSourcePreferences.first() }.getOrNull()
        val trackCount = runCatching { musicDao.countTracks() }.getOrDefault(-1)
        val playlistCount = runCatching { playlistDao.countPlaylists() }.getOrDefault(-1)
        val hiddenCount = runCatching { preferencesManager.hiddenTrackIds.first().size }.getOrDefault(0)
        val excludedCount = runCatching { preferencesManager.excludedFolders.first().size }.getOrDefault(0)
        val whitelist = runCatching { preferencesManager.folderWhitelistMode.first() }.getOrDefault(false)
        val minLen = runCatching { preferencesManager.minTrackLengthSec.first() }.getOrDefault(30)
        val downloads = runCatching { downloadDao.getAllOnce() }.getOrDefault(emptyList())
        val activeDl = downloads.count { it.status in setOf("queued", "downloading", "converting", "saving") }
        val failedDl = downloads.filter { it.status == "failed" }
        val lastFail = failedDl.firstOrNull()

        sb.appendLine("=== LYRICS & LIBRARY ===")
        val l = ui.lyrics
        sb.appendLine(
            "LyricsState: loading=${l.loading} | has=${l.hasLyrics} | synced=${l.isSynced} | lines=${l.lineCount} | " +
                "src=${l.source ?: "none"} | hidden=${l.hidden} | mismatch=${l.mismatch} | translated=${l.translatedCount} | transFail=${l.translationFailed}"
        )
        if (lyrSrc != null) {
            sb.appendLine(
                "LyricsSources: lrclib=${lyrSrc.lrclib}, genius=${lyrSrc.genius}, ovh=${lyrSrc.lyricsOvh}, " +
                    "netease=${lyrSrc.netease}, kugou=${lyrSrc.kugou}, custom=${lyrSrc.customUsable}"
            )
        }
        sb.appendLine("Library: tracks=$trackCount | hidden=$hiddenCount | playlists=$playlistCount | minLen=${minLen}s | excluded=$excludedCount | whitelist=$whitelist")
        val lastFailStr = lastFail?.let {
            " | lastErr=${it.errorKind ?: "?"}:${AppLogBuffer.sanitize(it.errorMessage.orEmpty()).take(80)}"
        }.orEmpty()
        sb.appendLine("Downloads: total=${downloads.size} | active=$activeDl | failed=${failedDl.size}$lastFailStr")

        sb.toString().trim()
    }

    /**
     * Combines the live state snapshot + newest log tail within [maxChars] so bug reports
     * sent over the network always carry BOTH full context and recent error logs.
     */
    suspend fun buildReportLogsPayload(includeLogs: Boolean, maxChars: Int = 7800): String {
        val state = runCatching { collectStateSnapshot() }.getOrDefault("")
        if (!includeLogs) return ""
        val logBudget = (maxChars - state.length - 48).coerceAtLeast(1500)
        val logsTail = appLogBuffer.snapshotTail(logBudget)
        return buildString {
            if (state.isNotBlank()) {
                appendLine(state)
            }
            if (logsTail.isNotBlank()) {
                appendLine()
                appendLine("=== RECENT LOGS ===")
                append(logsTail)
            }
        }.trim().take(maxChars)
    }

    suspend fun buildFullDiagnosticReport(): String {
        val state = runCatching { collectStateSnapshot() }.getOrDefault("")
        val baseReport = appLogBuffer.buildDiagnosticReport(context)
        return if (state.isBlank()) baseReport else "$state\n\n$baseReport"
    }

    suspend fun shareFullDiagnosticLogFile(chooserTitle: String) {
        val fullReport = buildFullDiagnosticReport()
        appLogBuffer.shareDiagnosticTextAsFile(context, fullReport, chooserTitle)
    }

    private fun describeNetwork(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "net=unknown"
        val active = cm.activeNetwork ?: return "net=offline"
        val caps = cm.getNetworkCapabilities(active) ?: return "net=offline"
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "net=$kind(vpn=$vpn,ok=$validated)"
    }

    private fun describeFontResolution(appFont: String?, customFontPath: String?): String {
        val key = normalizeFontKey(appFont ?: "vazirmatn")
        if (key == "custom") {
            if (customFontPath.isNullOrBlank()) return "customFont=missing_path(fallback=system)"
            val file = File(customFontPath)
            return "customFont=${file.name}(exists=${file.isFile},read=${file.canRead()},bytes=${file.length()})"
        }
        val sdkNote = if (key in setOf("jakarta_vazir", "space_kufi", "playfair_naskh", "mono", "lalezar_jakarta") &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        ) {
            "paired_latin_only_sdk${Build.VERSION.SDK_INT}"
        } else {
            "ok_sdk${Build.VERSION.SDK_INT}"
        }
        return "customFont=none | fontStatus=$sdkNote"
    }
}
