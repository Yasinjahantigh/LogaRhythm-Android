package com.yasinjahantigh.logarhythm.domain.download

import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer

/** Cleans YouTube/SoundCloud/local titles into an artist + song pair and a safe file name. */
object DownloadTitleParser {

    private val noiseTag = Regex(
        """(?i)[\(\[\{][^\)\]\}]*(official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\b8k\b|m/v|music video|remaster|explicit|clean|color coded|audio only|with lyrics|full song|\b320\b|\b256\b|\b192\b|\b128\b|kbps|\bmp3\b|\bflac\b|\bm4a\b|\bwav\b|radio\s*javan|bia2music|nex1music|tehranmusic|music-?fa|upmusics?|download1music|golsarmusic|sakhamusic|ahangify|ahangha|ahang|musicsweb|musicdel|rozmusic|sultanmusic|tabanmusic|apmusic|irantune|farskids|fazmusic|javanmusic|mytehranmusic|pop-?music|کیفیت\s*\d*|دانلود|آهنگ\s+جدید|موزیک\s+جدید|نسخه\s+اصلی|دمو|پخش\s+آنلاین)[^\)\]\}]*[\)\]\}]"""
    )
    private val inlineAudioExt = Regex("""(?i)\.(mp3|m4a|flac|ogg|opus|wav|aac|wma)$""")
    private val inlineBitrate = Regex("""(?i)(?:\b(?:320|256|192|128|64)\s*kbps\b|(?<=\S)\s*[\[(]?\s*(?:320|128)\s*[\])]?$)""")
    private val telegramHandle = Regex("""(?:^|\s)@[A-Za-z0-9_]{3,}\b""")
    private val websiteWatermark = Regex("""(?i)(?:^|\s|[\[\(\|-])(?:https?://)?(?:www\.)?[a-z0-9-]+(?:\.(?:ir|fm|cc|xyz|co|info|me|tv)|\.(?:com|net|org)\b(?=$|\s|[\]\)\|-]))[\]\)\|]*""")
    private val siteBrandNoise = Regex(
        """(?i)(?:^|\s|[-–—|])(?:radio\s*javan|bia2music|nex1music|tehranmusic|mytehranmusic|music-?fa|upmusics?|download1music|golsarmusic|sakhamusic|ahangify|ahangha|musicsweb|musicdel|rozmusic|sultanmusic|tabanmusic|apmusic|irantune|farskids|fazmusic|javanmusic|pop-?music|musictag)(?=$|\s|[-–—|\.\]\)]|\d)"""
    )
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
        if (text.isEmpty()) return ""
        val cleanedDisplay = MetadataNormalizer.cleanDisplayText(text)
        if (cleanedDisplay.isEmpty()) return ""
        var value = if (cleanedDisplay.indexOf('_') >= 0) cleanedDisplay.replace('_', ' ').trim() else cleanedDisplay

        var hasDot = false
        var hasBracket = false
        var hasAt = false
        var hasDigit = false
        for (i in 0 until value.length) {
            when (value[i]) {
                '.' -> hasDot = true
                '(', '[', '{' -> hasBracket = true
                '@' -> hasAt = true
                in '0'..'9' -> hasDigit = true
            }
        }

        if (hasDot) {
            value = value.replace(inlineAudioExt, "")
        }
        if (hasBracket) {
            val firstPass = value.replace(noiseTag, " ")
            value = if (firstPass != value && firstPass.any { it == '(' || it == '[' || it == '{' }) {
                firstPass.replace(noiseTag, " ")
            } else {
                firstPass
            }
        }
        if (hasDot) {
            value = value.replace(websiteWatermark, " ")
        }
        // Only run siteBrandNoise when string could contain one of those brand tokens
        if (mayContainSiteBrand(value)) {
            value = value.replace(siteBrandNoise, " ")
        }
        if (hasAt) {
            value = value.replace(telegramHandle, " ")
        }
        if (hasDigit) {
            value = value.replace(inlineBitrate, " ")
        }
        val firstCh = value.firstOrNull()
        if (firstCh == 'د' || firstCh == 'd' || firstCh == 'D' || firstCh?.isWhitespace() == true) {
            value = value.replace(persianDownloadPrefix, "")
        }
        if (hasDigit && (value.firstOrNull()?.isDigit() == true || value.firstOrNull()?.isWhitespace() == true)) {
            val withoutTrackNum = value.replace(leadingTrackNum, "")
            if (withoutTrackNum.isNotBlank()) {
                value = withoutTrackNum
            }
        }
        val finalClean = if (value === cleanedDisplay) value else MetadataNormalizer.cleanDisplayText(value)
        return finalClean.trim(' ', '-', '–', '—', '.', ',', '|')
    }

    private fun mayContainSiteBrand(s: String): Boolean {
        if (s.length < 7) return false
        return s.contains("music", ignoreCase = true) ||
            s.contains("javan", ignoreCase = true) ||
            s.contains("ahang", ignoreCase = true) ||
            s.contains("tune", ignoreCase = true) ||
            s.contains("farskids", ignoreCase = true)
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
        if (raw.any { it == '(' || it == '[' || it == '{' }) {
            bracketFeatured.findAll(raw).forEach { match ->
                collect(match.groupValues[1])
            }
            main = main.replace(bracketFeatured, " ")
        }

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
        val firstCh = raw.firstOrNull()
        val hadPersianPrefix = (firstCh == 'د' || firstCh == 'd' || firstCh == 'D') &&
            persianDownloadPrefix.containsMatchIn(raw)
        val cleaned = clean(raw).ifBlank { raw }
        if (cleaned.indexOf('-') >= 0 || cleaned.indexOf('–') >= 0 || cleaned.indexOf('—') >= 0) {
            val parts = dashSplit.split(cleaned, limit = 2)
            if (parts.size == 2) {
                val left = clean(parts[0])
                val right = clean(parts[1])
                if (left.isNotBlank() && right.isNotBlank()) return left to right
            }
        }

        val fallbackArtist = seedArtist?.trim()?.let { MetadataNormalizer.cleanDisplayText(it) }
            ?.takeIf { it.isNotBlank() }
            ?: artistFromUploader(uploader)

        if (cleaned.contains("به نام")) {
            val persianMatch = persianByPattern.matchEntire(cleaned)
            if (persianMatch != null && (hadPersianPrefix || fallbackArtist.isBlank())) {
                val left = clean(persianMatch.groupValues[1])
                val right = clean(persianMatch.groupValues[2])
                if (left.isNotBlank() && right.isNotBlank()) return left to right
            }
        }

        return fallbackArtist to cleaned
    }

    fun artistFromUploader(uploader: String?): String {
        if (uploader.isNullOrBlank()) return ""
        var value = MetadataNormalizer.cleanDisplayText(uploader)
        if (value.indexOf('@') >= 0) value = value.replace(telegramHandle, " ")
        if (value.indexOf('.') >= 0) value = value.replace(websiteWatermark, " ")
        if (value.endsWith("topic", ignoreCase = true) || value.endsWith("vevo", ignoreCase = true)) {
            value = value.replace(uploaderSuffix, "")
        }
        value = MetadataNormalizer.cleanDisplayText(value)
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
