package com.yasinjahantigh.logarhythm.data.radar

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.download.DownloadSearchService
import com.yasinjahantigh.logarhythm.data.room.ArtistFollowDao
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import com.yasinjahantigh.logarhythm.data.room.FollowedArtistEntity
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    private val downloadSearchService: DownloadSearchService,
    private val preferencesManager: PreferencesManager,
    private val trackRepository: TrackRepository
) {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val radarMutex = Mutex()

    private fun isDismissed(release: ArtistReleaseEntity, dismissed: Set<String>): Boolean {
        if (dismissed.isEmpty()) return false
        return release.releaseId in dismissed ||
            ReleaseRadarService.titleKey(release.artistName, release.title) in dismissed
    }

    private fun isHiddenRelease(
        release: ArtistReleaseEntity,
        dismissed: Set<String>,
        ownedKeys: Set<String>
    ): Boolean {
        if (release.source.equals(DownloadSource.MUSICMAN.raw, ignoreCase = true)) return true
        if (release.discoveredAt <= 0L) return true
        if (isDismissed(release, dismissed)) return true
        return ReleaseRadarService.isTrackOwned(release.artistName, release.title, ownedKeys)
    }

    fun observeFollowed(): Flow<List<FollowedArtistEntity>> = dao.observeFollowed()

    fun observeIsFollowed(artistName: String): Flow<Boolean> {
        val key = ReleaseRadarService.normalizeArtistKey(artistName)
        if (key.isBlank()) return flowOf(false)
        return dao.observeIsFollowed(key)
    }

    fun observeReleases(): Flow<List<ArtistReleaseEntity>> =
        combine(
            dao.observeReleases(),
            preferencesManager.dismissedRadarIds,
            trackRepository.getAllTracks()
        ) { list, dismissed, tracks ->
            val ownedKeys = ReleaseRadarService.ownedTrackKeys(tracks)
            list.filterNot { isHiddenRelease(it, dismissed, ownedKeys) }
        }

    fun observeUnseenCount(): Flow<Int> =
        combine(
            dao.observeReleases(),
            preferencesManager.dismissedRadarIds,
            trackRepository.getAllTracks()
        ) { list, dismissed, tracks ->
            val ownedKeys = ReleaseRadarService.ownedTrackKeys(tracks)
            list.count { !it.isSeen && !isHiddenRelease(it, dismissed, ownedKeys) }
        }

    suspend fun dismissRelease(release: ArtistReleaseEntity) {
        preferencesManager.dismissRadarRelease(
            listOf(
                release.releaseId,
                ReleaseRadarService.titleKey(release.artistName, release.title)
            )
        )
        dao.deleteRelease(release.releaseId)
    }

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
            // Seed recent releases right away (marked seen so they don't spam notifications)
            // so Release Radar shows fresh catalog immediately instead of looking empty.
            backgroundScope.launch {
                radarMutex.withLock {
                    runCatching {
                        val current = dao.getByKey(key) ?: return@withLock
                        val result = radarService.checkArtist(current)
                        dao.upsertArtist(result.updatedArtist)
                        val dismissed = preferencesManager.dismissedRadarIds.first()
                        val ownedKeys = ReleaseRadarService.ownedTrackKeys(trackRepository.getAllTracks().first())
                        val visibleCatalog = result.recentCatalog.filterNot {
                            isHiddenRelease(it, dismissed, ownedKeys)
                        }
                        if (visibleCatalog.isNotEmpty()) {
                            dao.insertReleases(visibleCatalog)
                        }
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

        val dismissed = runCatching {
            preferencesManager.dismissedRadarIds.first()
        }.getOrDefault(emptySet())

        val ownedKeys = runCatching {
            ReleaseRadarService.ownedTrackKeys(trackRepository.getAllTracks().first())
        }.getOrDefault(emptySet())

        val allExisting = runCatching { dao.observeReleases().first() }.getOrDefault(emptyList())
        allExisting.filter { it.source.equals(DownloadSource.MUSICMAN.raw, ignoreCase = true) }
            .forEach { runCatching { dao.deleteRelease(it.releaseId) } }

        val existingReleasesByArtist = allExisting
            .filterNot { it.source.equals(DownloadSource.MUSICMAN.raw, ignoreCase = true) }
            .groupBy { it.artistKey }

        for (artist in followed) {
            try {
                val res = radarService.checkArtist(artist)
                dao.upsertArtist(res.updatedArtist)
                val freshNew = res.newReleases.filterNot { isHiddenRelease(it, dismissed, ownedKeys) }
                val freshCatalog = res.recentCatalog.filterNot { isHiddenRelease(it, dismissed, ownedKeys) }
                if (freshNew.isNotEmpty()) {
                    dao.insertReleases(freshNew)
                    discovered += freshNew
                } else if (existingReleasesByArtist[artist.artistKey].isNullOrEmpty() && freshCatalog.isNotEmpty()) {
                    dao.insertReleases(freshCatalog)
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

    fun releaseToSearchResult(release: ArtistReleaseEntity): DownloadSearchResult {
        val formattedDate = if (release.discoveredAt > 0L) {
            runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(release.discoveredAt))
            }.getOrDefault("")
        } else {
            ""
        }
        val kind = if (release.releaseId.contains("_alb:")) {
            DownloadResultKind.ALBUM
        } else {
            DownloadResultKind.TRACK
        }
        return DownloadSearchResult(
            id = release.releaseId,
            title = release.title,
            artist = release.artistName,
            source = DownloadSource.fromRaw(release.source),
            url = release.sourceUrl,
            durationMs = release.durationMs,
            thumbnailUrl = release.artworkUrl,
            kind = kind,
            album = release.album,
            albumArtist = release.artistName,
            releaseDate = formattedDate
        )
    }
}
