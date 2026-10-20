package com.yasinjahantigh.logarhythm.domain.lyrics

import com.yasinjahantigh.logarhythm.domain.model.LyricLine
import com.yasinjahantigh.logarhythm.domain.model.LyricWord
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import java.util.regex.Pattern

/**
 * Standard LRC parser supporting:
 * - Timestamps: [mm:ss.xx], [mm:ss.xxx], [mm:ss]
 * - Multiple timestamps per line: [00:12.00][00:24.00]Lyric line
 * - Offset tag: [offset:+/-millis]
 * - Enhanced LRC word timing: [00:12.00]<00:12.00>Hello <00:12.50>world
 * - Metadata tag exclusion ([ti:], [ar:], [al:], etc.)
 * - Unsynced plain text fallback
 */
object LrcParser {
    private val TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")
    private val OFFSET_PATTERN = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*\\]", Pattern.CASE_INSENSITIVE)
    private val WORD_TAG_PATTERN = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>")
    private val METADATA_TAG_PATTERN = Pattern.compile("^\\[[a-zA-Z]{1,10}:.*\\]")

    fun parse(content: String?): Lyrics {
        if (content.isNullOrBlank()) {
            return Lyrics()
        }

        val cleaned = content
            .replace("\uFEFF", "")
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val rawLines = cleaned.lines()
        var offsetMs = 0L

        // Pass 1: Extract global [offset:xxx] tag if present
        for (line in rawLines) {
            val trimmed = line.trim()
            val offsetMatcher = OFFSET_PATTERN.matcher(trimmed)
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                break
            }
        }

        val parsedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        // Pass 2: Extract timestamped and plain lines
        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Skip offset tag line itself
            if (OFFSET_PATTERN.matcher(trimmed).matches()) continue

            val matcher = TIMESTAMP_PATTERN.matcher(trimmed)
            val timestamps = mutableListOf<Long>()

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3)
                val frac = when (fracStr?.length) {
                    1 -> fracStr.toLong() * 100L
                    2 -> fracStr.toLong() * 10L
                    3 -> fracStr.toLong()
                    else -> 0L
                }
                val rawTime = min * 60_000L + sec * 1_000L + frac
                val finalTime = (rawTime + offsetMs).coerceAtLeast(0L)
                timestamps.add(finalTime)
            }

            if (timestamps.isNotEmpty()) {
                val body = TIMESTAMP_PATTERN.matcher(trimmed).replaceAll("").trim()
                val words = parseWords(body, offsetMs)
                val text = if (words.isEmpty()) body else WORD_TAG_PATTERN.matcher(body).replaceAll("").trim()
                for (time in timestamps) {
                    val shift = time - timestamps.first()
                    parsedLines.add(
                        LyricLine(
                            timestampMs = time,
                            text = text,
                            words = if (shift == 0L) words else words.map { it.copy(startMs = it.startMs + shift) }
                        )
                    )
                }
            } else {
                // If not a metadata tag (e.g. [ar:Singer]), preserve as plain lyrics
                if (!METADATA_TAG_PATTERN.matcher(trimmed).matches()) {
                    plainLines.add(trimmed)
                }
            }
        }

        val sortedLines = parsedLines.sortedBy { it.timestampMs }
        val isSynced = sortedLines.isNotEmpty()
        val plainText = if (isSynced) {
            sortedLines.joinToString("\n") { it.text }
        } else {
            plainLines.joinToString("\n")
        }

        return Lyrics(
            lines = sortedLines,
            plainText = plainText.ifBlank { null },
            isSynced = isSynced,
            offsetMs = offsetMs
        )
    }

    /** Splits "<t>word <t>word" into timed words; empty when the line has no word tags. */
    private fun parseWords(body: String, offsetMs: Long): List<LyricWord> {
        val matcher = WORD_TAG_PATTERN.matcher(body)
        val starts = mutableListOf<Triple<Long, Int, Int>>() // time, tag start, tag end
        while (matcher.find()) {
            val min = matcher.group(1)?.toLongOrNull() ?: 0L
            val sec = matcher.group(2)?.toLongOrNull() ?: 0L
            val frac = when (matcher.group(3)?.length) {
                1 -> matcher.group(3)!!.toLong() * 100L
                2 -> matcher.group(3)!!.toLong() * 10L
                3 -> matcher.group(3)!!.toLong()
                else -> 0L
            }
            starts += Triple((min * 60_000L + sec * 1_000L + frac + offsetMs).coerceAtLeast(0L), matcher.start(), matcher.end())
        }
        if (starts.isEmpty()) return emptyList()
        val words = mutableListOf<LyricWord>()
        for (i in starts.indices) {
            val end = if (i + 1 < starts.size) starts[i + 1].second else body.length
            val text = body.substring(starts[i].third, end)
            if (text.isNotBlank()) words += LyricWord(starts[i].first, text)
        }
        return words
    }
}
