package com.yasinjahantigh.logarhythm.domain.download

import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import java.text.Normalizer
import java.util.Locale

/**
 * Intelligent cross-source artist deduplication and merging engine.
 * Unifies matching artist entries across Deezer, MusicMan, Radio Javan, Saavn, SoundCloud, etc.
 * into a single unified profile without falsely merging different artists.
 */
object ArtistMerger {

    private val COMBINING_MARKS = Regex("""[\p{M}\u064B-\u065F\u0670]""")
    private val WHITESPACE = Regex("""\s+""")
    private val PUNCTUATION_AND_SYMBOLS = Regex("""[^\p{L}\p{N}\s]""")

    // Leading and trailing prefixes/suffixes that often cause duplicate artist records
    private val LEADING_PREFIXES = listOf(
        "the ",
        "dj ",
        "dr ",
        "mr ",
        "mc ",
        "دی جی ",
        "گروه "
    )

    private val TRAILING_SUFFIXES = listOf(
        " band",
        " music",
        " orchestra",
        " بند",
        " باند",
        " موزیک",
        " ارکستر"
    )

    /**
     * Canonical alias dictionary connecting English transliterations, alternative spellings,
     * and Persian script forms of prominent artists so cross-source duplicates merge seamlessly.
     */
    private val KNOWN_ALIASES: Map<String, String> = buildMap {
        fun pair(canonical: String, vararg variants: String) {
            put(canonical, canonical)
            for (v in variants) {
                put(normalizeRawString(v), canonical)
            }
        }

        // Persian Classic & Pop
        pair("ebi", "ابی", "ebrahim hamedi", "ابراهیم حامدی")
        pair("shadmehr aghili", "شادمهر عقیلی", "shadmehr", "شادمهر")
        pair("moein", "معین", "muein", "moeen", "نصرالله معین")
        pair("dariush", "داریوش", "dariush eghbali", "داریوش اقبالی")
        pair("hayedeh", "هایده", "haideh", "معصومه دده بالا")
        pair("mahasti", "مهستی", "خدیجه دده بالا")
        pair("googoosh", "گوگوش", "gougoush", "faegheh atashin", "فائقه آتشین")
        pair("siavash ghomayshi", "سیاوش قمیشی", "ghomayshi", "قمیشی")
        pair("homayoun shajarian", "همایون شجریان")
        pair("mohammad reza shajarian", "محمدرضا شجریان", "mohammadreza shajarian", "shajarian", "شجریان")
        pair("mohsen chavoshi", "محسن چاوشی", "mohsen chavoshi hosseini", "محسن چاووشی")
        pair("mohsen yeganeh", "محسن یگانه")
        pair("mohsen namjoo", "محسن نامجو")
        pair("reza sadeghi", "رضا صادقی")
        pair("reza bahram", "رضا بهرام")
        pair("sirvan khosravi", "سیروان خسروی", "sirvan")
        pair("xaniar khosravi", "زانیار خسروی", "zanyar khosravi", "xaniar", "zanyar")
        pair("ali yasini", "علی یاسینی")
        pair("aron afshar", "آرون افشار")
        pair("meysam ebrahimi", "میثم ابراهیمی")
        pair("ashvan", "اشوان")
        pair("sina derakhshande", "سینا درخشنده")
        pair("macan", "ماکان", "macan band", "ماکان بند")
        pair("puzzle", "پازل", "puzzle band", "پازل بند")
        pair("hoorosh", "هوروش", "hoorosh band", "هوروش بند")
        pair("chaartaar", "چارتار", "chartaar")
        pair("pallett", "پالت", "pallett band", "گروه پالت")
        pair("alireza ghorbani", "علیرضا قربانی")
        pair("mohammad esfahani", "محمد اصفهانی")
        pair("kayhan kalhor", "کیهان کلهر")
        pair("kourosh yaghmaei", "کوروش یغمایی")
        pair("farhad", "فرهاد", "farhad mehrad", "فرهاد مهراد")
        pair("fereydoun foroughi", "فریدون فروغی")
        pair("vigen", "ویگن", "viguen")
        pair("aref", "عارف", "aref arefkia", "عارف عارف کیا")
        pair("andy", "اندی", "andy madadian", "آندرانیک مددیان")
        pair("shahram shabpareh", "شهرام شب پره", "shahram shab pareh")
        pair("hassan shamaizadeh", "حسن شماعی زاده")
        pair("leila forouhar", "لیلا فروهر")
        pair("shohreh", "شهره", "shohreh solati", "شهره صولتی")
        pair("hengameh", "هنگامه")
        pair("mansour", "منصور")
        pair("pouya", "پویا")
        pair("omid", "امید", "omid soltani")
        pair("sepideh", "سپیده")
        pair("pyruz", "پیروز")
        pair("kamyar", "کامیار")
        pair("arash", "آرش", "arash labaf", "آرش لباف")
        pair("sami beigi", "سامی بیگی")
        pair("kiarash", "کیارش")
        pair("morteza pashaei", "مرتضی پاشایی")
        pair("benyamin", "بنیامین", "benyamin bahadori", "بنیامین بهادری")
        pair("farzad farzin", "فرزاد فرزین")
        pair("babak jahanbakhsh", "بابک جهانبخش")
        pair("ehsan khajeh amiri", "احسان خواجه امیری", "ehsan khajehamiri")
        pair("mohammad alizadeh", "محمد علیزاده")
        pair("mehdi yarrahi", "مهدی یراحی")
        pair("mehdi ahmadvand", "مهدی احمدوند")
        pair("saman jalili", "سامان جلیلی")
        pair("yousef zamani", "یوسف زمانی")
        pair("masih", "مسیح")
        pair("arash ap", "آرش ای پی", "arash adlparvar")
        pair("masih and arash ap", "مسیح و آرش", "masih & arash ap", "مسیح و آرش ای پی")
        pair("garsha rezaei", "گرشا رضایی")
        pair("kasra zahedi", "کسری زاهدی")
        pair("ragheb", "راغب", "mostafa ragheb", "مصطفی راغب")
        pair("shahin najafi", "شاهین نجفی")
        pair("farzad golpayegani", "فرزاد گلپایگانی")
        pair("hafez nazeri", "حافظ ناظری")
        pair("shahram nazeri", "شهرام ناظری")

        // Persian Rap / Hip Hop
        pair("yas", "یاس", "yaser bakhtiari")
        pair("hichkas", "هیچکس", "soroush lashkari")
        pair("shayea", "شایع", "mohammadreza shayea")
        pair("bahram", "بهرام", "bahram nouraei", "بهرام نورایی")
        pair("sadegh", "صادق", "sadegh vahedi")
        pair("tataloo", "تتلو", "amir tataloo", "امیر تتلو")
        pair("behzad leito", "بهزاد لیتو", "leito", "لیتو")
        pair("sohrab mj", "سهراب ام جی", "sohrab m j")
        pair("saman wilson", "سامان ویلسون")
        pair("mehrad hidden", "مهراد هیدن")
        pair("alireza jj", "علیرضا جی جی")
        pair("sijal", "سیجل", "siavash jalali")
        pair("zedbazi", "زدبازی")
        pair("erfan", "عرفان", "erfan paydar", "عرفان پایدار")
        pair("poori", "پوری", "godpoori", "گادپوری")
        pair("koorosh", "کوروش", "sami low", "سامی لو")
        pair("sogand", "سوگند", "sogand soheili")
        pair("donya", "دنیا", "donya dadrasan", "دنیا دادرسان")
        pair("anita", "آنیتا")
        pair("justina", "جاستینا")
        pair("gdaal", "جیدال", "ali gdaal")
        pair("madgal", "مدگل")
        pair("sasy", "ساسی", "sasy mankan", "ساسی مانکن")
        pair("tohi", "تهی", "hossein tohi", "حسین تهی")

        // Western Artists
        pair("the weeknd", "weeknd")
        pair("the beatles", "beatles")
        pair("guns n roses", "guns and roses", "guns n' roses")
        pair("twenty one pilots", "21 pilots")
        pair("simon and garfunkel", "simon & garfunkel")
    }

