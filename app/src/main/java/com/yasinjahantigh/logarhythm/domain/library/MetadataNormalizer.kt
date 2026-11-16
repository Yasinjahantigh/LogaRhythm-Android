package com.yasinjahantigh.logarhythm.domain.library

import java.text.Normalizer
import java.util.Locale

/**
 * Pure JVM helpers for cleaning and unifying bilingual (English + Persian/Arabic)
 * metadata, genre tags, and comparison keys.
 */
object MetadataNormalizer {

    private val combiningMarks = Regex("""\p{Mn}+""")
    private val nonWordChars = Regex("""[^\p{L}\p{N}]+""")
    private val whitespace = Regex("""\s+""")
    private val invisibleMarks = Regex("""[\u200B\u200E\u200F\uFEFF\u00AD\u202A-\u202E\u2066-\u2069]""")
    private val zwnjSpaceEdge = Regex("""\s+\u200C+|\u200C+\s+""")
    private val multiZwnj = Regex("""\u200C{2,}""")
    private val arabicDiacritics = Regex("""[\u064B-\u065F\u0670\u06D6-\u06ED\u0640]""")
    private val persianScriptChar = Regex("""[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]""")
    private val id3v1GenrePattern = Regex("""^\(?(\d{1,3})\)?(?:[\s\-:]*(.+))?$""")
    private val genreSplitPattern = Regex("""[,;|/]+""")

    private val commonPlaceholders = setOf(
        "<unknown>",
        "unknown",
        "unknown artist",
        "unknown title",
        "unknown album",
        "unknown genre",
        "null",
        "n/a",
        "na",
        "untitled",
        "نامشخص",
        "ناشناس",
        "بدون نام",
        "بدون عنوان"
    )

    private val extraGenrePlaceholders = setOf(
        "other",
        "misc",
        "miscellaneous",
        "none",
        "genre",
        "music",
        "audio",
        "سایر",
        "بدون ژانر",
        "متفرقه"
    )

