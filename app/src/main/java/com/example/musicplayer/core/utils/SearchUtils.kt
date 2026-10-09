package com.example.musicplayer.core.utils

/**
 * Utility functions for search queries, including Persian character normalization.
 */
object SearchUtils {
    /**
     * Normalizes Arabic/Persian letter variants (e.g. Arabic Yeh to Persian Yeh, Arabic Kaf to Persian Keheh)
     * and strips Arabic diacritics (harakat) for consistent search matching.
     */
    fun normalize(input: String): String {
        return input.trim()
            .replace('\u064A', '\u06CC') // Arabic Yeh (ي) -> Persian Yeh (ی)
            .replace('\u0649', '\u06CC') // Alef Maksura (ى) -> Persian Yeh (ی)
            .replace('\u0643', '\u06A9') // Arabic Kaf (ك) -> Persian Keheh (ک)
            .replace("\u064E", "") // Fat-ha
            .replace("\u064F", "") // Damma
            .replace("\u0650", "") // Kasra
            .replace("\u0651", "") // Shadda
            .replace("\u0652", "") // Sukun
    }
}
