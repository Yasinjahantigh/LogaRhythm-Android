package com.yasinjahantigh.logarhythm.domain.library

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
        val normalized = artist.replace(SPACES, " ").trim()
        if (normalized.isEmpty()) return emptyList()

        val protectedNames = buildList {
            addAll(PROTECTED_NAMES)
            neverSplit.forEach { add(it.replace(SPACES, " ").trim().lowercase()) }
        }.filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { it.length }

        if (separators.isEmpty() || normalized.lowercase() in protectedNames) return listOf(normalized)

        val placeholders = LinkedHashMap<String, String>()
        val masked = if (protectedNames.isEmpty()) {
            normalized
        } else {
            Regex(
                protectedNames.joinToString("|") { Regex.escape(it) },
                RegexOption.IGNORE_CASE
            ).replace(normalized) { match ->
                val token = "\u0001${placeholders.size}\u0002"
                placeholders[token] = match.value
                token
            }
        }

        val pattern = Regex(
            separators.joinToString("|") { Regex.escape(it.replace(SPACES, " ")) },
            RegexOption.IGNORE_CASE
        )
        val seen = LinkedHashMap<String, String>()
        for (part in masked.split(pattern)) {
            var name = part.trim()
            if (name.isEmpty()) continue
            for ((token, original) in placeholders) {
                if (name.contains(token)) name = name.replace(token, original)
            }
            if (name.isNotEmpty()) seen.putIfAbsent(name.lowercase(), name)
        }
        return if (seen.isEmpty()) listOf(normalized) else seen.values.toList()
    }
}