    // Standard ID3v1 / Winamp genre table (codes 0..147 cover virtually all legacy MP3 tags).
    private val id3v1Genres: Map<Int, String> = mapOf(
        0 to "Blues",
        1 to "Classic Rock",
        2 to "Country",
        3 to "Dance",
        4 to "Disco",
        5 to "Funk",
        6 to "Grunge",
        7 to "Hip-Hop",
        8 to "Jazz",
        9 to "Metal",
        10 to "New Age",
        11 to "Oldies",
        12 to "Other",
        13 to "Pop",
        14 to "R&B",
        15 to "Rap",
        16 to "Reggae",
        17 to "Rock",
        18 to "Techno",
        19 to "Industrial",
        20 to "Alternative",
        21 to "Ska",
        22 to "Death Metal",
        23 to "Pranks",
        24 to "Soundtrack",
        25 to "Euro-Techno",
        26 to "Ambient",
        27 to "Trip-Hop",
        28 to "Vocal",
        29 to "Jazz+Funk",
        30 to "Fusion",
        31 to "Trance",
        32 to "Classical",
        33 to "Instrumental",
        34 to "Acid",
        35 to "House",
        36 to "Game",
        37 to "Sound Clip",
        38 to "Gospel",
        39 to "Noise",
        40 to "Alternative Rock",
        41 to "Bass",
        42 to "Soul",
        43 to "Punk",
        44 to "Space",
        45 to "Meditative",
        46 to "Instrumental Pop",
        47 to "Instrumental Rock",
        48 to "Ethnic",
        49 to "Gothic",
        50 to "Darkwave",
        51 to "Techno-Industrial",
        52 to "Electronic",
        53 to "Pop-Folk",
        54 to "Eurodance",
        55 to "Dream",
        56 to "Southern Rock",
        57 to "Comedy",
        58 to "Cult",
        59 to "Gangsta",
        60 to "Top 40",
        61 to "Christian Rap",
        62 to "Pop/Funk",
        63 to "Jungle",
        64 to "Native American",
        65 to "Cabaret",
        66 to "New Wave",
        67 to "Psychedelic",
        68 to "Rave",
        69 to "Showtunes",
        70 to "Trailer",
        71 to "Lo-Fi",
        72 to "Tribal",
        73 to "Acid Punk",
        74 to "Acid Jazz",
        75 to "Polka",
        76 to "Retro",
        77 to "Musical",
        78 to "Rock & Roll",
        79 to "Hard Rock",
        80 to "Folk",
        81 to "Folk-Rock",
        82 to "National Folk",
        83 to "Swing",
        84 to "Fast Fusion",
        85 to "Bebop",
        86 to "Latin",
        87 to "Revival",
        88 to "Celtic",
        89 to "Bluegrass",
        90 to "Avantgarde",
        91 to "Gothic Rock",
        92 to "Progressive Rock",
        93 to "Psychedelic Rock",
        94 to "Symphonic Rock",
        95 to "Slow Rock",
        96 to "Big Band",
        97 to "Chorus",
        98 to "Easy Listening",
        99 to "Acoustic",
        100 to "Humour",
        101 to "Speech",
        102 to "Chanson",
        103 to "Opera",
        104 to "Chamber Music",
        105 to "Sonata",
        106 to "Symphony",
        107 to "Booty Bass",
        108 to "Primus",
        109 to "Porn Groove",
        110 to "Satire",
        111 to "Slow Jam",
        112 to "Club",
        113 to "Tango",
        114 to "Samba",
        115 to "Folklore",
        116 to "Ballad",
        117 to "Power Ballad",
        118 to "Rhythmic Soul",
        119 to "Freestyle",
        120 to "Duet",
        121 to "Punk Rock",
        122 to "Drum Solo",
        123 to "A Cappella",
        124 to "Euro-House",
        125 to "Dance Hall",
        126 to "Goa",
        127 to "Drum & Bass",
        128 to "Club-House",
        129 to "Hardcore",
        130 to "Terror",
        131 to "Indie",
        132 to "BritPop",
        133 to "Negerpunk",
        134 to "Polsk Punk",
        135 to "Beat",
        136 to "Christian Gangsta Rap",
        137 to "Heavy Metal",
        138 to "Black Metal",
        139 to "Crossover",
        140 to "Contemporary Christian",
        141 to "Christian Rock",
        142 to "Merengue",
        143 to "Salsa",
        144 to "Thrash Metal",
        145 to "Anime",
        146 to "J-Pop",
        147 to "Synthpop",
        148 to "Abstract",
        149 to "Art Rock",
        150 to "Baroque",
        151 to "Bhangra",
        152 to "Big Beat",
        153 to "Breakbeat",
        154 to "Chillout",
        155 to "Downtempo",
        156 to "Dub",
        157 to "EBM",
        158 to "Eclectic",
        159 to "Electro",
        160 to "Electroclash",
        161 to "Emo",
        162 to "Experimental",
        163 to "Garage",
        164 to "Global",
        165 to "IDM",
        166 to "Illbient",
        167 to "Industro-Goth",
        168 to "Jam Band",
        169 to "Krautrock",
        170 to "Leftfield",
        171 to "Lounge",
        172 to "Math Rock",
        173 to "New Romantic",
        174 to "Nu-Breakz",
        175 to "Post-Punk",
        176 to "Post-Rock",
        177 to "Psytrance",
        178 to "Shoegaze",
        179 to "Space Rock",
        180 to "Trop Rock",
        181 to "World Music",
        182 to "Neoclassical",
        183 to "Audiobook",
        184 to "Audio Theatre",
        185 to "Neue Deutsche Welle",
        186 to "Podcast",
        187 to "Indie-Rock",
        188 to "G-Funk",
        189 to "Dubstep",
        190 to "Garage Rock",
        191 to "Psybient"
    )

