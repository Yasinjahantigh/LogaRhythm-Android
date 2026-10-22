package com.yasinjahantigh.logarhythm.domain.download

/** Merge helpers for the Deezer → MusicBrainz → yt-dlp metadata chain. */
object DownloadMetadataMerge {

    private val placeholderValues = setOf(
        "unknown", "unknown artist", "unknown album", "n/a", "na", "none", "null", "-", ""
    )

    fun isPlaceholder(value: String?): Boolean =
        value == null || value.trim().lowercase() in placeholderValues

    fun pick(vararg candidates: String?): String? =
        candidates.firstOrNull { !isPlaceholder(it) }?.trim()

    /**
     * Keeps the artist that came from the video itself unless a provider gives a close match.
     * A compilation or a same-title cover can otherwise replace the real artist.
     */
    fun preferSourceArtist(sourceArtist: String?, vararg providerArtists: String?): String? {
        val source = sourceArtist?.trim()?.takeUnless { isPlaceholder(it) }
            ?: return pick(*providerArtists)
        val best = providerArtists
            .filter { !isPlaceholder(it) }
            .map { it!!.trim() }
            .maxByOrNull { similarity(it, source) }
        return if (best != null && similarity(best, source) >= MIN_ARTIST_SIMILARITY) best else source
    }

    fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    /** Token Jaccard with a containment boost, enough to reject unrelated covers/songs. */
    fun similarity(a: String, b: String): Double {
        val left = normalize(a).split(' ').filter { it.isNotBlank() }.toSet()
        val right = normalize(b).split(' ').filter { it.isNotBlank() }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val jaccard = intersection / left.union(right).size
        val containment = intersection / minOf(left.size, right.size)
        return maxOf(jaccard, containment * 0.9)
    }

    private const val MIN_ARTIST_SIMILARITY = 0.5
}
