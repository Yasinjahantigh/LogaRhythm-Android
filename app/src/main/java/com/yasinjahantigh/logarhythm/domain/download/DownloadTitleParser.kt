package com.yasinjahantigh.logarhythm.domain.download

/** Cleans YouTube/SoundCloud titles into an artist + song pair and a safe file name. */
object DownloadTitleParser {

    private val noiseTag = Regex(
        """(?i)[\(\[\{][^\)\]\}]*(official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\b8k\b|m/v|music video|remaster|explicit|clean|color coded|audio only|with lyrics|full song)[^\)\]\}]*[\)\]\}]"""
    )
    private val dashSplit = Regex("""\s+[-–—]\s+""")
    private val uploaderSuffix = Regex("""(?i)\s*-\s*topic\s*$|vevo\s*$""")
    private val unsafeFileChars = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val whitespace = Regex("""\s+""")

    fun clean(text: String): String {
        var value = text.replace('_', ' ').trim()
        repeat(2) { value = value.replace(noiseTag, " ") }
        return value
            .replace(whitespace, " ")
            .trim(' ', '-', '–', '—', '.', ',', '|')
    }

    fun parse(videoTitle: String, uploader: String?, seedArtist: String? = null): Pair<String, String> {
        val raw = videoTitle.trim()
        val cleaned = clean(raw).ifBlank { raw }
        val parts = dashSplit.split(cleaned, limit = 2)

        if (parts.size == 2) {
            val left = clean(parts[0])
            val right = clean(parts[1])
            if (left.isNotBlank() && right.isNotBlank()) return left to right
        }

        val artist = seedArtist?.trim()?.takeIf { it.isNotBlank() }
            ?: artistFromUploader(uploader)
        return artist to cleaned
    }

    fun artistFromUploader(uploader: String?): String {
        val value = uploader.orEmpty().replace(uploaderSuffix, "").trim()
        return if (value.equals("various artists", ignoreCase = true)) "" else value
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(unsafeFileChars, " ")
            .replace(whitespace, " ")
            .trim(' ', '.')
            .take(120)
        return cleaned.ifBlank { "track" }
    }
}
