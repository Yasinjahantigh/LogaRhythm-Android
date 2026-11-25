package com.yasinjahantigh.logarhythm.data.analytics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounded in-memory ring buffer of sanitized diagnostic breadcrumbs (no PII, tokens, or cookies)
 * included in crash reports and shake-to-report bug submissions.
 */
@Singleton
class AppLogBuffer @Inject constructor() {

    private val entries = ArrayDeque<String>(MAX_LINES)
    private val lock = Any()

    fun log(tag: String, message: String) {
        val stamp = synchronized(TIME_FORMAT) { TIME_FORMAT.format(Date()) }
        val cleanTag = tag.replace(Regex("""[\r\n\t]"""), " ").take(28)
        val cleanMsg = sanitize(message).take(220)
        val line = "$stamp [$cleanTag] $cleanMsg"
        synchronized(lock) {
            if (entries.size >= MAX_LINES) {
                entries.removeFirst()
            }
            entries.addLast(line)
        }
    }

    fun snapshot(maxLines: Int = MAX_LINES): String = synchronized(lock) {
        entries.takeLast(maxLines.coerceIn(1, MAX_LINES)).joinToString("\n")
    }

    fun lineCount(): Int = synchronized(lock) { entries.size }

    companion object {
        private const val MAX_LINES = 120
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        private val SENSITIVE_PATTERNS = listOf(
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
