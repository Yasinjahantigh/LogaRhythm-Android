package com.example.musicplayer.core.utils

/** Utility functions for search queries, including Arabic/Persian character normalization. */
object SearchUtils {
    /** Maps common Arabic glyph variants to Persian forms and removes Arabic harakat. */
    fun normalize(input: String): String = input.trim()
        .replace('\u064A', '\u06CC') // Arabic Yeh (ي) -> Persian Yeh (ی)
        .replace('\u0649', '\u06CC') // Alef Maksura (ى) -> Persian Yeh (ی)
        .replace('\u0643', '\u06A9') // Arabic Kaf (ك) -> Persian Keheh (ک)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("\\s+"), " ")
}
