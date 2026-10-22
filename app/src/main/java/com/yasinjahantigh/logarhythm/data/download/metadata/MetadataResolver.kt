package com.yasinjahantigh.logarhythm.data.download.metadata

import com.yasinjahantigh.logarhythm.domain.download.DownloadMetadataMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deezer → MusicBrainz (+ Cover Art Archive) → yt-dlp/search seed.
 * Every provider is optional: a blocked API silently degrades to the next one.
 */
@Singleton
class MetadataResolver @Inject constructor(
    private val deezer: DeezerClient,
    private val itunes: ItunesClient,
    private val musicBrainz: MusicBrainzClient
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

    suspend fun resolve(title: String, artist: String, durationMs: Long): Resolved {
        val deezerResult = bestEffort(DEEZER_TIMEOUT_MS) {
            deezer.search(title, artist, durationMs)
        }
        val itunesResult = bestEffort(ITUNES_TIMEOUT_MS) {
            itunes.search(title, artist, durationMs)
        }

        var album = DownloadMetadataMerge.pick(deezerResult?.album, itunesResult?.album)
        var artwork = deezerResult?.artworkUrl ?: itunesResult?.artworkUrl
        var genre = itunesResult?.genre

        if (album == null || artwork == null || genre == null) {
            val mb = bestEffort(MUSICBRAINZ_TIMEOUT_MS) {
                musicBrainz.lookup(title, artist)
            }
            if (mb != null) {
                album = DownloadMetadataMerge.pick(album, mb.album)
                genre = DownloadMetadataMerge.pick(genre, mb.genre)
                if (artwork == null && !mb.releaseId.isNullOrBlank()) {
                    artwork = "https://coverartarchive.org/release/${mb.releaseId}/front-500"
                }
            }
        }

        return Resolved(
            title = DownloadMetadataMerge.pick(deezerResult?.title, itunesResult?.title, title),
            artist = DownloadMetadataMerge.preferSourceArtist(
                artist,
                deezerResult?.artist,
                itunesResult?.artist
            ),
            album = DownloadMetadataMerge.pick(album),
            genre = DownloadMetadataMerge.pick(genre),
            artworkUrl = artwork?.takeIf { it.startsWith("http") },
            year = itunesResult?.year,
            trackNumber = itunesResult?.trackNumber,
            discNumber = itunesResult?.discNumber,
            composer = itunesResult?.composer
        )
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
    }
}
