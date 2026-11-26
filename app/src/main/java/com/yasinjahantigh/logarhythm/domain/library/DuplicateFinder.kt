package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlin.math.abs

enum class DuplicateMatchReason {
    SAME_METADATA,
    MULTI_SOURCE,
    IDENTICAL_AUDIO
}

data class DuplicateGroup(
    val key: String,
    val tracks: List<Track>,
    val reason: DuplicateMatchReason = DuplicateMatchReason.SAME_METADATA,
    val recommendedKeepId: String = tracks.firstOrNull()?.id.orEmpty()
)

/**
 * Library hygiene helpers; pure so they can be tested without Android.
 *
 * Detects duplicate tracks reliably without false positives from file-size coincidences:
 * 1. Identical audio content fingerprint (hashed from audio payload bytes, not just file size).
 * 2. Cleaned Title + Version + Primary Artist within ±5s duration (catches 128 vs 320 kbps / FLAC copies).
 * 3. Cleaned Title + Version (non-generic, len >= 4) within ±3s across compatible/missing artist tags
 *    (catches multi-source downloads where one copy has missing/channel artist tags).
 */
object DuplicateFinder {

    private const val METADATA_DURATION_TOLERANCE_MS = 5_000L
    private const val CROSS_SOURCE_DURATION_TOLERANCE_MS = 3_000L
    private const val AUDIO_HASH_DURATION_TOLERANCE_MS = 4_000L

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val BRACKETED_NOISE = Regex(
        """[\(\[\{][^)\]\}]*(?:official|audio|video|lyric|lyrics|visualizer|hd|hq|4k|320|256|192|128|kbps|mp3|flac|m4a|radio\s*edit|radio\s*javan|musicfa|nex1|bia2|download|free)[^)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_NOISE = Regex(
        """(?:\s*[-–—|]\s*)?(?:official\s+(?:music\s+)?(?:video|audio|lyric\s+video)|lyrics?|320\s*kbps|128\s*kbps|192\s*kbps|\[?320\]?|\[?128\]?|radio\s*javan|musicfa|nex1music|bia2music|svmusic|rozmusic|musicdel|ahangify)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val FEAT_SUFFIX = Regex(
        """\s*(?:[\(\[]\s*)?(?:feat\.?|ft\.?|featuring)\s+[^)\]\n]+[\)\]]?""",
        RegexOption.IGNORE_CASE
    )
    private val VERSION_QUALIFIER = Regex(
        """\b(remix|live|acoustic|instrumental|slowed|reverb|sped\s*up|nightcore|cover|demo|unplugged|karaoke|orchestral|piano\s+version)\b""",
        RegexOption.IGNORE_CASE
    )
    private val GENERIC_TITLES = setOf(
        "intro", "outro", "interlude", "untitled", "track", "audio", "music", "song", "unknown", "voice"
    )

    fun normalize(value: String): String = value.lowercase().replace(NON_WORD, "")

    /** Cleans common multi-source download tags/suffixes before comparing titles. */
    fun cleanForComparison(value: String): String {
        var text = value.trim()
        // Normalize Arabic Yeh/Kaf and ZWNJ to consistent Persian forms
        text = text.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
        repeat(3) {
            val next = text
                .replace(BRACKETED_NOISE, " ")
                .replace(FEAT_SUFFIX, " ")
                .replace(TRAILING_NOISE, " ")
                .trim()
            if (next == text) return@repeat
            text = next
        }
        return normalize(text)
    }

    /** Extracts distinct edition qualifiers (remix, live, acoustic, etc.) so variants aren't merged as duplicates. */
    private fun versionTag(title: String): String {
        val matches = VERSION_QUALIFIER.findAll(title.lowercase())
            .map { normalize(it.value) }
            .distinct()
            .sorted()
            .toList()
        return matches.joinToString("+")
    }

    private fun isPlaceholder(value: String): Boolean =
        value.isBlank() ||
            value.startsWith("Unknown ", ignoreCase = true) ||
            value.equals("<unknown>", ignoreCase = true) ||
            value.equals("unknown", ignoreCase = true)

    fun find(
        tracks: List<Track>,
        separators: List<String> = ArtistSplitter.DEFAULT_SEPARATORS,
        fileSizes: Map<String, Long> = emptyMap(),
        audioFingerprints: Map<String, String> = emptyMap()
    ): List<DuplicateGroup> {
        if (tracks.size < 2) return emptyList()

        val n = tracks.size
        val parent = IntArray(n) { it }
        val edgeReasons = HashMap<Int, MutableSet<DuplicateMatchReason>>()

        fun findRoot(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var cur = x
            while (cur != r) {
                val next = parent[cur]
                parent[cur] = r
                cur = next
            }
            return r
        }

        fun union(a: Int, b: Int, reason: DuplicateMatchReason) {
            val ra = findRoot(a)
            val rb = findRoot(b)
            if (ra != rb) {
                parent[rb] = ra
                val merged = edgeReasons.getOrPut(ra) { mutableSetOf() }
                edgeReasons.remove(rb)?.let { merged.addAll(it) }
                merged.add(reason)
            } else {
                edgeReasons.getOrPut(ra) { mutableSetOf() }.add(reason)
            }
        }

        val cleanTitles = Array(n) { i ->
            val t = tracks[i]
            if (isPlaceholder(t.title)) "" else cleanForComparison(t.title)
        }
        val titleVersions = Array(n) { i ->
            val t = tracks[i]
            if (isPlaceholder(t.title)) "" else versionTag(t.title)
        }
        val cleanArtists = Array(n) { i ->
            val t = tracks[i]
            if (isPlaceholder(t.artist)) "" else {
                val primary = ArtistSplitter.split(t.artist, separators).firstOrNull().orEmpty()
                cleanForComparison(primary)
            }
        }
        val rawTitlesNorm = Array(n) { i -> normalize(tracks[i].title) }
        val fileNamesNorm = Array(n) { i ->
            val name = tracks[i].filePath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            normalize(name)
        }

        // Pass 1: Identical audio content fingerprint within ±4s duration
        if (audioFingerprints.isNotEmpty()) {
            val hashBuckets = HashMap<String, MutableList<Int>>()
            for (i in 0 until n) {
                val fp = audioFingerprints[tracks[i].id]?.takeIf { it.isNotBlank() } ?: continue
                hashBuckets.getOrPut(fp) { mutableListOf() }.add(i)
            }
            for (bucket in hashBuckets.values) {
                if (bucket.size < 2) continue
                bucket.sortBy { tracks[it].durationMs }
                for (k in 0 until bucket.size) {
                    for (m in k + 1 until bucket.size) {
                        val i = bucket[k]
                        val j = bucket[m]
                        if (abs(tracks[i].durationMs - tracks[j].durationMs) <= AUDIO_HASH_DURATION_TOLERANCE_MS) {
                            union(i, j, DuplicateMatchReason.IDENTICAL_AUDIO)
                        } else break
                    }
                }
            }
        }

        // Pass 2: Clean Title + Version + Clean Primary Artist within ±5s
        val metaBuckets = HashMap<String, MutableList<Int>>()
        for (i in 0 until n) {
            val ct = cleanTitles[i]
            val ca = cleanArtists[i]
            if (ct.isEmpty() || ca.isEmpty()) continue
            metaBuckets.getOrPut("$ct|${titleVersions[i]}|$ca") { mutableListOf() }.add(i)
        }
        for (bucket in metaBuckets.values) {
            if (bucket.size < 2) continue
            bucket.sortBy { tracks[it].durationMs }
            for (k in 0 until bucket.size) {
                for (m in k + 1 until bucket.size) {
                    val i = bucket[k]
                    val j = bucket[m]
                    if (abs(tracks[i].durationMs - tracks[j].durationMs) <= METADATA_DURATION_TOLERANCE_MS) {
                        val sameSourceFolder = tracks[i].folder.equals(tracks[j].folder, ignoreCase = true)
                        val reason = if (sameSourceFolder) DuplicateMatchReason.SAME_METADATA else DuplicateMatchReason.MULTI_SOURCE
                        union(i, j, reason)
                    } else break
                }
            }
        }

        // Pass 3: Same Cleaned Title + Version (non-generic, len >= 4) within ±3s when artist tags are compatible
        val titleBuckets = HashMap<String, MutableList<Int>>()
        for (i in 0 until n) {
            val ct = cleanTitles[i]
            if (ct.length < 4 || ct in GENERIC_TITLES) continue
            titleBuckets.getOrPut("$ct|${titleVersions[i]}") { mutableListOf() }.add(i)
        }
        for (bucket in titleBuckets.values) {
            if (bucket.size < 2) continue
            bucket.sortBy { tracks[it].durationMs }
            for (k in 0 until bucket.size) {
                for (m in k + 1 until bucket.size) {
                    val i = bucket[k]
                    val j = bucket[m]
                    val durDiff = abs(tracks[i].durationMs - tracks[j].durationMs)
                    if (durDiff > CROSS_SOURCE_DURATION_TOLERANCE_MS) break
                    val a1 = cleanArtists[i]
                    val a2 = cleanArtists[j]
                    val artistInOtherContext = (a1.length >= 3 && (rawTitlesNorm[j].contains(a1) || fileNamesNorm[j].contains(a1))) ||
                        (a2.length >= 3 && (rawTitlesNorm[i].contains(a2) || fileNamesNorm[i].contains(a2)))
                    val compatibleArtist = a1.isEmpty() || a2.isEmpty() ||
                        a1 == a2 || a1.contains(a2) || a2.contains(a1) ||
                        artistInOtherContext
                    if (compatibleArtist) {
                        union(i, j, DuplicateMatchReason.MULTI_SOURCE)
                    }
                }
            }
        }

        // Collect connected components with >= 2 tracks
        val components = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) {
            components.getOrPut(findRoot(i)) { mutableListOf() }.add(i)
        }

        val groups = mutableListOf<DuplicateGroup>()
        for ((root, indices) in components) {
            if (indices.size < 2) continue
            val groupTracks = indices.map { tracks[it] }
                .sortedWith(
                    compareByDescending<Track> { qualityScore(it, fileSizes[it.id] ?: 0L) }
                        .thenBy { it.filePath.length }
                        .thenBy { it.id }
                )
            val keepId = groupTracks.first().id
            val reasons = edgeReasons[root].orEmpty()
            val primaryReason = when {
                DuplicateMatchReason.IDENTICAL_AUDIO in reasons -> DuplicateMatchReason.IDENTICAL_AUDIO
                DuplicateMatchReason.MULTI_SOURCE in reasons -> DuplicateMatchReason.MULTI_SOURCE
                else -> DuplicateMatchReason.SAME_METADATA
            }
            val rep = groupTracks.first()
            val groupKey = (cleanTitles[indices.first()].ifBlank { normalize(rep.title) }) +
                "|" + (cleanArtists[indices.first()].ifBlank { normalize(rep.artist) }) +
                "|${rep.id}"
            groups += DuplicateGroup(
                key = groupKey,
                tracks = groupTracks,
                reason = primaryReason,
                recommendedKeepId = keepId
            )
        }

        return groups.sortedWith(
            compareByDescending<DuplicateGroup> { it.tracks.size }
                .thenBy { it.tracks.firstOrNull()?.title?.lowercase().orEmpty() }
        )
    }

