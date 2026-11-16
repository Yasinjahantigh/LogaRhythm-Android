package com.yasinjahantigh.logarhythm.data.download.metadata

import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.data.download.radiojavan.RadioJavanClient
import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import com.yasinjahantigh.logarhythm.domain.download.DownloadTitleParser
import com.yasinjahantigh.logarhythm.domain.library.MetadataNormalizer
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Deezer / iTunes → MusicBrainz (+ Cover Art Archive) → RadioJavan fallback → search seed.
 * Every provider is optional: a blocked API silently degrades to the next one.
 */
@Singleton
class MetadataResolver @Inject constructor(
    private val deezer: DeezerClient,
    private val itunes: ItunesClient,
    private val musicBrainz: MusicBrainzClient,
    private val radioJavan: RadioJavanClient,
    private val musicMan: MusicManClient
) {

    data class Resolved(
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val artworkUrl: String?,
        val year: String?,
        val trackNumber: Int?,
        val discNumber: Int?,
        val composer: String?
    )

    suspend fun resolve(
        title: String,
        artist: String,
        durationMs: Long,
        replaceArtist: Boolean = false
    ): Resolved = coroutineScope {
        val lookupTitle = DownloadTitleParser.extractFeatured(title).first
        val lookupArtist = DownloadTitleParser.extractFeatured(artist).first
        val cleanArtistQuery = if (DownloadMetadataMerge.isPlaceholder(lookupArtist)) "" else lookupArtist
        val isPersian = MetadataNormalizer.containsPersian(title) || MetadataNormalizer.containsPersian(artist)

        val deezerDeferred = async {
            bestEffort(DEEZER_TIMEOUT_MS) { deezer.search(lookupTitle, cleanArtistQuery, durationMs) }
        }
        val itunesDeferred = async {
            bestEffort(ITUNES_TIMEOUT_MS) { itunes.search(lookupTitle, cleanArtistQuery, durationMs) }
        }
        val rjDeferred = if (isPersian) {
            async { resolveFromRadioJavan(lookupTitle, cleanArtistQuery, durationMs) }
        } else null
        val musicManDeferred = async {
            resolveFromMusicMan(lookupTitle, cleanArtistQuery, durationMs)
        }

        val deezerResult = deezerDeferred.await()
        val itunesResult = itunesDeferred.await()
        var rjResult = rjDeferred?.await()
        val mmResult = musicManDeferred.await()

        var album = DownloadMetadataMerge.pick(deezerResult?.album, itunesResult?.album, mmResult?.album)
        var artwork = deezerResult?.artworkUrl ?: itunesResult?.artworkUrl ?: mmResult?.thumbnailUrl
        var genre = itunesResult?.genre?.let { MetadataNormalizer.canonicalizeGenre(it, "").takeIf(String::isNotEmpty) }

        if (album == null || artwork == null || genre == null) {
            val mb = bestEffort(MUSICBRAINZ_TIMEOUT_MS) {
                musicBrainz.lookup(lookupTitle, cleanArtistQuery)
            }
            if (mb != null) {
                album = DownloadMetadataMerge.pick(album, mb.album)
                if (genre == null) {
                    genre = mb.genre?.let { MetadataNormalizer.canonicalizeGenre(it, "").takeIf(String::isNotEmpty) }
                }
                if (artwork == null && !mb.releaseId.isNullOrBlank()) {
                    artwork = "https://coverartarchive.org/release/${mb.releaseId}/front-500"
                }
            }
        }

        // Secondary RadioJavan fallback for tracks missed by Western catalogs (e.g., transliterated Persian songs).
        if (!isPersian && deezerResult == null && itunesResult == null && album == null) {
            rjResult = resolveFromRadioJavan(lookupTitle, cleanArtistQuery, durationMs)
        }

        if (artwork == null) {
            artwork = rjResult?.thumbnailUrl?.takeIf { it.startsWith("http") }
        }

        Resolved(
            title = DownloadMetadataMerge.pick(deezerResult?.title, itunesResult?.title, mmResult?.title, rjResult?.title, title),
            artist = if (replaceArtist) {
                DownloadMetadataMerge.pick(deezerResult?.artist, itunesResult?.artist, mmResult?.artist, rjResult?.artist, artist)
            } else {
                DownloadMetadataMerge.preferSourceArtist(
                    artist,
                    deezerResult?.artist,
                    itunesResult?.artist,
                    mmResult?.artist ?: rjResult?.artist
                )
            },
            album = DownloadMetadataMerge.pick(album),
            genre = DownloadMetadataMerge.pick(genre),
            artworkUrl = artwork?.takeIf { it.startsWith("http") },
            year = itunesResult?.year,
            trackNumber = itunesResult?.trackNumber,
            discNumber = itunesResult?.discNumber,
            composer = itunesResult?.composer
        )
    }

    private suspend fun resolveFromMusicMan(
        title: String,
        artist: String,
        durationMs: Long
    ): DownloadSearchResult? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.length < 2) return null
        val results = bestEffort(MUSICMAN_TIMEOUT_MS) {
            musicMan.search(query, DownloadSearchCategory.TRACK, limit = 8)
        }.orEmpty().filter { it.kind == DownloadResultKind.TRACK }
        return results
            .asSequence()
            .filter { candidate ->
                if (durationMs > 0L && candidate.durationMs > 0L && abs(candidate.durationMs - durationMs) > 25_000L) {
                    return@filter false
                }
                val titleSim = DownloadMetadataMerge.similarity(candidate.title, title)
                val artistSim = if (artist.isBlank()) 1.0 else DownloadMetadataMerge.similarity(candidate.artist, artist)
                titleSim >= 0.70 && artistSim >= 0.65
            }
            .maxByOrNull { candidate ->
                val titleSim = DownloadMetadataMerge.similarity(candidate.title, title)
                val artistSim = if (artist.isBlank()) 0.8 else DownloadMetadataMerge.similarity(candidate.artist, artist)
                titleSim * 0.6 + artistSim * 0.4
            }
    }

    private suspend fun resolveFromRadioJavan(
        title: String,
        artist: String,
        durationMs: Long
    ): DownloadSearchResult? {
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.length < 2) return null
        val results = bestEffort(RADIOJAVAN_TIMEOUT_MS) { radioJavan.search(query, limit = 8) }.orEmpty()
        return results
            .asSequence()
            .filter { candidate ->
                if (durationMs > 0L && candidate.durationMs > 0L && abs(candidate.durationMs - durationMs) > 25_000L) {
                    return@filter false
                }
                val titleSim = DownloadMetadataMerge.similarity(candidate.title, title)
                val artistSim = if (artist.isBlank()) 1.0 else DownloadMetadataMerge.similarity(candidate.artist, artist)
                titleSim >= 0.65 && artistSim >= 0.60
            }
            .maxByOrNull { candidate ->
                val titleSim = DownloadMetadataMerge.similarity(candidate.title, title)
                val artistSim = if (artist.isBlank()) 0.8 else DownloadMetadataMerge.similarity(candidate.artist, artist)
                titleSim * 0.6 + artistSim * 0.4
            }
    }

    private suspend fun <T> bestEffort(timeoutMs: Long, block: suspend () -> T?): T? = try {
        withTimeout(timeoutMs) { block() }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val DEEZER_TIMEOUT_MS = 6_000L
        const val ITUNES_TIMEOUT_MS = 6_000L
        const val MUSICBRAINZ_TIMEOUT_MS = 8_000L
        const val RADIOJAVAN_TIMEOUT_MS = 6_000L
        const val MUSICMAN_TIMEOUT_MS = 6_000L
    }
}
