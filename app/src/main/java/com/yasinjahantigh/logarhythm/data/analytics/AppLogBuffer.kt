package com.yasinjahantigh.logarhythm.data.analytics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.yasinjahantigh.logarhythm.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent, rotating, sanitized diagnostic logger.
 *
 * Keeps a bounded in-memory ring buffer for instant display/crash snapshots AND appends every
 * entry (with date, timestamp, level, thread, tag, and optional exception summary/stack trace)
 * to a rotating log file under `filesDir/logs/` (`app.log` + `app.prev.log`, ~1 MB total)
 * so complete multi-session diagnostics can be viewed, copied, or exported as a `.log` file.
 */
@Singleton
class AppLogBuffer @Inject constructor() {

    enum class Level(val code: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    private val entries = ArrayDeque<String>(MAX_MEMORY_LINES)
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var logDir: File? = null

    fun initPersistence(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val dir = File(ctx.filesDir, LOG_DIR_NAME).apply { mkdirs() }
        synchronized(lock) {
            logDir = dir
            AppLog.attach(this)
            if (entries.isEmpty()) {
                val tail = readPersistedLines(limit = MAX_MEMORY_LINES)
                entries.addAll(tail)
            }
        }
    }

    fun log(tag: String, message: String, level: Level = Level.INFO, throwable: Throwable? = null) {
        val now = Date()
        val stamp = synchronized(STAMP_FORMAT) { STAMP_FORMAT.format(now) }
        val cleanTag = tag.replace(Regex("""[\r\n\t]"""), " ").trim().take(28).ifEmpty { "App" }
        val cleanMsg = sanitize(message).take(600)
        val threadName = Thread.currentThread().name.replace(Regex("""[\r\n\t]"""), "_").take(20)
        val errSuffix = if (throwable != null) {
            val type = throwable::class.java.simpleName.ifBlank { throwable::class.java.name }
            val errMsg = sanitize(throwable.message.orEmpty()).take(240)
            if (errMsg.isNotBlank()) " | $type: $errMsg" else " | $type"
        } else ""
        val line = "$stamp ${level.code}/$cleanTag [$threadName]: $cleanMsg$errSuffix"

        synchronized(lock) {
            appendMemoryLocked(line)
            appendDiskLocked(line)
            if (throwable != null && (level == Level.ERROR || level == Level.WARN)) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackLines = sanitizeMultiline(sw.toString(), maxChars = 2400)
                    .lineSequence()
                    .take(16)
                    .map { "    $it" }
                    .toList()
                for (stackLine in stackLines) {
                    appendMemoryLocked(stackLine)
                    appendDiskLocked(stackLine)
                }
            }
        }
    }

    fun recordCrash(threadName: String, throwable: Throwable) {
        val stamp = synchronized(STAMP_FORMAT) { STAMP_FORMAT.format(Date()) }
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sanitizeMultiline(sw.toString(), maxChars = 6000)
        val header = "$stamp E/CRASH [$threadName]: Uncaught ${throwable::class.java.name}: ${sanitize(throwable.message.orEmpty()).take(300)}"
        synchronized(lock) {
            appendMemoryLocked(header)
            appendDiskLocked(header)
            stack.lineSequence().forEach { stackLine ->
                val indented = "    $stackLine"
                appendMemoryLocked(indented)
                appendDiskLocked(indented)
            }
        }
    }

    private fun appendMemoryLocked(line: String) {
        if (entries.size >= MAX_MEMORY_LINES) {
            entries.removeFirst()
        }
        entries.addLast(line)
    }

