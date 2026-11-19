package com.yasinjahantigh.logarhythm.ui.components

import java.text.NumberFormat
import java.util.Locale

private val WHITESPACE_REGEX = Regex("\\s+")

private val PLACEHOLDER_KEYS = hashSetOf(
    "<unknown>", "unknown", "null", "n/a", "na", "none", "not available",
    "unknown title", "unknown artist", "unknown album", "unknown genre"
)

private val cachedNumberFormat = object : ThreadLocal<Pair<Locale, NumberFormat>>() {}

/**
 * Returns the metadata value, or null when it is blank or one of the placeholders MediaStore and
 * tag readers like to invent ("<unknown>", "null", "Unknown Artist" ...). Callers substitute a
 * localized "Unknown ..." string, so placeholders never leak into the UI in the wrong language.
 */
fun String?.cleanMetaOrNull(): String? {
    if (this.isNullOrEmpty()) return null
    val len = this.length

    // Fast path: check if whitespace normalization or surrounding quote stripping is even needed.
    var needsWhitespaceFix = false
    var prevSpace = false
    var allTrimChars = true
    for (i in 0 until len) {
        val ch = this[i]
        if (ch <= ' ') {
            if (ch != ' ' || i == 0 || i == len - 1 || prevSpace) {
                needsWhitespaceFix = true
            }
            prevSpace = true
        } else {
            prevSpace = false
            if (ch != '-' && ch != '_' && ch != '.') {
                allTrimChars = false
            }
        }
    }
    if (allTrimChars) return null

    var value = if (needsWhitespaceFix) {
        this.replace(WHITESPACE_REGEX, " ").trim()
    } else {
        this
    }
    if (value.length >= 2 && value[0] == '"' && value[value.length - 1] == '"') {
        value = value.substring(1, value.length - 1).trim()
    }
    if (value.isEmpty()) return null

    // Every placeholder in PLACEHOLDER_KEYS starts with '<', 'u', 'n' (case-insensitive) after
    // trimming leading spaces/'-'/'_'/'/'., and has length <= 14.
    if (value.length <= 20) {
        val key = value.lowercase(Locale.ROOT).trim(' ', '-', '_', '.')
        if (key.isEmpty() || key in PLACEHOLDER_KEYS) return null
    }
    return value
}

/** Locale-aware integer formatting (Persian digits in the Persian UI, grouping separators). */
fun formatCount(count: Int): String {
    val locale = Locale.getDefault()
    val cached = cachedNumberFormat.get()
    val formatter = if (cached != null && cached.first == locale) {
        cached.second
    } else {
        NumberFormat.getInstance(locale).also { cachedNumberFormat.set(locale to it) }
    }
    return formatter.format(count)
}