    private val latinDisplayCanonical: Map<String, String> = mapOf(
        "pop" to "Pop",
        "persian pop" to "Persian Pop",
        "iranian pop" to "Persian Pop",
        "synthpop" to "Synthpop",
        "synth pop" to "Synthpop",
        "electropop" to "Electropop",
        "dance pop" to "Dance Pop",
        "indie pop" to "Indie Pop",
        "kpop" to "K-Pop",
        "k pop" to "K-Pop",
        "jpop" to "J-Pop",
        "j pop" to "J-Pop",
        "rap" to "Rap",
        "persian rap" to "Persian Rap",
        "hiphop" to "Hip-Hop",
        "hip hop" to "Hip-Hop",
        "hip hop rap" to "Hip-Hop/Rap",
        "rap hip hop" to "Hip-Hop/Rap",
        "trap" to "Trap",
        "rnb" to "R&B",
        "r b" to "R&B",
        "r and b" to "R&B",
        "rnb soul" to "R&B/Soul",
        "soul" to "Soul",
        "rock" to "Rock",
        "alternative rock" to "Alternative Rock",
        "alt rock" to "Alternative Rock",
        "alternative" to "Alternative",
        "indie" to "Indie",
        "indie rock" to "Indie Rock",
        "metal" to "Metal",
        "heavy metal" to "Heavy Metal",
        "electronic" to "Electronic",
        "electronics" to "Electronic",
        "edm" to "Electronic",
        "electro" to "Electronic",
        "house" to "House",
        "deep house" to "Deep House",
        "techno" to "Techno",
        "trance" to "Trance",
        "dubstep" to "Dubstep",
        "dance" to "Dance",
        "ambient" to "Ambient",
        "chill" to "Chillout",
        "chillout" to "Chillout",
        "lofi" to "Lo-Fi",
        "lo fi" to "Lo-Fi",
        "jazz" to "Jazz",
        "blues" to "Blues",
        "classical" to "Classical",
        "instrumental" to "Instrumental",
        "soundtrack" to "Soundtrack",
        "ost" to "Soundtrack",
        "film score" to "Soundtrack",
        "score" to "Soundtrack",
        "folk" to "Folk",
        "traditional" to "Traditional",
        "persian traditional" to "Traditional",
        "sonnati" to "Traditional",
        "acoustic" to "Acoustic",
        "reggae" to "Reggae",
        "latin" to "Latin",
        "country" to "Country",
        "punk" to "Punk",
        "funk" to "Funk",
        "disco" to "Disco",
        "world" to "World",
        "world music" to "World"
    )

    private val genreFamilyRules: List<Pair<String, List<String>>> = listOf(
        "metal" to listOf("metal", "متال", "هوی متال", "راک سنگین"),
        "rap" to listOf("rap", "hip hop", "hiphop", "trap", "drill", "boom bap", "grime", "رپ", "هیپ هاپ", "ترپ", "دریل", "گنگ"),
        "rnb" to listOf("r b", "rnb", "soul", "neo soul", "motown", "سول", "آر اند بی", "ار اند بی"),
        "rock" to listOf("rock", "punk", "grunge", "indie", "alternative", "alt", "shoegaze", "emo", "britpop", "post punk", "راک", "آلترناتیو", "الترناتیو", "ایندی", "پانک", "گرانج"),
        "electronic" to listOf("electronic", "edm", "electro", "house", "techno", "trance", "dubstep", "synth", "dnb", "drum bass", "drum and bass", "club", "disco", "dance", "rave", "الکترونیک", "هاوس", "تکنو", "ترنس", "دابستپ", "دنس", "دیسکو", "کلاب", "شاد", "بندری", "تکنو"),
        "traditional" to listOf("traditional", "sonnati", "sonati", "folklore", "folk", "ethnic", "celtic", "سنتی", "آواز سنتی", "موسیقی سنتی", "اصیل", "ایرانی اصیل", "محلی", "فولکلور", "فولک", "مقامی", "لری", "کردی", "آذری", "شمالی", "جنوبی"),
        "classical" to listOf("classical", "orchestral", "symphony", "opera", "baroque", "neoclassical", "sonata", "chamber", "کلاسیک", "ارکسترال", "سمفونی", "اپرا"),
        "jazz" to listOf("jazz", "blues", "swing", "bebop", "bossa", "جاز", "بلوز"),
        "chill" to listOf("ambient", "chill", "chillout", "lo fi", "lofi", "downtempo", "new age", "meditative", "easy listening", "lounge", "امبینت", "چیل", "لایفای", "لوفای", "آرام", "آرامبخش", "مدیتیشن", "آسان"),
        "acoustic" to listOf("acoustic", "unplugged", "singer songwriter", "ballad", "slow", "آکوستیک", "اکوستیک", "آنپلاگد", "انپلاگد", "ملایم", "غمگین", "اسلو", "دپ"),
        "instrumental" to listOf("instrumental", "piano", "guitar", "violin", "solo", "بیکلام", "بی کلام", "پیانو", "گیتار", "ویولن"),
        "soundtrack" to listOf("soundtrack", "ost", "score", "cinema", "game", "anime", "trailer", "موسیقی متن", "متن فیلم", "تیتراژ", "انیمه", "بازی"),
        "reggae" to listOf("reggae", "ska", "dub", "رگی", "اسکا"),
        "country" to listOf("country", "bluegrass", "americana", "کانتری"),
        "latin" to listOf("latin", "salsa", "samba", "tango", "reggaeton", "merengue", "لاتین"),
        "nostalgic" to listOf("oldies", "retro", "nostalgia", "nostalgic", "vintage", "نوستالژی", "نوستالژیک", "قدیمی", "دهه شصت", "دهه هفتاد", "لس آنجلسی", "کوچه بازاری"),
        "fusion" to listOf("fusion", "world", "crossover", "تلفیقی"),
        "religious" to listOf("gospel", "christian", "islamic", "nasheed", "مذهبی", "مداحی", "نوحه", "عاشورایی", "دعا"),
        "pop" to listOf("pop", "k pop", "j pop", "funk", "پاپ", "فیوژن پاپ")
    )

