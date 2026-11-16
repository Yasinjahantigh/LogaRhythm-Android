package com.yasinjahantigh.logarhythm.domain.download

import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer

/** Cleans YouTube/SoundCloud/local titles into an artist + song pair and a safe file name. */
object DownloadTitleParser {

    private val noiseTag = Regex(
        """(?i)[\(\[\{][^\)\]\}]*(official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\b8k\b|m/v|music video|remaster|explicit|clean|color coded|audio only|with lyrics|full song|\b320\b|\b256\b|\b192\b|\b128\b|kbps|\bmp3\b|\bflac\b|\bm4a\b|\bwav\b|radio\s*javan|bia2music|nex1music|tehranmusic|music-?fa|upmusics|ahang|musicsweb|کیفیت\s*\d*|دانلود|آهنگ\s+جدید|موزیک\s+جدید)[^\)\]\}]*[\)\]\}]"""
    )
    private val inlineAudioExt = Regex("""(?i)\.(mp3|m4a|flac|ogg|opus|wav|aac|wma)$""")
    private val inlineBitrate = Regex("""(?i)(?:\b(?:320|256|192|128|64)\s*kbps\b|(?<=\S)\s*[\[(]?\s*(?:320|128)\s*[\])]?$)""")
    private val telegramHandle = Regex("""(?:^|\s)@[A-Za-z0-9_]{3,}\b""")
    private val websiteWatermark = Regex("""(?i)(?:^|\s|[\[\(\|-])(?:https?://)?(?:www\.)?[a-z0-9-]+(?:\.(?:ir|fm|cc)|\.(?:com|net|org)\b(?=$|\s|[\]\)\|-]))[\]\)\|]*""")
    private val persianDownloadPrefix = Regex("""^(?:\s*دانلود\s+(?:آهنگ|موزیک|ترانه|مداحی|ریمیکس|پادکست)\s*(?:و\s*زیبای\s+|جدید\s+|شاد\s+|غمگین\s+)?(?:از\s+)?|(?i)\bdownload\s+(?:new\s+)?(?:song|music|track)\s*[-:|]?\s*)""")
    private val leadingTrackNum = Regex("""^\s*(?:0\d{1,2}|\d{1,2})\s*(?:[.\)\]]\s*|-\s+|_\s+)""")
    private val persianByPattern = Regex("""^(.+?)\s+به\s+نام\s+(.+)$""")
    private val bracketFeatured = Regex("""(?i)[\(\[\{]\s*(?:feat\.?|ft\.?|featuring|with|با\s+همراهی|فیت)\s+([^\)\]\}]*?)[\)\]\}]""")
    private val trailingFeatured = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|با\s+همراهی)\s+([^-–—|]+)$""")
    private val featuredSplit = Regex("""(?i)\s*(?:,|&|\band\b|\bx\b|\bو\b)\s*""")
    private val dashSplit = Regex("""\s+[-–—]\s+""")
    private val uploaderSuffix = Regex("""(?i)\s*-\s*topic\s*$|vevo\s*$""")
    private val unsafeFileChars = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val whitespace = Regex("""\s+""")

    fun clean(text: String): String {
        var value = MetadataNormalizer.cleanDisplayText(text).replace('_', ' ').trim()
        value = value.replace(inlineAudioExt, "")
        repeat(2) { value = value.replace(noiseTag, " ") }
        value = value
            .replace(websiteWatermark, " ")
            .replace(telegramHandle, " ")
            .replace(inlineBitrate, " ")
            .replace(persianDownloadPrefix, "")
        val withoutTrackNum = value.replace(leadingTrackNum, "")
        if (withoutTrackNum.isNotBlank()) {
            value = withoutTrackNum
        }
        return MetadataNormalizer.cleanDisplayText(value)
            .replace(whitespace, " ")
            .trim(' ', '-', '–', '—', '.', ',', '|')
    }

    /**
     * Separates featured artists (e.g., "(feat. X)", "ft. Y & Z", "(با همراهی X)")
     * from the base title/artist text.
     */
    fun extractFeatured(text: String): Pair<String, List<String>> {
        val raw = MetadataNormalizer.cleanDisplayText(text)
        if (raw.isEmpty()) return "" to emptyList()
        val featured = mutableListOf<String>()

        fun collect(segment: String) {
            featuredSplit.split(segment).forEach { part ->
                val cleanPart = MetadataNormalizer.cleanDisplayText(part).trim(' ', '-', '.', ',', '(', ')', '[', ']')
                if (cleanPart.isNotEmpty()) featured.add(cleanPart)
            }
        }

        var main = raw
        bracketFeatured.findAll(raw).forEach { match ->
            collect(match.groupValues[1])
        }
        main = main.replace(bracketFeatured, " ")

        val trailing = trailingFeatured.find(main)
        if (trailing != null) {
            collect(trailing.groupValues[1])
            main = main.removeRange(trailing.range)
        }

        val cleanedMain = MetadataNormalizer.cleanDisplayText(main)
            .replace(whitespace, " ")
            .trim(' ', '-', '–', '—', '.', ',', '|')
            .ifBlank { raw }

        return cleanedMain to featured.distinct()
    }

    fun parse(videoTitle: String, uploader: String?, seedArtist: String? = null): Pair<String, String> {
        val raw = MetadataNormalizer.cleanDisplayText(videoTitle)
        val hadPersianPrefix = persianDownloadPrefix.containsMatchIn(raw)
        val cleaned = clean(raw).ifBlank { raw }
        val parts = dashSplit.split(cleaned, limit = 2)

        if (parts.size == 2) {
            val left = clean(parts[0])
            val right = clean(parts[1])
            if (left.isNotBlank() && right.isNotBlank()) return left to right
        }

        val fallbackArtist = seedArtist?.trim()?.let { MetadataNormalizer.cleanDisplayText(it) }
            ?.takeIf { it.isNotBlank() }
            ?: artistFromUploader(uploader)

        val persianMatch = persianByPattern.matchEntire(cleaned)
        if (persianMatch != null && (hadPersianPrefix || fallbackArtist.isBlank())) {
            val left = clean(persianMatch.groupValues[1])
            val right = clean(persianMatch.groupValues[2])
            if (left.isNotBlank() && right.isNotBlank()) return left to right
        }

        return fallbackArtist to cleaned
    }

    fun artistFromUploader(uploader: String?): String {
        val value = MetadataNormalizer.cleanDisplayText(uploader.orEmpty())
            .replace(telegramHandle, " ")
            .replace(websiteWatermark, " ")
            .replace(uploaderSuffix, "")
            .replace(whitespace, " ")
            .trim()
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
