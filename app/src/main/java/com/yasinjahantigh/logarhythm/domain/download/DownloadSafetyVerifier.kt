package com.yasinjahantigh.logarhythm.domain.download

import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import kotlin.math.abs

/**
 * Safety verification engine for audio downloads.
 * Prevents mismatched songs (e.g. "Khodaye Binahayat" when "Khodafez" was requested)
 * from being silently chosen, downloaded, or tagged onto disk.
 */
object DownloadSafetyVerifier {

    const val MIN_TITLE_SIMILARITY = 0.65
    const val MIN_ARTIST_SIMILARITY = 0.45
    const val MAX_DURATION_DIFFERENCE_MS = 25_000L

    /**
     * Checks if a candidate track is a safe match for the requested track.
     *
     * @param expectedSource The source of the requested track (e.g., DEEZER).
     * @param expectedTrackId The ID on the requested source.
     * @param expectedTitle The requested track title.
     * @param expectedArtist The requested track artist.
     * @param expectedDurationMs The requested track duration in ms (0 if unknown).
     * @param candidateSource The source of the candidate track (e.g., MUSICMAN).
     * @param candidateTrackId The candidate track's ID on candidateSource.
     * @param candidateTitle The candidate track's title.
     * @param candidateArtist The candidate track's artist.
     * @param candidateDurationMs The candidate track's duration in ms (0 if unknown).
     */
    fun isSafeMatch(
        expectedSource: DownloadSource,
        expectedTrackId: String?,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long,
        candidateSource: DownloadSource,
        candidateTrackId: String?,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long
    ): Boolean {
        // 1. Exact ID match on the same source
        if (expectedSource == candidateSource &&
            !expectedTrackId.isNullOrBlank() &&
            !candidateTrackId.isNullOrBlank() &&
            cleanId(expectedTrackId) == cleanId(candidateTrackId)
        ) {
            return true
        }

        // 2. Normalized Title Check
        val normExpectedTitle = normalize(expectedTitle)
        val normCandidateTitle = normalize(candidateTitle)
        if (normExpectedTitle.isBlank() || normCandidateTitle.isBlank()) {
            return false
        }

        val titleSim = titleSimilarity(normExpectedTitle, normCandidateTitle)
        // If title similarity is below minimum threshold, reject immediately.
        // e.g. "khodafez" vs "khodaye binahayat" has similarity 0.0 -> rejected!
        if (titleSim < MIN_TITLE_SIMILARITY) {
            return false
        }

        // 3. Normalized Artist Check (if expected artist is known)
        val normExpectedArtist = normalize(expectedArtist)
        val isStreamingUploader = candidateSource == DownloadSource.YOUTUBE ||
            candidateSource == DownloadSource.SOUNDCLOUD
        val normCandidateArtist = cleanChannelName(normalize(candidateArtist), isStreamingUploader)
        val hasKnownExpectedArtist = normExpectedArtist.isNotBlank() &&
            !DownloadMetadataMerge.isPlaceholder(expectedArtist)
        val hasKnownCandidateArtist = normCandidateArtist.isNotBlank() &&
            !DownloadMetadataMerge.isPlaceholder(candidateArtist)

        var artistSim = 1.0
        if (hasKnownExpectedArtist && hasKnownCandidateArtist) {
            artistSim = artistSimilarity(normExpectedArtist, normCandidateArtist)
            // YouTube/SoundCloud uploaders are often record labels or music channels (e.g. "Avang Music")
            // while the track artist appears in the video/track title ("Shadmehr - Taghdir").
            if (artistSim < MIN_ARTIST_SIMILARITY && isStreamingUploader) {
                val artistInTitleSim = artistSimilarity(normExpectedArtist, normCandidateTitle)
                if (artistInTitleSim >= 0.65) {
                    artistSim = artistInTitleSim
                }
            }
            // If the artists are completely different, reject!
            if (artistSim < MIN_ARTIST_SIMILARITY) {
                return false
            }
        }

        // 4. Duration Check (if both durations are known and positive)
        if (expectedDurationMs > 0L && candidateDurationMs > 0L) {
            val diffMs = abs(expectedDurationMs - candidateDurationMs)
            if (diffMs > MAX_DURATION_DIFFERENCE_MS) {
                return false
            }
        }

        // 5. Combined Confidence
        val combinedScore = (titleSim * 0.65) + (artistSim * 0.35)
        return combinedScore >= 0.60
    }

    /**
     * Calculates score for ranking multiple safe matches.
     */
    fun matchScore(
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long,
        candidateTitle: String,
        candidateArtist: String,
        candidateDurationMs: Long,
        hasReadyAudio: Boolean = false
    ): Double {
        val normExpTitle = normalize(expectedTitle)
        val normCandTitle = normalize(candidateTitle)
        val titleSim = titleSimilarity(normExpTitle, normCandTitle)

        val normExpArtist = normalize(expectedArtist)
        val normCandArtist = normalize(candidateArtist)
        val artistSim = if (normExpArtist.isNotBlank() && normCandArtist.isNotBlank()) {
            artistSimilarity(normExpArtist, normCandArtist)
        } else 0.5

        val durationScore = if (expectedDurationMs > 0L && candidateDurationMs > 0L) {
            val diff = abs(expectedDurationMs - candidateDurationMs)
            (1.0 - (diff.toDouble() / MAX_DURATION_DIFFERENCE_MS)).coerceIn(0.0, 1.0)
        } else 0.5

        val readyBonus = if (hasReadyAudio) 0.05 else 0.0
        return (titleSim * 0.55) + (artistSim * 0.30) + (durationScore * 0.15) + readyBonus
    }