    /**
     * Heuristic to recommend the best copy to keep:
     * prefers lossless / larger bitrate file size, complete metadata tags, and artwork.
     */
    fun qualityScore(track: Track, fileSizeBytes: Long): Long {
        var score = 0L
        score += (fileSizeBytes / 1024L).coerceAtMost(200_000L)
        val lowerPath = track.filePath.lowercase()
        if (lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav") || lowerPath.endsWith(".alac")) {
            score += 20_000L
        } else if (lowerPath.endsWith(".m4a") || lowerPath.endsWith(".opus")) {
            score += 600L
        }
        if (!isPlaceholder(track.title)) score += 300L
        if (!isPlaceholder(track.artist)) score += 300L
        if (!isPlaceholder(track.album)) score += 250L
        if (!track.artworkUri.isNullOrBlank()) score += 200L
        if (track.year > 1900) score += 100L
        return score
    }

    /** Returns every non-best track across all duplicate groups (ready for 1-tap cleanup). */
    fun extraDuplicatesToRemove(groups: List<DuplicateGroup>): List<Track> =
        groups.flatMap { group -> group.tracks.filter { it.id != group.recommendedKeepId } }

    /** Tracks whose title, artist or album tag is still a placeholder. */
    fun missingTags(tracks: List<Track>): List<Track> = tracks.filter {
        isPlaceholder(it.title) || isPlaceholder(it.artist) || isPlaceholder(it.album)
    }

    /** Estimates audio bitrate in kbps from file size and duration when both are known. */
    fun estimateBitrateKbps(fileSizeBytes: Long, durationMs: Long): Int? {
        if (fileSizeBytes <= 0L || durationMs < 5_000L) return null
        val bits = fileSizeBytes.toDouble() * 8.0
        val seconds = durationMs.toDouble() / 1000.0
        val kbps = (bits / seconds / 1000.0).toInt()
        return kbps.takeIf { it in 32..3000 }
    }
}
