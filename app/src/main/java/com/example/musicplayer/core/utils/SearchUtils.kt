package com.example.musicplayer.core.utils

import java.util.Locale

/** Utility functions for search queries, including Arabic/Persian character normalization and fuzzy token matching. */
object SearchUtils {
    /** Maps common Arabic glyph variants to Persian forms, strips harakat/diacritics, ZWNJ, and normalizes alefs. */
    fun normalize(input: String): String = input.trim()
        .replace('\u064A', '\u06CC') // Arabic Yeh (ي) -> Persian Yeh (ی)
        .replace('\u0649', '\u06CC') // Alef Maksura (ى) -> Persian Yeh (ی)
        .replace('\u0626', '\u06CC') // Yeh with Hamza (ئ) -> Persian Yeh (ی)
        .replace('\u0643', '\u06A9') // Arabic Kaf (ك) -> Persian Keheh (ک)
        .replace('\u0622', '\u0627') // Alef with Madda (آ) -> Alef (ا)
        .replace('\u0623', '\u0627') // Alef with Hamza Above (أ) -> Alef (ا)
        .replace('\u0625', '\u0627') // Alef with Hamza Below (إ) -> Alef (ا)
        .replace('\u0671', '\u0627') // Alef Wasla (ٱ) -> Alef (ا)
        .replace('\u0629', '\u0647') // Teh Marbuta (ة) -> Heh (ه)
        .replace('\u06C0', '\u0647') // Heh with Yeh Above (ۀ) -> Heh (ه)
        .replace('\u200C', ' ')       // Persian ZWNJ (نیم‌فاصله) -> space
        .replace('\u00A0', ' ')       // Non-breaking space -> space
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // Arabic harakat / diacritics
        .replace(Regex("[\\p{Punct}&&[^']]+"), " ")    // Punctuation to space
        .replace(Regex("\\s+"), " ")

    /** Checks if all tokens in query exist in haystack (case-insensitive, normalized). */
    fun matchesTokens(haystack: String, query: String): Boolean {
        val cleanQuery = normalize(query).lowercase(Locale.ROOT)
        if (cleanQuery.isBlank()) return true
        val cleanHaystack = normalize(haystack).lowercase(Locale.ROOT)
        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true
        return tokens.all { token -> cleanHaystack.contains(token) }
    }
}