    private fun appendDiskLocked(line: String) {
        val dir = logDir ?: return
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val current = File(dir, CURRENT_LOG_FILE)
            if (current.exists() && current.length() >= MAX_FILE_BYTES) {
                val prev = File(dir, PREV_LOG_FILE)
                if (prev.exists()) prev.delete()
                current.renameTo(prev)
            }
            current.appendText(line + "\n", Charsets.UTF_8)
        }
    }

    private fun readPersistedLines(limit: Int = Int.MAX_VALUE): List<String> {
        val dir = logDir ?: return emptyList()
        val prev = File(dir, PREV_LOG_FILE)
        val current = File(dir, CURRENT_LOG_FILE)
        val all = ArrayList<String>(256)
        runCatching {
            if (prev.isFile) prev.readLines(Charsets.UTF_8).let(all::addAll)
            if (current.isFile) current.readLines(Charsets.UTF_8).let(all::addAll)
        }
        return if (limit < all.size) all.takeLast(limit) else all
    }

    fun snapshot(maxLines: Int = MAX_MEMORY_LINES): String = synchronized(lock) {
        entries.takeLast(maxLines.coerceIn(1, MAX_MEMORY_LINES)).joinToString("\n")
    }

    /** Returns the most recent sanitized log lines fitting within [maxChars] (never cutting off the newest errors). */
    fun snapshotTail(maxChars: Int = 7500): String {
        val lines = synchronized(lock) {
            val persisted = readPersistedLines(limit = 400)
            if (persisted.isNotEmpty()) persisted else entries.toList()
        }
        if (lines.isEmpty()) return ""
        val selected = ArrayList<String>()
        var total = 0
        for (i in lines.lastIndex downTo 0) {
            val line = lines[i]
            val addLen = line.length + 1
            if (total + addLen > maxChars && selected.isNotEmpty()) break
            selected.add(line)
            total += addLen
        }
        selected.reverse()
        return selected.joinToString("\n").takeLast(maxChars)
    }

    fun lineCount(): Int = synchronized(lock) {
        val persistedCount = runCatching {
            val dir = logDir ?: return@runCatching 0
            var count = 0
            File(dir, PREV_LOG_FILE).takeIf { it.isFile }?.useLines { count += it.count() }
            File(dir, CURRENT_LOG_FILE).takeIf { it.isFile }?.useLines { count += it.count() }
            count
        }.getOrDefault(0)
        maxOf(entries.size, persistedCount)
    }

    fun totalLogSizeBytes(): Long = synchronized(lock) {
        val dir = logDir ?: return 0L
        val cur = File(dir, CURRENT_LOG_FILE).takeIf { it.isFile }?.length() ?: 0L
        val prev = File(dir, PREV_LOG_FILE).takeIf { it.isFile }?.length() ?: 0L
        cur + prev
    }

    fun clearLogs() {
        synchronized(lock) {
            entries.clear()
            val dir = logDir
            if (dir != null) {
                runCatching { File(dir, CURRENT_LOG_FILE).delete() }
                runCatching { File(dir, PREV_LOG_FILE).delete() }
            }
        }
        log("Logs", "Diagnostic log cleared by user", Level.INFO)
    }

    fun buildDiagnosticReport(context: Context): String {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(memInfo) }
        val runtime = Runtime.getRuntime()
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val maxHeapMb = runtime.maxMemory() / (1024L * 1024L)
        val sysAvailMb = memInfo.availMem / (1024L * 1024L)
        val sysTotalMb = memInfo.totalMem / (1024L * 1024L)
        val exportedAt = synchronized(STAMP_FORMAT) { STAMP_FORMAT.format(Date()) }

        val sb = StringBuilder(16_384)
        sb.appendLine("============================================================")
        sb.appendLine("LogaRhythm Diagnostic Log Report")
        sb.appendLine("============================================================")
        sb.appendLine("Exported At    : $exportedAt")
        sb.appendLine("App Version    : ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        sb.appendLine("Channel/Build  : ${BuildConfig.DISTRIBUTION_CHANNEL} / ${BuildConfig.BUILD_TYPE}")
        sb.appendLine("Device         : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        sb.appendLine("Android OS     : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABIs           : ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("Locale         : ${Locale.getDefault().toLanguageTag()}")
        sb.appendLine("App Heap       : ${usedHeapMb} MB / ${maxHeapMb} MB")
        if (sysTotalMb > 0L) {
            sb.appendLine("Device RAM     : ${sysAvailMb} MB free / ${sysTotalMb} MB total (lowRam=${am?.isLowRamDevice == true})")
        }
        sb.appendLine("------------------------------------------------------------")

        val pendingCrashes = CrashReportHandler.pendingDir(appCtx)
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (pendingCrashes.isNotEmpty()) {
            sb.appendLine("Pending Crash Files (${pendingCrashes.size}):")
            pendingCrashes.take(3).forEach { file ->
                sb.appendLine("  - ${file.name} (${file.length()} bytes)")
            }
            sb.appendLine("------------------------------------------------------------")
        }

        val lines = synchronized(lock) {
            val persisted = readPersistedLines()
            if (persisted.isNotEmpty()) persisted else entries.toList()
        }
        sb.appendLine("Log Entries (${lines.size} lines):")
        sb.appendLine()
        for (line in lines) {
            sb.appendLine(line)
        }
        return sb.toString()
    }

    fun exportDiagnosticLogFile(context: Context): Uri =
        exportDiagnosticTextAsFile(context, buildDiagnosticReport(context))

    fun exportDiagnosticTextAsFile(context: Context, content: String): Uri {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outFile = File(dir, "logarhythm-diagnostics-$fileStamp.log")
        outFile.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    fun shareDiagnosticLogFile(context: Context, chooserTitle: String) {
        shareDiagnosticTextAsFile(context, buildDiagnosticReport(context), chooserTitle)
    }

    fun shareDiagnosticTextAsFile(context: Context, content: String, chooserTitle: String) {
        val uri = exportDiagnosticTextAsFile(context, content)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LogaRhythm Diagnostics (${BuildConfig.VERSION_NAME})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    companion object {
        private const val LOG_DIR_NAME = "logs"
        private const val CURRENT_LOG_FILE = "app.log"
        private const val PREV_LOG_FILE = "app.prev.log"
        private const val MAX_FILE_BYTES = 512L * 1024L
        private const val MAX_MEMORY_LINES = 600
        private val STAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        private val SENSITIVE_PATTERNS = listOf(
            Regex("""(?i)\bauthorization\s*:\s*bearer\s+[^\s,;]+"""),
            Regex("""(?i)\bbearer\s+[A-Za-z0-9\-._~+/]{8,}=*"""),
            Regex("""(?i)(authorization|bearer|x-api-key|api[_-]?key|token|cookie|set-cookie)\s*[:=]\s*[^\s,;]+"""),
            Regex("""AIza[0-9A-Za-z\-_]{20,}"""),
            Regex("""sk-[0-9A-Za-z\-_]{16,}"""),
            Regex("""[0-9]{8,12}:[A-Za-z0-9_-]{30,}""")
        )
        private val PATH_PATTERN = Regex("""/storage/emulated/\d+/[^\s"']+""")

        fun sanitize(raw: String): String {
            var text = raw.replace('\r', ' ').replace('\n', ' ')
            for (regex in SENSITIVE_PATTERNS) {
                text = text.replace(regex) { "${it.groupValues.firstOrNull()?.substringBefore('=')?.substringBefore(':') ?: "secret"}=[REDACTED]" }
            }
            text = text.replace(PATH_PATTERN, "/storage/[MEDIA_PATH]")
            return text.trim()
        }

        fun sanitizeMultiline(raw: String, maxChars: Int = 6000): String {
            var text = raw.replace("\r\n", "\n")
            for (regex in SENSITIVE_PATTERNS) {
                text = text.replace(regex, "[REDACTED]")
            }
            text = text.replace(PATH_PATTERN, "/storage/[MEDIA_PATH]")
            return text.trim().take(maxChars)
        }
    }
}

/**
 * Lightweight global bridge so non-Hilt classes, services, workers, and static helpers can write
 * directly to [AppLogBuffer] (and Logcat) without extra constructor wiring.
 */
object AppLog {
    @Volatile
    private var buffer: AppLogBuffer? = null

    fun attach(instance: AppLogBuffer) {
        buffer = instance
    }

    fun d(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
        buffer?.log(tag, message, AppLogBuffer.Level.DEBUG)
    }

    fun i(tag: String, message: String) {
        runCatching { Log.i(tag, message) }
        buffer?.log(tag, message, AppLogBuffer.Level.INFO)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }
        buffer?.log(tag, message, AppLogBuffer.Level.WARN, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
        buffer?.log(tag, message, AppLogBuffer.Level.ERROR, throwable)
    }
}
