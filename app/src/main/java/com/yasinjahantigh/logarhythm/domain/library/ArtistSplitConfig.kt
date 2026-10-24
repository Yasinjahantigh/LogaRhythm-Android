package com.yasinjahantigh.logarhythm.domain.library

/** One resolved snapshot of the user's artist-splitting settings. */
data class ArtistSplitConfig(
    val enabled: Boolean = true,
    val separators: List<String> = ArtistSplitter.DEFAULT_SEPARATORS,
    val neverSplit: Set<String> = emptySet()
) {
    fun namesOf(rawArtist: String): List<String> {
        val trimmed = rawArtist.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (enabled) ArtistSplitter.split(trimmed, separators, neverSplit) else listOf(trimmed)
    }

    companion object {
        val OFF = ArtistSplitConfig(enabled = false, separators = emptyList())
    }
}