    /**
     * Normalizes display strings without altering valid script characters:
     * unifies Arabic `ك`/`ي`/`ى` -> Persian `ک`/`ی`, removes invisible bidi/zero-width
     * noise, cleans edge ZWNJs, decodes common HTML entities, and collapses spaces.
     */
    fun cleanDisplayText(text: String): String {
        if (text.isEmpty()) return ""
        var s = unifyPersianScript(text)
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&ndash;", "-")
            .replace("&mdash;", "-")
            .replace("&nbsp;", " ")
        s = s.replace(invisibleMarks, "")
        s = s.replace(multiZwnj, "\u200C")
        s = s.replace(zwnjSpaceEdge, " ")
        s = s.replace(whitespace, " ").trim().trim('\u200C', ' ')
        return s
    }

    /**
     * Produces a canonical comparison key: unifies Arabic/Persian forms, removes
     * diacritics/ZWNJ/Latin accents, maps Eastern digits to ASCII, lowercases, and
     * replaces punctuation with single spaces.
     */
    fun normalizeForComparison(value: String): String {
        if (value.isBlank()) return ""
        var s = cleanDisplayText(value)
            .replace('\u0629', '\u0647') // ة -> ه
            .replace('\u0624', '\u0648') // ؤ -> و
            .replace('\u06C0', '\u0647') // ۀ -> ه
            .replace('\u0623', '\u0627') // أ -> ا
            .replace('\u0625', '\u0627') // إ -> ا
            .replace('\u0622', '\u0627') // آ -> ا
            .replace('\u0671', '\u0627') // ٱ -> ا
            .replace(arabicDiacritics, "")
            .replace('\u200C', ' ')
        s = asciiDigits(s)
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(combiningMarks, "")
        return s.lowercase(Locale.ROOT)
            .replace(nonWordChars, " ")
            .replace(whitespace, " ")
            .trim()
    }

    fun containsPersian(text: String): Boolean = persianScriptChar.containsMatchIn(text)

    fun isPlaceholderField(value: String?): Boolean {
        val norm = normalizeForComparison(value.orEmpty())
        val rawLower = cleanDisplayText(value.orEmpty()).lowercase(Locale.ROOT)
        return norm.isEmpty() || rawLower in commonPlaceholders || norm in commonPlaceholders
    }

    fun isPlaceholderGenre(value: String?): Boolean {
        if (isPlaceholderField(value)) return true
        val norm = normalizeForComparison(value.orEmpty())
        val rawLower = cleanDisplayText(value.orEmpty()).lowercase(Locale.ROOT)
        return norm in extraGenrePlaceholders || rawLower in extraGenrePlaceholders
    }