    /**
     * Selects the best candidate that strictly passes safety verification.
     * Returns null if no candidate passes verification.
     */
    fun <T> findBestMatch(
        expectedSource: DownloadSource,
        expectedTrackId: String?,
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long,
        candidates: List<T>,
        candidateSource: DownloadSource,
        trackIdExtractor: (T) -> String?,
        titleExtractor: (T) -> String,
        artistExtractor: (T) -> String,
        durationExtractor: (T) -> Long,
        hasReadyAudioExtractor: (T) -> Boolean = { false }
    ): T? {
        val verified = candidates.filter { candidate ->
            isSafeMatch(
                expectedSource = expectedSource,
                expectedTrackId = expectedTrackId,
                expectedTitle = expectedTitle,
                expectedArtist = expectedArtist,
                expectedDurationMs = expectedDurationMs,
                candidateSource = candidateSource,
                candidateTrackId = trackIdExtractor(candidate),
                candidateTitle = titleExtractor(candidate),
                candidateArtist = artistExtractor(candidate),
                candidateDurationMs = durationExtractor(candidate)
            )
        }

        if (verified.isEmpty()) return null

        return verified.maxByOrNull { candidate ->
            matchScore(
                expectedTitle = expectedTitle,
                expectedArtist = expectedArtist,
                expectedDurationMs = expectedDurationMs,
                candidateTitle = titleExtractor(candidate),
                candidateArtist = artistExtractor(candidate),
                candidateDurationMs = durationExtractor(candidate),
                hasReadyAudio = hasReadyAudioExtractor(candidate)
            )
        }
    }

    /**
     * Verifies that the downloaded audio outcome safely matches the requested download item
     * before writing ID3 tags and persisting to storage.
     *
     * Throws [DownloadPipelineException] if there is a mismatch.
     */
    fun verifyBeforeSaving(
        item: DownloadItem,
        candidateSource: DownloadSource?,
        candidateTrackId: String?,
        candidateTitle: String?,
        candidateArtist: String?,
        candidateDurationMs: Long
    ) {
        if (!candidateTitle.isNullOrBlank()) {
            val safe = isSafeMatch(
                expectedSource = item.source,
                expectedTrackId = item.videoId,
                expectedTitle = item.title,
                expectedArtist = item.artist,
                expectedDurationMs = item.durationMs,
                candidateSource = candidateSource ?: item.source,
                candidateTrackId = candidateTrackId,
                candidateTitle = candidateTitle,
                candidateArtist = candidateArtist.orEmpty(),
                candidateDurationMs = candidateDurationMs
            )
            if (!safe) {
                throw DownloadPipelineException(
                    DownloadErrorKind.CONTENT,
                    "Downloaded audio did not match the requested track: expected '${item.title}' by '${item.artist}', but got '$candidateTitle' by '${candidateArtist.orEmpty()}'"
                )
            }
        }
    }

    private fun normalize(text: String): String =
        MetadataNormalizer.normalizeForComparison(DownloadTitleParser.clean(text))

    private fun cleanId(id: String): String = id.trim().removePrefix("it_")

    internal fun titleSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val tokensA = a.split(' ').filter { it.isNotBlank() }
        val tokensB = b.split(' ').filter { it.isNotBlank() }
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val setA = tokensA.toSet()
        val setB = tokensB.toSet()
        val intersection = setA.intersect(setB).size.toDouble()
        val union = setA.union(setB).size.toDouble()
        val jaccard = intersection / union

        val minSize = minOf(setA.size, setB.size).toDouble()
        val containment = intersection / minSize

        // Levenshtein-based similarity for single-token typo/transliteration tolerance
        if (tokensA.size == 1 && tokensB.size == 1) {
            val levSim = levenshteinSimilarity(tokensA[0], tokensB[0])
            return maxOf(jaccard, levSim)
        }

        return maxOf(jaccard, containment * 0.85)
    }

    internal fun artistSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val tokensA = a.split(' ').filter { it.isNotBlank() && it !in COLLABORATION_TOKENS }.toSet()
        val tokensB = b.split(' ').filter { it.isNotBlank() && it !in COLLABORATION_TOKENS }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size.toDouble()
        val union = tokensA.union(tokensB).size.toDouble()
        val jaccard = intersection / union
        val containment = intersection / minOf(tokensA.size, tokensB.size).toDouble()
        return maxOf(jaccard, containment * 0.9)
    }

    private fun cleanChannelName(normArtist: String, isStreamingUploader: Boolean): String {
        if (!isStreamingUploader || normArtist.isBlank()) return normArtist
        val tokens = normArtist.split(' ')
            .filter { it.isNotBlank() && it !in CHANNEL_NOISE_TOKENS }
        return if (tokens.isEmpty()) normArtist else tokens.joinToString(" ")
    }

    private val COLLABORATION_TOKENS = setOf("feat", "ft", "featuring", "with", "and", "x", "vs", "و")
    private val CHANNEL_NOISE_TOKENS = setOf("topic", "vevo", "official", "channel", "music", "records")

    private fun levenshteinSimilarity(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(s1, s2)
        return (maxLen - dist).toDouble() / maxLen
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev else minOf(prev, dp[j], dp[j - 1]) + 1
                prev = temp
            }
        }
        return dp[s2.length]
    }
}
