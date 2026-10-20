package com.yasinjahantigh.logarhythm.ui.components

import java.text.NumberFormat

/**
 * Returns the metadata value, or null when it is blank or one of the placeholders MediaStore and
 * tag readers like to invent ("<unknown>", "null", "Unknown Artist" ...). Callers substitute a
 * localized "Unknown ..." string, so placeholders never leak into the UI in the wrong language.
 */
fun String?.cleanMetaOrNull(): String? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when (value.lowercase()) {
        "<unknown>", "unknown", "null", "unknown artist", "unknown album", "unknown genre" -> null
        else -> value
    }
}

/** Locale-aware integer formatting (Persian digits in the Persian UI, grouping separators). */
fun formatCount(count: Int): String = NumberFormat.getInstance().format(count)
