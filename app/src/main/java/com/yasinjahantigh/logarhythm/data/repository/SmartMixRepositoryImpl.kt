package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.room.LyricsDao
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import com.yasinjahantigh.logarhythm.domain.repository.SmartMixRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.domain.smart.SmartMix
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixBuilder
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import com.yasinjahantigh.logarhythm.domain.smart.TasteInsights
import com.yasinjahantigh.logarhythm.domain.smart.UserTasteModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartMixRepositoryImpl @Inject constructor(
    private val trackRepository: TrackRepository,
    private val lyricsDao: LyricsDao,
    private val statsRepository: StatsRepository
) : SmartMixRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class Snapshot(
        val tracks: List<Track>,
        val playStats: Map<String, TrackPlayStat>,
        val favoriteIds: Set<String>,
        val downloadedUris: Set<String>,
        val lyricsTrackIds: Set<String>,
        val playEvents: List<PlayEvent>,
        val tasteModel: UserTasteModel
    )

    private val sharedSnapshot: Flow<Snapshot> = combine(
        combine(
            trackRepository.getAllTracks(),
            trackRepository.getPlayStats(),
            trackRepository.getFavoriteTrackIds(),
            trackRepository.getDownloadedTrackUris(),
            lyricsDao.getTrackIdsWithLyrics().distinctUntilChanged()
        ) { tracks, stats, favorites, downloads, lyricsIds ->
            BaseData(tracks, stats, favorites, downloads, lyricsIds.toHashSet())
        },
        statsRepository.getAllPlayEvents()
    ) { base, events ->
        val model = UserTasteModel.build(
            tracks = base.tracks,
            events = events,
            favoriteIds = base.favoriteIds
        )
        Snapshot(
            tracks = base.tracks,
            playStats = base.playStats,
            favoriteIds = base.favoriteIds,
            downloadedUris = base.downloadedUris,
            lyricsTrackIds = base.lyricsTrackIds,
            playEvents = events,
            tasteModel = model
        )
    }
        .flowOn(Dispatchers.Default)
        .shareIn(repoScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L, replayExpirationMillis = 0L), replay = 1)

    private fun snapshot(): Flow<Snapshot> = sharedSnapshot

    private data class BaseData(
        val tracks: List<Track>,
        val playStats: Map<String, TrackPlayStat>,
        val favoriteIds: Set<String>,
        val downloadedUris: Set<String>,
        val lyricsTrackIds: Set<String>
    )

    private fun build(snapshot: Snapshot, type: SmartMixType): List<Track> =
        SmartMixBuilder.build(
            type = type,
            tracks = snapshot.tracks,
            playStats = snapshot.playStats,
            favoriteIds = snapshot.favoriteIds,
            downloadedUris = snapshot.downloadedUris,
            lyricsTrackIds = snapshot.lyricsTrackIds,
            playEvents = snapshot.playEvents,
            tasteModel = snapshot.tasteModel
        )

    override fun getSmartMixes(): Flow<List<SmartMix>> = snapshot().map { snapshot ->
        SmartMixType.entries.map { type ->
            val tracks = build(snapshot, type)
            SmartMix(
                type = type,
                trackCount = tracks.size,
                artworkUris = SmartMixBuilder.artworkOf(tracks)
            )
        }
    }

    override fun getSmartMix(type: SmartMixType): Flow<SmartMix?> = snapshot().map { snapshot ->
        val tracks = build(snapshot, type)
        SmartMix(
            type = type,
            trackCount = tracks.size,
            artworkUris = SmartMixBuilder.artworkOf(tracks)
        )
    }

    override fun getSmartMixTracks(type: SmartMixType): Flow<List<Track>> =
        snapshot().map { snapshot -> build(snapshot, type) }

    override fun observeTasteInsights(): Flow<TasteInsights> =
        snapshot().map { it.tasteModel.insights }
}
