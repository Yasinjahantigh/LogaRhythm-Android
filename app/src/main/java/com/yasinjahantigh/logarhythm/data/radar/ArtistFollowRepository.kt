package com.yasinjahantigh.logarhythm.data.radar

import com.yasinjahantigh.logarhythm.data.download.DownloadSearchService
import com.yasinjahantigh.logarhythm.data.room.ArtistFollowDao
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import com.yasinjahantigh.logarhythm.data.room.FollowedArtistEntity
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class RadarRefreshSummary(
    val newReleases: List<ArtistReleaseEntity>,
    val checkedArtists: Int,
    val anyReachable: Boolean,
    val suggestVpn: Boolean
)

@Singleton
class ArtistFollowRepository @Inject constructor(
    private val dao: ArtistFollowDao,
    private val radarService: ReleaseRadarService,
    private val downloadRepository: DownloadRepository,
    private val downloadSearchService: DownloadSearchService
) {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val radarMutex = Mutex()

    fun observeFollowed(): Flow<List<FollowedArtistEntity>> = dao.observeFollowed()

    fun observeIsFollowed(artistName: String): Flow<Boolean> {
        val key = ReleaseRadarService.normalizeArtistKey(artistName)
        if (key.isBlank()) return flowOf(false)
        return dao.observeIsFollowed(key)
    }

    fun observeReleases(): Flow<List<ArtistReleaseEntity>> = dao.observeReleases()

    fun observeUnseenCount(): Flow<Int> = dao.observeUnseenCount()

    suspend fun toggleFollow(
        displayName: String,
        musicManArtistId: String? = null,
        artworkUrl: String? = null
    ): Boolean {
        val key = ReleaseRadarService.normalizeArtistKey(displayName)
        if (key.isBlank()) return false
        val existing = dao.getByKey(key)
        return if (existing != null) {
            dao.deleteReleasesForArtist(key)
            dao.deleteArtist(key)
            false
        } else {
            val entity = FollowedArtistEntity(
                artistKey = key,
                displayName = displayName.trim(),
                musicManArtistId = musicManArtistId,
                artworkUrl = artworkUrl,
                knownTrackIdsJson = "[]",
                latestTrackTitle = null,
                lastCheckedAt = 0L,
                followedAt = System.currentTimeMillis()
            )
            dao.upsertArtist(entity)
            // Seed current catalog baseline in background so existing tracks do not flood alerts.
            backgroundScope.launch {
                radarMutex.withLock {
                    runCatching {
                        val current = dao.getByKey(key) ?: return@withLock
                        val result = radarService.checkArtist(current)
                        dao.upsertArtist(result.updatedArtist)
                    }
                }
            }
            true
        }
    }

    suspend fun unfollow(artistKey: String) {
        dao.deleteReleasesForArtist(artistKey)
        dao.deleteArtist(artistKey)
    }

    suspend fun markAllSeen() {
        dao.markAllSeen()
    }

    suspend fun refreshAll(): RadarRefreshSummary = radarMutex.withLock {
        val followed = dao.getFollowedOnce()
        if (followed.isEmpty()) {
            return RadarRefreshSummary(
                newReleases = emptyList(),
                checkedArtists = 0,
                anyReachable = true,
                suggestVpn = false
            )
        }
        val discovered = mutableListOf<ArtistReleaseEntity>()
        var anyReachable = false
        var anySuggestVpn = false

        for (artist in followed) {
            try {
                val res = radarService.checkArtist(artist)
                dao.upsertArtist(res.updatedArtist)
                if (res.newReleases.isNotEmpty()) {
                    dao.insertReleases(res.newReleases)
                    discovered += res.newReleases
                }
                if (res.reachable) anyReachable = true
                if (res.suggestVpn) anySuggestVpn = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                anySuggestVpn = true
            }
        }

        return RadarRefreshSummary(
            newReleases = discovered,
            checkedArtists = followed.size,
            anyReachable = anyReachable,
            suggestVpn = !anyReachable && anySuggestVpn
        )
    }

    /** Enqueues a discovered release directly if it has a downloadable source, or searches for it first. */
    suspend fun downloadRelease(release: ArtistReleaseEntity): Boolean {
        val directSource = when (release.source.lowercase()) {
            DownloadSource.MUSICMAN.raw -> DownloadSource.MUSICMAN
            DownloadSource.DEEZER.raw -> DownloadSource.DEEZER
            DownloadSource.RADIOJAVAN.raw -> DownloadSource.RADIOJAVAN
            DownloadSource.YOUTUBE.raw -> DownloadSource.YOUTUBE
            else -> null
        }

        val isAlbum = release.releaseId.contains("_alb:") ||
            release.releaseId.startsWith("mm:album:") ||
            (release.album.isNotBlank() && release.album.equals(release.title, ignoreCase = true) && release.durationMs == 0L)

        if (directSource == DownloadSource.MUSICMAN && !isAlbum && release.sourceUrl.isNotBlank()) {
            val id = release.releaseId.substringAfterLast(':')
            downloadRepository.enqueue(
                DownloadSearchResult(
                    id = id,
                    title = release.title,
                    artist = release.artistName,
                    source = DownloadSource.MUSICMAN,
                    url = release.sourceUrl,
                    durationMs = release.durationMs,
                    thumbnailUrl = release.artworkUrl,
                    kind = DownloadResultKind.TRACK,
                    album = release.album,
                    albumArtist = release.artistName
                )
            )
            return true
        }

        val outcome = runCatching {
            downloadSearchService.search("${release.title} ${release.artistName}".trim(), null)
        }.getOrNull()
        val match = outcome?.results?.firstOrNull { it.kind == DownloadResultKind.TRACK }
            ?: outcome?.results?.firstOrNull()
            ?: return false

        downloadRepository.enqueue(
            match.copy(
                album = match.album.ifBlank { release.album },
                albumArtist = match.albumArtist.ifBlank { release.artistName }
            )
        )
        return true
    }
}
