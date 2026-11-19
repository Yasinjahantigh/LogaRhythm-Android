package com.yasinjahantigh.logarhythm.domain.library

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Splits a combined artist tag ("A feat. B; C") into individual artists so each one gets their own
 * page. Pure Kotlin so it is unit-tested on the JVM.
 *
 * Separators are matched literally (case-insensitive) including their surrounding spaces, so a
 * separator of " & " never cuts "AT&T" apart. Well-known acts whose names contain a separator are
 * masked before splitting, which keeps "Tyler, The Creator" whole while still splitting
 * "Tyler, The Creator & Kali Uchis".
 */
object ArtistSplitter {

    val DEFAULT_SEPARATORS: List<String> = listOf(
        ";",
        ", ",
        "، ",
        "،",
        " feat. ",
        " feat ",
        " ft. ",
        " featuring ",
        " / ",
        " & ",
        " x ",
        " × ",
        " with "
    )

    private val PROTECTED_NAMES: Set<String> = setOf(
        "simon & garfunkel",
        "hall & oates",
        "earth, wind & fire",
        "mumford & sons",
        "crosby, stills & nash",
        "crosby, stills, nash & young",
        "brooks & dunn",
        "florence + the machine",
        "tyler, the creator",
        "kool & the gang",
        "ac/dc",
        "mark ronson & the business intl",
        "echo & the bunnymen",
        "iron & wine",
        "dan + shay",
        "sam & dave",
        "ike & tina turner",
        "martha & the vandellas",
        "gladys knight & the pips",
        "sly & the family stone",
        "the mamas & the papas",
        "peter, paul & mary",
        "emerson, lake & palmer",
        "blood, sweat & tears",
        "hootie & the blowfish",
        "kc & the sunshine band",
        "the b-52's"
    )

    private val SPACES = Regex("\\s+")
    private const val MAX_RESULT_CACHE = 4096
    private const val MAX_RULES_CACHE = 32

    private class CompiledRules(
        val protectedSet: Set<String>,
        val protectedRegex: Regex?,
        val separatorRegex: Regex?,
        val isDefaultProtected: Boolean
    )

    private data class RulesKey(
        val separators: List<String>,
        val neverSplit: Set<String>
    )

    private val defaultRules: CompiledRules = compileRules(DEFAULT_SEPARATORS, emptySet(), isDefaultProtected = true)
    private val customRulesCache = ConcurrentHashMap<RulesKey, CompiledRules>()
    private val defaultSplitCache = ConcurrentHashMap<String, List<String>>()

    private fun compileRules(
        separators: List<String>,
        neverSplit: Set<String>,
        isDefaultProtected: Boolean
    ): CompiledRules {
        val protectedList = buildList(PROTECTED_NAMES.size + neverSplit.size) {
            addAll(PROTECTED_NAMES)
            neverSplit.forEach {
                val cleaned = collapseSpaces(it).lowercase(Locale.ROOT)
                if (cleaned.isNotEmpty()) add(cleaned)
            }
        }.distinct().sortedByDescending { it.length }

        val protectedRegex = if (protectedList.isEmpty()) {
            null
        } else {
            Regex(
                protectedList.joinToString("|") { Regex.escape(it) },
                RegexOption.IGNORE_CASE
            )
        }
        val separatorRegex = if (separators.isEmpty()) {
            null
        } else {
            Regex(
                separators.joinToString("|") { Regex.escape(collapseSpacesUnTrimmed(it)) },
                RegexOption.IGNORE_CASE
            )
        }
        return CompiledRules(
            protectedSet = protectedList.toHashSet(),
            protectedRegex = protectedRegex,
            separatorRegex = separatorRegex,
            isDefaultProtected = isDefaultProtected
        )
    }

    private fun collapseSpaces(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        var needsCollapse = false
        var prevSpace = false
        for (i in 0 until trimmed.length) {
            val ch = trimmed[i]
            val isWs = ch.isWhitespace()
            if (isWs && (ch != ' ' || prevSpace)) {
                needsCollapse = true
                break
            }
            prevSpace = isWs
        }
        return if (needsCollapse) trimmed.replace(SPACES, " ") else trimmed
    }

    private fun collapseSpacesUnTrimmed(input: String): String {
        var needsCollapse = false
        var prevSpace = false
        for (i in 0 until input.length) {
            val ch = input[i]
            val isWs = ch.isWhitespace()
            if (isWs && (ch != ' ' || prevSpace)) {
                needsCollapse = true
                break
            }
            prevSpace = isWs
        }
        return if (needsCollapse) input.replace(SPACES, " ") else input
    }

    private fun mayContainDefaultProtected(text: String): Boolean {
        for (i in 0 until text.length) {
            when (text[i]) {
                '&', '+', '/', ',', '-' -> return true
            }
        }
        return false
    }

    /** Parses the user-editable list stored as one separator per line. */
    fun parseSeparators(raw: String?): List<String> {
        if (raw == null) return DEFAULT_SEPARATORS
        return raw.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
    }

    fun serializeSeparators(separators: List<String>): String = separators.joinToString("\n")

    fun split(
        artist: String,
        separators: List<String> = DEFAULT_SEPARATORS,
        neverSplit: Set<String> = emptySet()
    ): List<String> {
        val isDefaultCall = (separators === DEFAULT_SEPARATORS || separators == DEFAULT_SEPARATORS) && neverSplit.isEmpty()
        if (isDefaultCall) {
            defaultSplitCache[artist]?.let { return it }
        }

        val normalized = collapseSpaces(artist)
        if (normalized.isEmpty()) return emptyList()

        val rules = if (isDefaultCall) {
            defaultRules
        } else {
            val key = RulesKey(separators, neverSplit)
            customRulesCache[key] ?: compileRules(separators, neverSplit, isDefaultProtected = neverSplit.isEmpty()).also {
                if (customRulesCache.size >= MAX_RULES_CACHE) customRulesCache.clear()
                customRulesCache[key] = it
            }
        }

        val result = splitWithRules(normalized, rules)
        if (isDefaultCall) {
            if (defaultSplitCache.size >= MAX_RESULT_CACHE) defaultSplitCache.clear()
            defaultSplitCache[artist] = result
        }
        return result
    }

    private fun splitWithRules(normalized: String, rules: CompiledRules): List<String> {
        val separatorRegex = rules.separatorRegex ?: return listOf(normalized)
        val lower = normalized.lowercase(Locale.ROOT)
        if (lower in rules.protectedSet) return listOf(normalized)

        val protectedRegex = rules.protectedRegex
        var placeholders: LinkedHashMap<String, String>? = null
        val masked = if (protectedRegex == null || (rules.isDefaultProtected && !mayContainDefaultProtected(normalized))) {
            normalized
        } else {
            protectedRegex.replace(normalized) { match ->
                val map = placeholders ?: LinkedHashMap<String, String>(2).also { placeholders = it }
                val token = "\u0001${map.size}\u0002"
                map[token] = match.value
                token
            }
        }

        if (!separatorRegex.containsMatchIn(masked)) {
            return listOf(normalized)
        }

        val seen = LinkedHashMap<String, String>()
        for (part in masked.split(separatorRegex)) {
            var name = part.trim()
            if (name.isEmpty()) continue
            val map = placeholders
            if (map != null) {
                for ((token, original) in map) {
                    if (name.contains(token)) name = name.replace(token, original)
                }
            }
            if (name.isNotEmpty()) seen.putIfAbsent(name.lowercase(Locale.ROOT), name)
        }
        return if (seen.isEmpty()) listOf(normalized) else seen.values.toList()
    }
}