    /**
     * Strips punctuation, diacritics, unifies Persian/Arabic letters and removes common prefixes/suffixes.
     */
    fun normalizeArtistName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""

        val rawCleaned = normalizeRawString(trimmed)

        // Strip known leading prefixes (e.g., "The Beatles" -> "Beatles", "گروه پالت" -> "پالت")
        var withoutPrefix = rawCleaned
        for (prefix in LEADING_PREFIXES) {
            if (withoutPrefix.startsWith(prefix) && withoutPrefix.length > prefix.length + 1) {
                withoutPrefix = withoutPrefix.removePrefix(prefix).trim()
                break
            }
        }

        // Strip known trailing suffixes (e.g., "Macan Band" -> "Macan", "ماکان بند" -> "ماکان")
        var withoutSuffix = withoutPrefix
        for (suffix in TRAILING_SUFFIXES) {
            if (withoutSuffix.endsWith(suffix) && withoutSuffix.length > suffix.length + 1) {
                withoutSuffix = withoutSuffix.removeSuffix(suffix).trim()
                break
            }
        }

        return withoutSuffix
    }

    fun normalizeRawString(raw: String): String {
        var text = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS, "")

        // Handle stylized artist symbols
        text = text
            .replace("$", "s")
            .replace("!", "i")
            .replace("/", "")
            .replace("&", " and ")
            .replace("+", " and ")

        // Persian / Arabic character unifications
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ۀ', 'ة' -> sb.append('ه')
                '\u200C', '\u200B' -> sb.append(' ') // ZWNJ and ZWSP into space
                else -> sb.append(ch)
            }
        }

        return sb.toString()
            .replace(PUNCTUATION_AND_SYMBOLS, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /**
     * Gets the canonical key for an artist, resolving known aliases.
     */
    fun getCanonicalArtistKey(name: String): String {
        val cleanName = if (name.contains("(") && name.contains(")")) {
            val beforeParen = name.substringBefore("(").trim()
            val insideParen = name.substringAfter("(").substringBefore(")").trim()
            val keyBefore = KNOWN_ALIASES[normalizeArtistName(beforeParen)]
                ?: KNOWN_ALIASES[normalizeRawString(beforeParen)]
            val keyInside = KNOWN_ALIASES[normalizeArtistName(insideParen)]
                ?: KNOWN_ALIASES[normalizeRawString(insideParen)]
            keyBefore ?: keyInside ?: beforeParen
        } else {
            name
        }
        val normalized = normalizeArtistName(cleanName)
        if (normalized.isEmpty()) return ""
        return KNOWN_ALIASES[normalized]
            ?: KNOWN_ALIASES[normalizeRawString(cleanName)]
            ?: normalized
    }

    /**
     * Determines whether two artist names refer to the same artist.
     * Prevents false combinations of distinct artists (e.g. "Ebi" vs "Eminem", "Mohsen Yeganeh" vs "Mohsen Chavoshi").
     */
    fun areArtistsMatching(artistA: String, artistB: String): Boolean {
        val a = artistA.trim()
        val b = artistB.trim()
        if (a.isEmpty() || b.isEmpty()) return false

        // Exact match (case-insensitive)
        if (a.equals(b, ignoreCase = true)) return true

        // Check compound parenthesis variations (e.g. "ابی (Ebi)" matching "Ebi" or "ابی")
        if (a.contains("(") && a.contains(")")) {
            val part1 = a.substringBefore("(").trim()
            val part2 = a.substringAfter("(").substringBefore(")").trim()
            if (areArtistsMatching(part1, b) || areArtistsMatching(part2, b)) return true
        }
        if (b.contains("(") && b.contains(")")) {
            val part1 = b.substringBefore("(").trim()
            val part2 = b.substringAfter("(").substringBefore(")").trim()
            if (areArtistsMatching(a, part1) || areArtistsMatching(a, part2)) return true
        }

        val keyA = getCanonicalArtistKey(a)
        val keyB = getCanonicalArtistKey(b)

        if (keyA.isNotEmpty() && keyA == keyB) return true

        val normA = normalizeArtistName(a)
        val normB = normalizeArtistName(b)

        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA == normB) return true

        val tokensA = normA.split(' ').filter { it.isNotBlank() }
        val tokensB = normB.split(' ').filter { it.isNotBlank() }

        // Single-token artists must match exactly (never partially combine single words like "Ebi" vs "Eminem" or "Cher" vs "Cheryl")
        if (tokensA.size == 1 || tokensB.size == 1) {
            return normA == normB || keyA == keyB
        }

        // Multi-token artists: check token intersection
        val setA = tokensA.toSet()
        val setB = tokensB.toSet()

        // If one set completely contains the other, guard against differing distinguishing tokens:
        // E.g. "Mohsen Yeganeh" vs "Mohsen Chavoshi" share "mohsen", but differing tokens ["yeganeh"] vs ["chavoshi"] => DO NOT MATCH!
        val differingA = setA - setB
        val differingB = setB - setA

        if (differingA.isEmpty() || differingB.isEmpty()) {
            return true
        }

        // If both have different non-empty distinguishing tokens, they are different artists
        return false
    }

    /**
     * Merges a list of [DownloadSearchResult]s by unifying matching artist entries
     * into a single profile with combined sources, best artwork, and consolidated artist IDs.
     * Non-artist entries pass through in their original relative position.
     */
    fun mergeArtistResults(results: List<DownloadSearchResult>): List<DownloadSearchResult> {
        if (results.isEmpty()) return emptyList()

        val output = mutableListOf<DownloadSearchResult>()
        val artistIndices = mutableListOf<Int>() // indices in `output` containing ARTIST results

        for (item in results) {
            if (item.kind != DownloadResultKind.ARTIST) {
                output.add(item)
                continue
            }

            // Find if there is an existing artist card in output that matches
            var matchedIndex: Int? = null
            for (idx in artistIndices) {
                val existing = output[idx]
                if (areArtistsMatching(existing.title, item.title)) {
                    matchedIndex = idx
                    break
                }
            }

            if (matchedIndex != null) {
                val existing = output[matchedIndex]
                val merged = mergeTwoArtistResults(existing, item)
                output[matchedIndex] = merged
            } else {
                val initialSources = item.matchedSources.ifEmpty { setOf(item.source) }
                val initialIds = buildMap {
                    putAll(item.sourceArtistIds)
                    val idVal = item.artistId ?: item.id
                    if (idVal.isNotBlank()) put(item.source, idVal)
                }
                val singleArtist = item.copy(
                    matchedSources = initialSources,
                    sourceArtistIds = initialIds,
                    artist = formatSourceSubtitle(initialSources)
                )
                artistIndices.add(output.size)
                output.add(singleArtist)
            }
        }

        return output
    }

    private fun mergeTwoArtistResults(
        primary: DownloadSearchResult,
        secondary: DownloadSearchResult
    ): DownloadSearchResult {
        val mergedSources = (primary.matchedSources + secondary.matchedSources + primary.source + secondary.source)

        val mergedIds = LinkedHashMap<DownloadSource, String>().apply {
            putAll(primary.sourceArtistIds)
            putAll(secondary.sourceArtistIds)
            val pId = primary.artistId ?: primary.id
            if (pId.isNotBlank()) putIfAbsent(primary.source, pId)
            val sId = secondary.artistId ?: secondary.id
            if (sId.isNotBlank()) putIfAbsent(secondary.source, sId)
        }

        val bestThumb = selectBestThumbnail(primary.thumbnailUrl, secondary.thumbnailUrl)
        val bestTitle = selectBestTitle(primary.title, secondary.title)

        return primary.copy(
            title = bestTitle,
            thumbnailUrl = bestThumb,
            artist = formatSourceSubtitle(mergedSources),
            matchedSources = mergedSources,
            sourceArtistIds = mergedIds
        )
    }

    internal fun formatSourceSubtitle(sources: Set<DownloadSource>): String {
        if (sources.isEmpty()) return ""
        return sources.sortedBy { it.ordinal }.joinToString(" · ") { src ->
            when (src) {
                DownloadSource.MUSICMAN -> "MusicMan"
                DownloadSource.RADIOJAVAN -> "Radio Javan"
                DownloadSource.DEEZER -> "Deezer"
                DownloadSource.SAAVN -> "JioSaavn"
                DownloadSource.YOUTUBE -> "YouTube"
                DownloadSource.SOUNDCLOUD -> "SoundCloud"
                DownloadSource.BANDCAMP -> "Bandcamp"
            }
        }
    }

    private fun selectBestThumbnail(first: String?, second: String?): String? {
        if (first.isNullOrBlank()) return second?.takeIf { it.isNotBlank() }
        if (second.isNullOrBlank()) return first

        // Prefer higher quality images if detectable
        fun score(url: String): Int {
            var s = 0
            val lower = url.lowercase(Locale.ROOT)
            if (lower.contains("1000") || lower.contains("xl") || lower.contains("picture_xl")) s += 10
            if (lower.contains("800") || lower.contains("big") || lower.contains("photo_player")) s += 6
            if (lower.contains("500") || lower.contains("medium")) s += 4
            return s
        }

        return if (score(second) > score(first)) second else first
    }

    private fun selectBestTitle(first: String, second: String): String {
        val t1 = first.trim()
        val t2 = second.trim()
        if (t1.isEmpty()) return t2
        if (t2.isEmpty()) return t1
        // If one contains Persian and the other is Latin, prefer the more informative or user-friendly one
        val hasPersian1 = t1.any { it in '\u0600'..'\u06FF' }
        val hasPersian2 = t2.any { it in '\u0600'..'\u06FF' }
        if (hasPersian1 && !hasPersian2 && t2.isNotBlank()) {
            // Keep formatted bilingual title if distinct
            return "$t1 ($t2)"
        }
        if (hasPersian2 && !hasPersian1 && t1.isNotBlank()) {
            return "$t2 ($t1)"
        }
        // Prefer longer / more detailed title
        return if (t2.length > t1.length) t2 else t1
    }
}
