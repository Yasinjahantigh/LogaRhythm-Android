package com.yasinjahantigh.logarhythm.domain.library

/**
 * Splits a combined artist tag ("A feat. B; C") into individual artists so each one gets their own
 * page. Pure Kotlin so it is unit-tested on the JVM.
 *
 * Separators are matched literally (case-insensitive) including their surrounding spaces, so a
 * separator of " & " never cuts "AT&T" apart. Well-known acts whose names contain a separator are
 * kept whole.
 */
object ArtistSplitter {

    /** Conservative defaults: ", " is left out because of names like "Tyler, The Creator". */
    val DEFAULT_SEPARATORS: List<String> =
        listOf(";", " feat. ", " feat ", " ft. ", " featuring ", " / ", " & ")

    private val PROTECTED_NAMES: Set<String> = setOf(
        "simon & garfunkel", "hall & oates", "earth, wind & fire", "mumford & sons",
        "crosby, stills & nash", "crosby, stills, nash & young", "brooks & dunn",
        "florence + the machine", "tyler, the creator", "kool & the gang", "ac/dc",
        "mark ronson & the business intl"
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
        separators: List<String> = DEFAULT_SEPARATORS
    ): List<String> {
        val normalized = artist.replace(SPACES, " ").trim()
        if (normalized.isEmpty()) return emptyList()
        if (separators.isEmpty() || normalized.lowercase() in PROTECTED_NAMES) return listOf(normalized)

        val pattern = Regex(
            separators.joinToString("|") { Regex.escape(it.replace(SPACES, " ")) },
            RegexOption.IGNORE_CASE
        )
        val seen = LinkedHashMap<String, String>()
        for (part in normalized.split(pattern)) {
            val name = part.trim()
            if (name.isNotEmpty()) seen.putIfAbsent(name.lowercase(), name)
        }
        return if (seen.isEmpty()) listOf(normalized) else seen.values.toList()
    }
}