    /**
     * Resolves ID3v1 numeric codes (e.g. "17", "(13)", "(13)Pop"), discards placeholders,
     * cleans Persian/Arabic orthography, and normalizes common casing/synonyms while
     * preserving readable Persian genre labels.
     */
    fun canonicalizeGenre(raw: String?, fallback: String = "Unknown Genre"): String {
        val cleaned = cleanDisplayText(raw.orEmpty())
        if (cleaned.isEmpty() || isPlaceholderGenre(cleaned)) return fallback

        val id3Match = id3v1GenrePattern.matchEntire(cleaned)
        if (id3Match != null) {
            val code = id3Match.groupValues[1].toIntOrNull()
            val tail = cleanDisplayText(id3Match.groupValues[2])
            if (tail.isNotEmpty() && !isPlaceholderGenre(tail)) {
                return canonicalizeSingleOrListGenre(tail, fallback)
            }
            val mapped = code?.let { id3v1Genres[it] }
            if (mapped != null && !isPlaceholderGenre(mapped)) {
                return mapped
            }
            return fallback
        }

        return canonicalizeSingleOrListGenre(cleaned, fallback)
    }

    private fun canonicalizeSingleOrListGenre(cleaned: String, fallback: String): String {
        val normFull = normalizeForComparison(cleaned)
        latinDisplayCanonical[normFull]?.let { return it }

        val parts = genreSplitPattern.split(cleaned)
            .map { cleanDisplayText(it) }
            .filter { it.isNotEmpty() && !isPlaceholderGenre(it) }
        if (parts.isEmpty()) return fallback

        if (parts.size == 1) {
            val single = parts.first()
            val norm = normalizeForComparison(single)
            return latinDisplayCanonical[norm] ?: normalizePersianGenreLabel(single)
        }

        // Preserve "R&B/Soul" or similar compound labels cleanly if recognized, else format parts.
        val normalizedParts = parts.map { part ->
            val norm = normalizeForComparison(part)
            latinDisplayCanonical[norm] ?: normalizePersianGenreLabel(part)
        }.distinct()
        return normalizedParts.joinToString(" / ")
    }

    private fun normalizePersianGenreLabel(value: String): String {
        if (containsPersian(value)) return cleanDisplayText(value)
        // Tidy purely lowercase or ALL-CAPS English genres (e.g., "POST ROCK" -> "Post Rock").
        val isAllLower = value == value.lowercase(Locale.ROOT)
        val isAllUpper = value == value.uppercase(Locale.ROOT) && value.any { it.isLetter() }
        return if (isAllLower || isAllUpper) {
            value.lowercase(Locale.ROOT)
                .split(whitespace)
                .joinToString(" ") { word ->
                    word.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                    }
                }
        } else {
            value
        }
    }

    /**
     * Maps bilingual Persian and English genre labels into unified family keys
     * (e.g., `"پاپ"`, `"Pop"`, `"Synthpop"`, `"Persian Pop"` -> `"pop"`,
     * `"رپ"`, `"Rap"`, `"Hip-Hop"`, `"هیپ هاپ"` -> `"rap"`).
     * Returns `""` for unknown/placeholder genres.
     */
    fun genreGroupKey(raw: String?): String {
        val canonical = canonicalizeGenre(raw, fallback = "")
        if (canonical.isEmpty()) return ""
        val norm = normalizeForComparison(canonical)
        if (norm.isEmpty()) return ""

        for ((family, keywords) in genreFamilyRules) {
            for (keyword in keywords) {
                val kwNorm = normalizeForComparison(keyword)
                if (norm == kwNorm ||
                    norm.startsWith("$kwNorm ") ||
                    norm.endsWith(" $kwNorm") ||
                    norm.contains(" $kwNorm ")
                ) {
                    return family
                }
            }
        }
        return norm
    }

    private fun unifyPersianScript(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    '\u0643' -> '\u06A9' // ك -> ک
                    '\u064A', '\u0649' -> '\u06CC' // ي, ى -> ی
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun asciiDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    in '\u06F0'..'\u06F9' -> ('0'.code + (ch.code - '\u06F0'.code)).toChar()
                    in '\u0660'..'\u0669' -> ('0'.code + (ch.code - '\u0660'.code)).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
