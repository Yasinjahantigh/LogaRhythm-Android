package com.yasinjahantigh.logarhythm.core.utils

import java.util.Locale

/** Utility functions for search queries, including Arabic/Persian character normalization and fuzzy token matching. */
object SearchUtils {

    /**
     * Maps common Arabic glyph variants to Persian forms, strips harakat/diacritics, ZWNJ,
     * normalizes alefs, replaces ASCII punctuation (except apostrophe) with spaces, and collapses
     * whitespace in a single allocation with zero per-call regex compilation.
     */
    fun normalize(input: String): String {
        val len = input.length
        if (len == 0) return ""
        val sb = StringBuilder(len)
        var prevSpace = true
        for (i in 0 until len) {
            val raw = input[i]
            val mapped = when (raw) {
                '\u064A', '\u0649', '\u0626' -> '\u06CC' // Arabic Yeh / Alef Maksura / Yeh with Hamza -> Persian Yeh (ی)
                '\u0643' -> '\u06A9'                     // Arabic Kaf (ك) -> Persian Keheh (ک)
                '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627' // Alef variants -> Alef (ا)
                '\u0629', '\u06C0' -> '\u0647'           // Teh Marbuta / Heh with Yeh Above -> Heh (ه)
                '\u200C', '\u00A0' -> ' '                // Persian ZWNJ / NBSP -> space
                in '\u064B'..'\u065F', '\u0670' -> continue // Strip Arabic harakat / diacritics
                else -> raw
            }
            val isSpaceOrPunct = mapped.isWhitespace() || isAsciiPunctExceptApostrophe(mapped)
            if (isSpaceOrPunct) {
                if (!prevSpace) {
                    sb.append(' ')
                    prevSpace = true
                }
            } else {
                sb.append(mapped)
                prevSpace = false
            }
        }
        val outLen = sb.length
        if (outLen > 0 && sb[outLen - 1] == ' ') {
            sb.setLength(outLen - 1)
        }
        return sb.toString()
    }

    private fun isAsciiPunctExceptApostrophe(ch: Char): Boolean {
        val c = ch.code
        if (c > 0x7E || c == '\''.code) return false
        return (c in 0x21..0x2F) || (c in 0x3A..0x40) || (c in 0x5B..0x60) || (c in 0x7B..0x7E)
    }

    /** Pre-normalizes and splits a query once so batch searches avoid redoing it per track. */
    fun prepareTokens(query: String): List<String> {
        val cleanQuery = normalize(query).lowercase(Locale.ROOT)
        if (cleanQuery.isEmpty()) return emptyList()
        return cleanQuery.split(' ')
    }

    /** Matches pre-normalized query tokens against a raw haystack. */
    fun matchesPreparedTokens(haystack: String, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val cleanHaystack = normalize(haystack).lowercase(Locale.ROOT)
        for (i in tokens.indices) {
            if (!cleanHaystack.contains(tokens[i])) return false
        }
        return true
    }

    /** Checks if all tokens in query exist in haystack (case-insensitive, normalized). */
    fun matchesTokens(haystack: String, query: String): Boolean {
        val tokens = prepareTokens(query)
        if (tokens.isEmpty()) return true
        return matchesPreparedTokens(haystack, tokens)
    }
}
