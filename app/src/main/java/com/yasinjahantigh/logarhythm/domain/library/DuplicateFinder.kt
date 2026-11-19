package com.yasinjahantigh.logarhythm.domain.library

import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class DuplicateMatchReason {
    SAME_METADATA,
    MULTI_SOURCE,
    SAME_FILE_SIZE
}

data class DuplicateGroup(
    val key: String,
    val tracks: List<Track>,
    val reason: DuplicateMatchReason = DuplicateMatchReason.SAME_METADATA,
    val recommendedKeepId: String = tracks.firstOrNull()?.id.orEmpty()
)

/**
 * Library hygiene helpers; pure so they can be tested without Android.
 * Detects duplicates across different download sources using normalized metadata
 * (stripping site/quality tags like [320], Official Audio, RadioJavan, etc.),
 * cross-source title/duration similarity, and identical/near-identical file sizes.
 */
object DuplicateFinder {

    private const val METADATA_DURATION_TOLERANCE_MS = 6_000L
    private const val CROSS_SOURCE_DURATION_TOLERANCE_MS = 3_000L
    private const val SIZE_DURATION_TOLERANCE_MS = 2_500L
    private const val MIN_FILE_SIZE_FOR_SIZE_MATCH = 256L * 1024L // 256 KB
    private const val SIZE_TOLERANCE_BYTES = 16L * 1024L // 16 KB (header/ID3 tag delta)
    private const val SIZE_TOLERANCE_RATIO = 0.015 // 1.5%

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val BRACKETED_NOISE = Regex(
        """[\(\[\{][^)\]\}]*(?:official|audio|video|lyric|lyrics|visualizer|hd|hq|4k|320|256|192|128|kbps|mp3|flac|m4a|remix\s*version|radio\s*edit|radio\s*javan|musicfa|nex1|bia2|download|free)[^)\]\}]*[\)\]\}]""",
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

    fun normalize(value: String): String = value.lowercase().replace(NON_WORD, "")

    /** Cleans common multi-source download tags/suffixes before comparing titles. */
    fun cleanForComparison(value: String): String {
        var text = value.trim()
        // Strip Persian/Arabic diacritics and normalize Arabic Yeh/Kaf to Persian forms
        text = text.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
        // Iteratively strip trailing/bracketed noise tags
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

    private fun isPlaceholder(value: String): Boolean =
        value.isBlank() ||
            value.startsWith("Unknown ", ignoreCase = true) ||
            value.equals("<unknown>", ignoreCase = true) ||
            value.equals("unknown", ignoreCase = true)

    /**
     * Finds duplicate tracks using 3 complementary signals:
     * 1. Cleaned Title + Primary Artist within ±6s duration (catches multi-source re-encodes).
     * 2. Exact Cleaned Title (len >= 5) within ±3s duration across messy/different artist tags
     *    (e.g. downloaded from Telegram vs MusicMan vs YouTube where artist tag differs or is missing).
     * 3. Near-identical file size (within ±16KB or ±1.5%, >= 256KB) AND duration within ±2.5s
     *    (catches identical audio files saved with completely different titles/tags).
     */
    fun find(
        tracks: List<Track>,
        separators: List<String> = ArtistSplitter.DEFAULT_SEPARATORS,
        fileSizes: Map<String, Long> = emptyMap()
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
        val cleanArtists = Array(n) { i ->
            val t = tracks[i]
            if (isPlaceholder(t.artist)) "" else {
                val primary = ArtistSplitter.split(t.artist, separators).firstOrNull().orEmpty()
                cleanForComparison(primary)
            }
        }

        // Pass 1: Clean Title + Clean Primary Artist within ±6s
        val metaBuckets = HashMap<String, MutableList<Int>>()
        for (i in 0 until n) {
            val ct = cleanTitles[i]
            val ca = cleanArtists[i]
            if (ct.isEmpty() || ca.isEmpty()) continue
            metaBuckets.getOrPut("$ct|$ca") { mutableListOf() }.add(i)
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

        // Pass 2: Same Cleaned Title (len >= 5) within ±3s when artist tags differ/missing (multi-source downloads)
        val titleBuckets = HashMap<String, MutableList<Int>>()
        for (i in 0 until n) {
            val ct = cleanTitles[i]
            if (ct.length < 5) continue
            titleBuckets.getOrPut(ct) { mutableListOf() }.add(i)
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
                    val compatibleArtist = a1.isEmpty() || a2.isEmpty() ||
                        a1 == a2 || a1.contains(a2) || a2.contains(a1) ||
                        durDiff <= 1_500L
                    if (compatibleArtist) {
                        union(i, j, DuplicateMatchReason.MULTI_SOURCE)
                    }
                }
            }
        }

        // Pass 3: Near-identical file size + duration ±2.5s (catches renamed/untagged duplicates across sources)
        if (fileSizes.isNotEmpty()) {
            val sizedIndices = (0 until n).filter { i ->
                (fileSizes[tracks[i].id] ?: 0L) >= MIN_FILE_SIZE_FOR_SIZE_MATCH &&
                    tracks[i].durationMs >= 15_000L
            }.sortedBy { fileSizes[tracks[it].id] ?: 0L }

            for (k in 0 until sizedIndices.size) {
                val i = sizedIndices[k]
                val sizeI = fileSizes[tracks[i].id] ?: 0L
                val maxAllowedDiff = max(SIZE_TOLERANCE_BYTES, (sizeI * SIZE_TOLERANCE_RATIO).toLong())
                for (m in k + 1 until sizedIndices.size) {
                    val j = sizedIndices[m]
                    val sizeJ = fileSizes[tracks[j].id] ?: 0L
                    if (sizeJ - sizeI > maxAllowedDiff) break
                    if (abs(tracks[i].durationMs - tracks[j].durationMs) <= SIZE_DURATION_TOLERANCE_MS) {
                        union(i, j, DuplicateMatchReason.SAME_FILE_SIZE)
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
                DuplicateMatchReason.SAME_FILE_SIZE in reasons && DuplicateMatchReason.SAME_METADATA !in reasons ->
                    DuplicateMatchReason.SAME_FILE_SIZE
                DuplicateMatchReason.MULTI_SOURCE in reasons ->
                    DuplicateMatchReason.MULTI_SOURCE
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
     * prefers larger file size (higher bitrate/lossless), complete metadata tags, artwork, and lossless extensions.
     */
    fun qualityScore(track: Track, fileSizeBytes: Long): Long {
        var score = 0L
        // Primary signal: file size in KB (e.g., 8MB = +8000, 3MB = +3000)
        score += (fileSizeBytes / 1024L).coerceAtMost(200_000L)
        // Lossless format bonus
        val lowerPath = track.filePath.lowercase()
        if (lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav") || lowerPath.endsWith(".alac")) {
            score += 20_000L
        } else if (lowerPath.endsWith(".m4a") || lowerPath.endsWith(".opus")) {
            score += 600L
        }
        // Metadata completeness bonus
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
